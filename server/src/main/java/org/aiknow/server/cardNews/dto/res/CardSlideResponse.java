package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.CardSlide;

public record CardSlideResponse(
        Long id ,
        String title ,
        String imgUrl,
        String content,
        Integer sequence

){
    public static CardSlideResponse from(CardSlide cardSlides){
        return new CardSlideResponse(
                cardSlides.getId(),
                cardSlides.getTitle(),
                cardSlides.getImgUrl(),
                cardSlides.getContent(),
                cardSlides.getSequence()
        );
    }

}
