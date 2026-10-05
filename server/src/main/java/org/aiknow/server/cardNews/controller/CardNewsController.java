package org.aiknow.server.cardNews.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.dto.res.CardNewsResponse;
import org.aiknow.server.cardNews.dto.res.CardSlidesResponse;
import org.aiknow.server.cardNews.dto.res.CategoryResponse;
import org.aiknow.server.cardNews.service.CardNewsService;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "카드뉴스 API")
@RestController
@RequestMapping("/api/v1/cardNews")
@RequiredArgsConstructor
public class CardNewsController {
    private final CardNewsService cardNewsService;

    @GetMapping
    public ResponseEntity<List<CardNewsResponse>> getCardNews(
            @PageableDefault(page = 0, size = 10, sort = "publicationDate")
            Pageable pageable,
            @RequestParam(required = false) Long categoryId,
            @RequestParam() boolean isOnlyLiked,
            @RequestParam(required = false) Long userId
            ){
        List<CardNewsResponse> response = cardNewsService.getCardNews(pageable,categoryId,isOnlyLiked,userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/today")
    public ResponseEntity<CardNewsResponse> getTodayCardNews(){
        CardNewsResponse response = cardNewsService.getTodayCardNews();
        return ResponseEntity.ok(response);
    }


    @GetMapping("/cardSlides")
    public ResponseEntity<List<CardSlidesResponse>> getCardSlides(
            Long cardNewsId
    ){
        List<CardSlidesResponse> response = cardNewsService.getCardSlides(cardNewsId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(){
        List<CategoryResponse> response = cardNewsService.getCategories();
        return ResponseEntity.ok(response);
    }


    @PatchMapping("/like")
    public ResponseEntity<Boolean> updateLike(
            String userId,
            Long cardNewsId
    ){
        Boolean response = cardNewsService.updateLike(userId, cardNewsId);
        return ResponseEntity.ok(response);
    }



}
