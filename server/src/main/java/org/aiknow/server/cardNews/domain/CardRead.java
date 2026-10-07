package org.aiknow.server.cardNews.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="card_read", uniqueConstraints=@UniqueConstraint(name="uk_card_read_user_news", columnNames={"user_id","card_news_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CardRead {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private Long cardNewsId;
    @Column(nullable=false) private Instant readAt;
    public CardRead(Long userId, Long cardNewsId, Instant readAt) {
        this.userId=userId; this.cardNewsId=cardNewsId; this.readAt=readAt;
    }
}
