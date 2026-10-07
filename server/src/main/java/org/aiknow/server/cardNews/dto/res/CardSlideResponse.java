package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.CardSlide;

public record CardSlideResponse(
        Long id ,
        String title ,
        String imgUrl,
        String content,
        Integer sequence,
        String layout,
        String imageOrigin,
        String imageSourceUrl,
        String imageCredit

){
    public static CardSlideResponse from(CardSlide cardSlides){
        return new CardSlideResponse(
                cardSlides.getId(),
                cardSlides.getTitle(),
                cardSlides.getImgUrl(),
                cardSlides.getContent(),
                cardSlides.getSequence(),
                cardSlides.getLayout(), cardSlides.getImageOrigin(), cardSlides.getImageSourceUrl(), cardSlides.getImageCredit()
        );
    }

}
