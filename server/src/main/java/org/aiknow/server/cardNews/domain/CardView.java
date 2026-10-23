package org.aiknow.server.cardNews.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="card_view", uniqueConstraints=@UniqueConstraint(name="uk_card_view_user_news", columnNames={"user_id","card_news_id"}),
    indexes=@Index(name="idx_card_view_news",columnList="card_news_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CardView {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private Long cardNewsId;
    @Column(nullable=false) private long viewCount;
    @Column(nullable=false) private Instant lastViewedAt;
    public CardView(Long userId, Long cardNewsId, Instant now) {
        this.userId=userId; this.cardNewsId=cardNewsId; this.viewCount=1; this.lastViewedAt=now;
    }
    public void record(Instant now) {
        if (!now.isBefore(lastViewedAt.plusSeconds(30))) { viewCount++; lastViewedAt=now; }
    }
}
