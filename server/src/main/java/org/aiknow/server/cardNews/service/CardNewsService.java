package org.aiknow.server.cardNews.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.dto.res.CardNewsResponse;
import org.aiknow.server.cardNews.dto.res.CardSlideResponse;
import org.aiknow.server.cardNews.dto.res.CategoryResponse;
import org.aiknow.server.cardNews.dto.res.UpdateLikeResponse;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.cardNews.repository.CardSlideRepository;
import org.aiknow.server.cardNews.repository.CategoryRepository;
import org.aiknow.server.cardNews.repository.LikesRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.Clock;
import org.aiknow.server.notification.batch.NewsDeliveryProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Slf4j
@Service
@RequiredArgsConstructor
public class CardNewsService {
    private final CardNewsRepository cardNewsRepository;
    private final CardSlideRepository cardSlideRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LikesRepository likeRepository;
    private final Clock clock;
    private final NewsDeliveryProperties deliveryProperties;
    private final org.aiknow.server.notification.batch.DailyNewsEditionRepository editions;
    private final org.aiknow.server.cardNews.repository.CardReadRepository reads;

    @Transactional(readOnly = true)
    public List<CardNewsResponse> getCardNews(Pageable pageable, Long categoryId, String userId, boolean likedOnly) {
        return getCardNews(pageable, categoryId, userId, likedOnly, "");
    }
    @Transactional(readOnly = true)
    public List<CardNewsResponse> getCardNews(Pageable pageable, Long categoryId, String userId, boolean likedOnly, String query) {
        User user = findUserByUserIdOrThrow(userId);
        return responses(cardNewsRepository.searchPublished(user.getId(), categoryId, likedOnly,
            query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT), pageable), user);
    }
    private List<CardNewsResponse> responses(List<CardNews> news, User user) {
        if (news.isEmpty()) return List.of();
        var ids = news.stream().map(CardNews::getId).toList();
        var liked = likeRepository.likedIds(user.getId(), ids);
        var read = reads.readIds(user.getId(), ids);
        return news.stream().map(c -> CardNewsResponse.from(c, liked.contains(c.getId()), read.contains(c.getId()))).toList();
    }
    @Transactional(readOnly = true)
    public CardNewsResponse getCard(Long id, String userId) {
        return responses(List.of(approvedNews(id)), findUserByUserIdOrThrow(userId)).getFirst();
    }
    @Transactional(readOnly = true)
    public CardNewsResponse getTodayCardNews(String userId) {
        return getCard(getTodayCardNews().id(), userId);
    }
    @Transactional
    public void markRead(Long id, String userId) {
        User user = userRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        approvedNews(id);
        if (!reads.existsByUserIdAndCardNewsId(user.getId(), id))
            reads.save(new CardRead(user.getId(), id, clock.instant()));
    }

    @Transactional(readOnly = true)
    public CardNewsResponse getTodayCardNews(){
        LocalDate today = LocalDate.now(clock.withZone(deliveryProperties.zone()));
        var edition = editions.findById(today)
            .filter(e -> e.getCardNewsId() != null && e.getStartedAt() != null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "오늘의 카드 뉴스가 아직 게시되지 않았습니다."));
        CardNews cardNews = approvedNews(edition.getCardNewsId());
        return CardNewsResponse.from(cardNews);
    }

    @Transactional(readOnly = true)
    public List<CardSlideResponse> getCardSlides(Long cardNewsId){
        approvedNews(cardNewsId);
        List<CardSlide> cardSlidesList = cardSlideRepository.findCardSlidesBycardNewsId(cardNewsId);
        List<CardSlideResponse> responses = cardSlidesList.stream().sorted(java.util.Comparator.comparing(CardSlide::getSequence)).map(
                CardSlideResponse::from
        ).toList();
        return responses;
    }



    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(){
        List<Category> categoryList = categoryRepository.findAll();
        List<CategoryResponse> responses = categoryList.stream().map(
                CategoryResponse::from
        ).toList();
        return responses;
    }

    @Transactional
    public UpdateLikeResponse updateLike(String userId, Long cardNewsId, boolean liked){
        User user = findUserByUserIdOrThrow(userId);
        CardNews cardNews = approvedNews(cardNewsId);
        Optional<Likes> like = likeRepository.findByUserAndCardNews(user, cardNews);
        if (liked && like.isEmpty()) {
            likeRepository.save(Likes.of(user, cardNews));
        }

        if (!liked && like.isPresent()) {
            likeRepository.delete(like.get());
        }

        return new UpdateLikeResponse(cardNewsId, liked);
    }


    // ===== 메서드 ===== //
    private CardNews approvedNews(Long id) {
        return cardNewsRepository.findByIdAndInspectionStatusAndPublicationStatus(id, InspectionStatus.APPROVED, PublicationStatus.PUBLISHED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드 뉴스를 찾을 수 없습니다."));
    }

    User findUserByUserIdOrThrow(String userId){
        return userRepository.findByUserId(userId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유저를 찾을 수 없습니다."));
    }


}
