package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.CardSlides;

public record CardSlidesResponse(
        Long id ,
        String title ,
        String imgUrl,
        String content,
        Integer sequence

){
    public static CardSlidesResponse from(CardSlides cardSlides){
        return new CardSlidesResponse(
                cardSlides.getId(),
                cardSlides.getTitle(),
                cardSlides.getImgUrl(),
                cardSlides.getContent(),
                cardSlides.getSequence()
        );
    }
}
