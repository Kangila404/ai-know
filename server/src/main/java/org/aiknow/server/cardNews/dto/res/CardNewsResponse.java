package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.CardNews;

import java.util.List;

public record CardNewsResponse(
        Long id ,
        String title ,
        String titleImgUrl,
        List<String> keyPoints,
        String summary,
        String sourceTitle,
        String sourceUrl,
        java.time.Instant sourcePublishedAt,
        String titleImageOrigin,
        String titleImageSourceUrl,
        String titleImageCredit,
        org.aiknow.server.cardNews.domain.ContentType contentType,
        java.time.LocalDate publicationDate

){
    public static CardNewsResponse from(CardNews cardNews){
        return new CardNewsResponse(
                cardNews.getId(),
                cardNews.getTitle(),
                cardNews.getTitleImgUrl(),
                List.copyOf(cardNews.getKeyPoints()),
                cardNews.getSummary(), cardNews.getSourceTitle(), cardNews.getSourceUrl(), cardNews.getSourcePublishedAt(),
                cardNews.getTitleImageOrigin(), cardNews.getTitleImageSourceUrl(), cardNews.getTitleImageCredit(),
                cardNews.getContentType(), cardNews.getPublicationDate()
        );
    }
}
