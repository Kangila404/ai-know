package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;
import org.aiknow.server.user.domain.User;

@Entity
@Table(name = "likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class Like extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "user_id", nullable = false)
    private User user;


    private Like(User user, CardNews cardNews) {
        this.user = user;
        this.cardNews = cardNews;
    }

    public static Like of(User user, CardNews cardNews) {
        return new Like(user, cardNews);
    }

}
