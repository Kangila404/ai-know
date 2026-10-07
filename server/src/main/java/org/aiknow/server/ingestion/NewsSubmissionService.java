package org.aiknow.server.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class NewsSubmissionService {
    private final NewsSubmissionRepository submissions;
    private final NewsSubmissionWriter writer;
    private final CardNewsRepository news;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Clock clock;

    public record Receipt(Long id, InspectionStatus status, Long cardNewsId) {
        static Receipt from(NewsSubmission s) { return new Receipt(s.getId(), s.getStatus(), s.getCardNewsId()); }
    }
    public record Review(Long id, InspectionStatus status, NewsImportRequest draft, Long cardNewsId,
        Long reviewedBy, java.time.Instant reviewedAt, String reviewNote, PublicationStatus publicationStatus, ContentType contentType) {}
    public record Approve(@NotNull ContentType contentType, @Size(max = 10) List<@NotNull @Positive Long> categoryIds) {}
    public record Reject(@NotBlank @Size(max = 2000) String reason) {}

    public Receipt receive(NewsImportRequest request) {
        String payload;
        try { payload = mapper.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize news submission", e); }
        String sourceHash = hash(request.sourceUrl());
        String payloadHash = hash(payload);
        var existing = submissions.findBySourceHash(sourceHash);
        if (existing.isPresent()) return duplicate(existing.get(), payloadHash);
        try { return Receipt.from(writer.insert(sourceHash, payloadHash, request.sourceUrl(), payload)); }
        catch (DataIntegrityViolationException e) {
            return submissions.findBySourceHash(sourceHash).map(s -> duplicate(s, payloadHash)).orElseThrow(() -> e);
        }
    }

    private Receipt duplicate(NewsSubmission existing, String hash) {
        if (!existing.getPayloadHash().equals(hash))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 원문 URL의 다른 초안이 이미 존재합니다. submissionId=" + existing.getId());
        return Receipt.from(existing);
    }

    @Transactional(readOnly = true)
    public Page<Receipt> list(InspectionStatus status, int page, int size) {
        return submissions.findByStatusOrderByIdDesc(status, PageRequest.of(page, size)).map(Receipt::from);
    }

    @Transactional(readOnly = true)
    public Review get(Long id) {
        var s = submissions.findById(id).orElseThrow(this::notFound);
        var article = s.getCardNewsId() == null ? null : news.findById(s.getCardNewsId()).orElse(null);
        return new Review(s.getId(), s.getStatus(), read(s), s.getCardNewsId(), s.getReviewedBy(), s.getReviewedAt(), s.getReviewNote(),
            article == null ? null : article.getPublicationStatus(), article == null ? null : article.getContentType());
    }

    @Transactional
    public Receipt approve(Long id, Approve request, String reviewer) {
        var submission = submissions.findForUpdate(id).orElseThrow(this::notFound);
        if (submission.getStatus() == InspectionStatus.APPROVED) return Receipt.from(submission);
        requirePending(submission);
        var categoryIds = request.categoryIds() == null ? Set.<Long>of() : new LinkedHashSet<>(request.categoryIds());
        var selected = categories.findAllById(categoryIds);
        if (selected.size() != categoryIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다.");
        var article = prepareApproved(read(submission), request.contentType(), selected);
        news.saveAndFlush(article);
        submission.approve(article.getId(), reviewerId(reviewer), clock.instant());
        return Receipt.from(submission);
    }

    @Transactional
    public Receipt reject(Long id, Reject request, String reviewer) {
        var submission = submissions.findForUpdate(id).orElseThrow(this::notFound);
        requirePending(submission);
        submission.reject(request.reason().strip(), reviewerId(reviewer), clock.instant());
        return Receipt.from(submission);
    }

    private Long reviewerId(String userId) {
        return users.findByUserId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    private void requirePending(NewsSubmission submission) {
        if (submission.getStatus() != InspectionStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 검수가 완료된 초안입니다.");
    }
    private NewsImportRequest read(NewsSubmission s) {
        try { return mapper.readValue(s.getPayload(), NewsImportRequest.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored news submission is invalid", e); }
    }
    private CardNews prepareApproved(NewsImportRequest draft, ContentType contentType, List<Category> selected) {
        var image = draft.titleImage();
        var article = CardNews.builder().title(draft.title()).summary(draft.summary())
            .sourceTitle(draft.sourceTitle()).sourceUrl(draft.sourceUrl()).sourcePublishedAt(draft.publishedAt().toInstant())
            .approvedAt(clock.instant()).contentType(contentType).inspectionStatus(InspectionStatus.APPROVED)
            .keyPoints(new ArrayList<>(draft.keyPoints() == null ? List.of() : draft.keyPoints()))
            .titleImgUrl(image == null ? null : image.url())
            .titleImageSourceUrl(image == null ? null : image.sourceUrl())
            .titleImageCredit(image == null ? null : image.credit())
            .titleImageOrigin(image == null ? null : image.origin().name()).build();
        for (var slide : draft.slides()) {
            var visual = slide.image();
            article.getCardSlides().add(CardSlide.builder().cardNews(article).sequence(slide.sequence())
                .title(slide.title()).content(slide.content()).layout(slide.layout())
                .imgUrl(visual == null ? null : visual.url()).imageSourceUrl(visual == null ? null : visual.sourceUrl())
                .imageCredit(visual == null ? null : visual.credit())
                .imageOrigin(visual == null ? null : visual.origin().name()).build());
        }
        selected.forEach(category -> article.getCardNewsCategory().add(new CardNewsCategory(null, article, category)));
        return article;
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "뉴스 초안을 찾을 수 없습니다."); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
