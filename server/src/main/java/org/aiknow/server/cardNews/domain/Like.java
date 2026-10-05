package org.aiknow.server.cardNews.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.user.domain.User;

@Entity
@Table(name = "like")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "user_id", nullable = false)
    private User user;


    @Column(name = "is_liked", nullable = false)
    private Boolean isLiked;

    public void updateLike() {
        this.isLiked = !this.isLiked;
    }


}
