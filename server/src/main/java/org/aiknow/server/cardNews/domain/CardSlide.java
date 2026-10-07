package org.aiknow.server.cardNews.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;

@Entity
@Table(name = "card_slide")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class CardSlide extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sequence;
    @Column(name = "img_url")
    private String imgUrl;
    @Column(length = 50, nullable = false)
    private String content;
    @Column(length = 20, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;
}
