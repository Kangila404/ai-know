package org.aiknow.server.cardNews.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.aiknow.server.common.entity.BaseEntity;

@Entity
@Table(name = "card_slide")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Builder
public class CardSlide extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sequence;
    @Column(name = "img_url", length = 2048)
    private String imgUrl;
    @Column(length = 5000, nullable = false)
    private String content;
    @Column(length = 200, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;
    @Column(length = 100) private String layout;
    @Column(length = 2048) private String imageSourceUrl;
    @Column(length = 500) private String imageCredit;
    @Column(length = 20) private String imageOrigin;
}
