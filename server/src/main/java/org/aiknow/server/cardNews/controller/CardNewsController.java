package org.aiknow.server.cardNews.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.dto.req.UpdateLikeRequest;
import org.aiknow.server.cardNews.dto.res.CardNewsResponse;
import org.aiknow.server.cardNews.dto.res.CardSlideResponse;
import org.aiknow.server.cardNews.dto.res.CategoryResponse;
import org.aiknow.server.cardNews.dto.res.UpdateLikeResponse;
import org.aiknow.server.cardNews.service.CardNewsService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "false") boolean isOnlyLiked,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max=200) String query
            ){
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("publicationDate"), Sort.Order.desc("id")));
        List<CardNewsResponse> response = cardNewsService.getCardNews(pageable,categoryId,userId,isOnlyLiked,query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public CardNewsResponse getCard(@PathVariable Long id, @AuthenticationPrincipal String userId) {
        return cardNewsService.getCard(id, userId);
    }
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal String userId) {
        cardNewsService.markRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/today")
    public ResponseEntity<CardNewsResponse> getTodayCardNews(@AuthenticationPrincipal String userId){
        CardNewsResponse response = cardNewsService.getTodayCardNews(userId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/cardSlides")
    public ResponseEntity<List<CardSlideResponse>> getCardSlides(
            @RequestParam Long cardNewsId
    ){
        List<CardSlideResponse> response = cardNewsService.getCardSlides(cardNewsId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(){
        List<CategoryResponse> response = cardNewsService.getCategories();
        return ResponseEntity.ok(response);
    }


    @PatchMapping("/{cardNewsId}/like")
    public ResponseEntity<UpdateLikeResponse> updateLike(
            @AuthenticationPrincipal String userId,
            @PathVariable Long cardNewsId,
            @Valid @RequestBody UpdateLikeRequest request
    ){
        UpdateLikeResponse response = cardNewsService.updateLike(userId, cardNewsId, request.liked());
        return ResponseEntity.ok(response);
    }



}
