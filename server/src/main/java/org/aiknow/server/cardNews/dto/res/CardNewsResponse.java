package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.CardNews;

import java.util.List;

public record CardNewsResponse(
        Long id ,
        String title ,
        String titleImgUrl,
        List<String>  keyPoints

){
    public static CardNewsResponse from(CardNews cardNews){
        return new CardNewsResponse(
                cardNews.getId(),
                cardNews.getTitle(),
                cardNews.getTitleImgUrl(),
                List.copyOf(cardNews.getKeyPoints())
        );
    }
}
