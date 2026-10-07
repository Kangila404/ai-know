package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.aiknow.server.common.entity.BaseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Getter
@Entity
@Table(name = "card_news", indexes = @Index(name = "idx_news_selection",
    columnList = "inspection_status,content_type,publication_status,approved_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardNews extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cardNews",cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<CardNewsCategory> cardNewsCategory=new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cardNews",cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<CardSlide> cardSlides=new ArrayList<>();

    @Column(length = 200, nullable = false)
    private String title;

    @Column(name = "title_img_url", length = 2048)
    private String titleImgUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "card_news_key_points",
            joinColumns = @JoinColumn(name = "card_news_id")
    )
    @Column(name = "key_points", nullable = false, length = 1000)
    @Builder.Default private List<String> keyPoints = new ArrayList<>();


    @Column(name = "publication_date")
    private LocalDate publicationDate;


    @Column(name = "inspection_status")
    @Enumerated(EnumType.STRING)
    private InspectionStatus inspectionStatus;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default private PublicationStatus publicationStatus = PublicationStatus.READY;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default private ContentType contentType = ContentType.NEWS;
    private java.time.Instant approvedAt;
    private java.time.Instant firstUsedAt;
    private java.time.Instant lastUsedAt;

    public void publishForDelivery(LocalDate date, java.time.Instant now) {
        if (publicationStatus == PublicationStatus.READY) {
            publicationStatus = PublicationStatus.PUBLISHED;
            publicationDate = date;
            firstUsedAt = now;
        }
        lastUsedAt = now;
    }

    @Column(length = 500) private String sourceTitle;
    @Column(length = 2048) private String sourceUrl;
    private java.time.Instant sourcePublishedAt;
    @Column(length = 5000) private String summary;
    @Column(length = 2048) private String titleImageSourceUrl;
    @Column(length = 500) private String titleImageCredit;
    @Column(length = 20) private String titleImageOrigin;
}
