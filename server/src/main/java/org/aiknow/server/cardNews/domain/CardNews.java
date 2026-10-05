package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Getter
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
    private List<CardSlides> cardSlides=new ArrayList<>();

    @Column(length = 20, nullable = false)
    private String title;

    @Column(name = "title_img_url")
    private String titleImgUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "card_news_key_points",
            joinColumns = @JoinColumn(name = "card_news_id")
    )
    @Column(name = "key_points", nullable = false)
    private List<String> keyPoints = new ArrayList<>();


    @Column(name = "publication_date", nullable = false)
    private LocalDate publicationDate;


    @Column(name = "inspection_status")
    @Enumerated(EnumType.STRING)
    private InspectionStatus inspectionStatus;
}
