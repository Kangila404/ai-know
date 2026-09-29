package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "card_news")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CardNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cardNews",cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CardNewsCategory> cardNewsCategory=new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cardNews",cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CardSlide> cardSlides=new ArrayList<>();

    @Column(length = 20, nullable = false)
    private String title;

}
