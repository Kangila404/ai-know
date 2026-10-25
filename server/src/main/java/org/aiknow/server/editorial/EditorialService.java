package org.aiknow.server.editorial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.dto.res.*;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.ingestion.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class EditorialService {
    private final NewsSubmissionRepository submissions;
    private final NewsSubmissionService reviews;
    private final CardNewsRepository news;
    private final EditorialAuditService audit;
    private final org.aiknow.server.cardNews.repository.CardViewRepository views;
    public record Edit(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String reason,
        @NotNull @Valid NewsImportRequest draft) {}
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String reason) {}
    public record Article(long version, PublicationStatus publicationStatus, CardNewsResponse news, List<CardSlideResponse> slides,
        java.time.Instant approvedAt, java.time.Instant firstUsedAt, java.time.Instant lastUsedAt,
        boolean hasStartedDelivery, long totalViews, long uniqueViewers) {}

    @Transactional
    public NewsSubmissionService.Review editSubmission(Long id, Edit request, String actor) {
        var submission = submissions.findForUpdate(id).orElseThrow(this::notFound);
        requireVersion(submission.getVersion(), request.version());
        if (submission.getStatus() == InspectionStatus.APPROVED) throw conflict("승인된 초안은 게시글 수정 API를 사용하세요.");
        var before = reviews.get(id);
        requireSameSource(before.draft(), request.draft()); reviews.validateImages(request.draft());
        // Preserve immutable ingestion payload/hash so repeated n8n deliveries remain idempotent.
        submission.edit(audit.json(request.draft())); submissions.saveAndFlush(submission);
        var after = reviews.get(id);
        audit.record("SUBMISSION", id, "EDIT", actor, request.reason(), before, after); return after;
    }

    @Transactional
    public NewsSubmissionService.Review reopen(Long id, Action request, String actor) {
        var submission = submissions.findForUpdate(id).orElseThrow(this::notFound);
        requireVersion(submission.getVersion(), request.version());
        if (submission.getStatus() != InspectionStatus.DENIED) throw conflict("반려된 초안만 재검수할 수 있습니다.");
        var before = reviews.get(id); submission.reopen(); submissions.saveAndFlush(submission);
        var after = reviews.get(id); audit.record("SUBMISSION", id, "REOPEN", actor, request.reason(), before, after); return after;
    }

    @Transactional(readOnly = true)
    public Article getArticle(Long id) { return snapshot(news.findById(id).orElseThrow(this::notFound)); }

    @Transactional(readOnly = true)
    public Page<Article> listArticles(PublicationStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").descending());
        var results = status == null ? news.findAll(pageable) : news.findByPublicationStatus(status, pageable);
        var counts = results.isEmpty() ? Map.<Long, org.aiknow.server.cardNews.repository.CardViewRepository.Counts>of()
            : views.counts(results.getContent().stream().map(CardNews::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(org.aiknow.server.cardNews.repository.CardViewRepository.Counts::getCardId, c -> c));
        return results.map(article -> snapshot(article, counts.get(article.getId())));
    }

    @Transactional
    public Article editArticle(Long id, Edit request, String actor) {
        var article = news.findForUpdate(id).orElseThrow(this::notFound);
        requireVersion(article.getVersion(), request.version());
        if (!Objects.equals(article.getSourceUrl(), request.draft().sourceUrl())
            || !Objects.equals(article.getSourceTitle(), request.draft().sourceTitle())
            || !Objects.equals(article.getSourcePublishedAt(), request.draft().publishedAt().toInstant()))
            throw conflict("기사의 원문 정보는 변경할 수 없습니다.");
        reviews.validateImages(request.draft());
        var before = snapshot(article); article.editContent(request.draft()); news.saveAndFlush(article);
        var after = snapshot(article); audit.record("ARTICLE", id, "EDIT", actor, request.reason(), before, after); return after;
    }

    @Transactional
    public Article visibility(Long id, boolean visible, Action request, String actor) {
        var article = news.findForUpdate(id).orElseThrow(this::notFound);
        requireVersion(article.getVersion(), request.version());
        if (article.getPublicationStatus() == PublicationStatus.READY) throw conflict("최초 발송 전 콘텐츠는 공개 상태를 변경할 수 없습니다.");
        var before = snapshot(article); article.setEditorialVisibility(visible); news.saveAndFlush(article);
        var after = snapshot(article);
        audit.record("ARTICLE", id, visible ? "RESTORE" : "HIDE", actor, request.reason(), before, after); return after;
    }

    private Article snapshot(CardNews article) {
        return snapshot(article, views.counts(List.of(article.getId())).stream().findFirst().orElse(null));
    }
    private Article snapshot(CardNews article, org.aiknow.server.cardNews.repository.CardViewRepository.Counts count) {
        return new Article(article.getVersion(), article.getPublicationStatus(), CardNewsResponse.from(article),
            article.getCardSlides().stream().sorted(Comparator.comparing(CardSlide::getSequence)).map(CardSlideResponse::from).toList(),
            article.getApprovedAt(), article.getFirstUsedAt(), article.getLastUsedAt(), article.getFirstUsedAt() != null,
            count == null ? 0 : count.getTotalViews(), count == null ? 0 : count.getUniqueViewers());
    }
    private void requireVersion(long actual, Long expected) { if (expected == null || actual != expected) throw conflict("다른 변경이 반영되었습니다. 다시 조회하세요."); }
    private void requireSameSource(NewsImportRequest before, NewsImportRequest after) {
        if (!Objects.equals(before.sourceUrl(), after.sourceUrl()) || !Objects.equals(before.sourceTitle(), after.sourceTitle())
            || !Objects.equals(before.publishedAt().toInstant(), after.publishedAt().toInstant())) throw conflict("원문 정보는 변경할 수 없습니다.");
    }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
