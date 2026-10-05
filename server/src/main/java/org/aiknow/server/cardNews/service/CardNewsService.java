package org.aiknow.server.cardNews.service;

import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.dto.res.CardNewsResponse;
import org.aiknow.server.cardNews.dto.res.CardSlidesResponse;
import org.aiknow.server.cardNews.dto.res.CategoryResponse;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.cardNews.repository.CategoryRepository;
import org.aiknow.server.cardNews.repository.LikeRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.dto.req.UpdateNicknameRequest;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CardNewsService {
    private final CardNewsRepository cardNewsRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LikeRepository likeRepository;
    @Transactional(readOnly = true)
    public List<CardNewsResponse> getCardNews(Pageable pageable,Long categoryId,boolean isOnlyLiked,String userId){
        User user = findUserByUserIdOrThrow(userId);

        List<CardNews> cardNewsList = new ArrayList<>();

        if(categoryId == null && isOnlyLiked == false){
            cardNewsList = cardNewsRepository.findApproved(pageable);
        }
        if(categoryId!=null && isOnlyLiked==false){
            cardNewsList = cardNewsRepository.findApprovedAndCategoryId(pageable,categoryId);
        }
        if(categoryId==null && isOnlyLiked!=false){
            cardNewsList = cardNewsRepository.findUserLiked(user.getId(), InspectionStatus.APPROVED, pageable);
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
    public List<CardSlidesResponse> getCardSlides(Long cardNewsId){
        List<CardSlides> cardSlidesList = cardNewsRepository.findCardSlidesBycardNewsId(cardNewsId);
        List<CardSlidesResponse> responses = cardSlidesList.stream().map(
                CardSlidesResponse::from
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
    public Boolean updateLike(String userId,Long cardNewsId){
        User user = findUserByUserIdOrThrow(userId);
        CardNews cardNews = cardNewsRepository.findById(cardNewsId)
                .orElseThrow(() -> new IllegalArgumentException("카드 뉴스를 찾을 수 없습니다."));
        Like like = likeRepository.findByUserAndCardNews(user, cardNews);
        like.updateLike();
        return like.getIsLiked();
    }


    // ===== 메서드 ===== //

    User findUserByUserIdOrThrow(String userId){
        return userRepository.findByUserId(userId)
                .orElseThrow(()-> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }


}
