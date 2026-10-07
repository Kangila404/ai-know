package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardSlide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardSlideRepository extends JpaRepository<CardSlide,Long> {
    List<CardSlide> findCardSlidesBycardNewsId(Long cardNewsId);
}
