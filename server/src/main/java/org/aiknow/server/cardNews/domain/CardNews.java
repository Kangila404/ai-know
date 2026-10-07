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
    @Version private long version;

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
        if (publicationStatus == PublicationStatus.HIDDEN) throw new IllegalStateException("Hidden content cannot be delivered");
        if (publicationStatus == PublicationStatus.READY) {
            publicationStatus = PublicationStatus.PUBLISHED;
            publicationDate = date;
            firstUsedAt = now;
        }
        lastUsedAt = now;
    }

    public void setEditorialVisibility(boolean visible) {
        if (publicationStatus == PublicationStatus.READY) throw new IllegalStateException("Unpublished content cannot be republished");
        publicationStatus = visible ? PublicationStatus.PUBLISHED : PublicationStatus.HIDDEN;
    }

    public void editContent(org.aiknow.server.ingestion.NewsImportRequest draft) {
        title = draft.title(); summary = draft.summary();
        keyPoints.clear(); keyPoints.addAll(draft.keyPoints() == null ? List.of() : draft.keyPoints());
        var image = draft.titleImage();
        titleImgUrl = image == null ? null : image.url();
        titleImageSourceUrl = image == null ? null : image.sourceUrl();
        titleImageCredit = image == null ? null : image.credit();
        titleImageOrigin = image == null ? null : image.origin().name();
        cardSlides.clear();
        for (var slide : draft.slides()) {
            var visual = slide.image();
            cardSlides.add(CardSlide.builder().cardNews(this).sequence(slide.sequence()).title(slide.title()).content(slide.content())
                .layout(slide.layout()).imgUrl(visual == null ? null : visual.url()).imageSourceUrl(visual == null ? null : visual.sourceUrl())
                .imageCredit(visual == null ? null : visual.credit()).imageOrigin(visual == null ? null : visual.origin().name()).build());
        }
    }

    @Column(length = 500) private String sourceTitle;
    @Column(length = 2048) private String sourceUrl;
    private java.time.Instant sourcePublishedAt;
    @Column(length = 5000) private String summary;
    @Column(length = 2048) private String titleImageSourceUrl;
    @Column(length = 500) private String titleImageCredit;
    @Column(length = 20) private String titleImageOrigin;
}
