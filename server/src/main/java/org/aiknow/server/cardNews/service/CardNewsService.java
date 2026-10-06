package org.aiknow.server.cardNews.service;

import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.dto.res.CardNewsResponse;
import org.aiknow.server.cardNews.dto.res.CardSlideResponse;
import org.aiknow.server.cardNews.dto.res.CategoryResponse;
import org.aiknow.server.cardNews.dto.res.UpdateLikeResponse;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.cardNews.repository.CardSlideRepository;
import org.aiknow.server.cardNews.repository.CategoryRepository;
import org.aiknow.server.cardNews.repository.LikeRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardNewsService {
    private final CardNewsRepository cardNewsRepository;
    private final CardSlideRepository cardSlidesRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LikeRepository likeRepository;
    @Transactional(readOnly = true)
    public List<CardNewsResponse> getCardNews(Pageable pageable,Long categoryId,String userId,boolean isOnlyLiked){

        List<CardNews> cardNewsList;
        User user = findUserByUserIdOrThrow(userId);

        if (!isOnlyLiked){
            if (categoryId == null) {
                // 1. 승인된 전체 카드뉴스
                cardNewsList = cardNewsRepository.findApproved(pageable);
            }
            // 2. 승인된 특정 카테고리 카드뉴스
            cardNewsList = cardNewsRepository
                    .findApprovedAndCategoryId(pageable, categoryId);
        }
        else {

            if (categoryId == null) {
                // 3. 유저가 좋아요한 승인 카드뉴스
                cardNewsList = cardNewsRepository.findUserLiked(
                        user.getId(),
                        InspectionStatus.APPROVED,
                        pageable
                );
            }
            // 4. 유저가 좋아요한 특정 카테고리의 승인 카드뉴스
            cardNewsList = cardNewsRepository.findUserLikedAndCategoryId(
                    user.getId(),
                    categoryId,
                    InspectionStatus.APPROVED,
                    pageable
            );
        }

        List<CardNewsResponse> responses = cardNewsList.stream().map(
               CardNewsResponse::from
        ).toList();

        return responses;
    }

    @Transactional(readOnly = true)
    public CardNewsResponse getTodayCardNews(){
        LocalDate today = LocalDate.now();
        CardNews cardNews = cardNewsRepository.findCardNewsByPublicationDate(today,InspectionStatus.APPROVED)
                .orElseThrow(()-> new IllegalArgumentException("오늘의 카드 뉴스가 존재하지 않습니다."));;
        return CardNewsResponse.from(cardNews);
    }

    @Transactional(readOnly = true)
    public List<CardSlideResponse> getCardSlides(Long cardNewsId){
        List<CardSlide> cardSlidesList = cardSlidesRepository.findCardSlidesBycardNewsId(cardNewsId);
        List<CardSlideResponse> responses = cardSlidesList.stream().map(
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
        CardNews cardNews = cardNewsRepository.findById(cardNewsId)
                .orElseThrow(() -> new IllegalArgumentException("카드 뉴스를 찾을 수 없습니다."));
        Optional<Like> like = likeRepository.findByUserAndCardNews(user, cardNews);
        if (liked && like.isEmpty()) {
            likeRepository.save(Like.of(user, cardNews));
        }

        if (!liked && like.isPresent()) {
            likeRepository.delete(like.get());
        }

        return new UpdateLikeResponse(cardNewsId, liked);
    }


    // ===== 메서드 ===== //

    User findUserByUserIdOrThrow(String userId){
        return userRepository.findByUserId(userId)
                .orElseThrow(()-> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }


}
