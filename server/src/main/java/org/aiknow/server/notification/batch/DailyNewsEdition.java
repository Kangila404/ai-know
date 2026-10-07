package org.aiknow.server.notification.batch;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One immutable choice per Korean calendar date; a null article fixes an empty day too. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "daily_news_edition")
public class DailyNewsEdition {
    @Id private LocalDate deliveryDate;
    // Assigned date IDs must use INSERT (not merge) on creation: never overwrite a competing day's choice.
    @Version private Long version;
    private Long cardNewsId;
    @Column(nullable = false) private Instant selectedAt;
    @Column(nullable = false) private Instant approvalCutoff;
    private Instant startedAt;

    public static DailyNewsEdition select(LocalDate date, Long articleId, Instant now, Instant cutoff) {
        var edition = new DailyNewsEdition();
        edition.deliveryDate = date; edition.cardNewsId = articleId;
        edition.selectedAt = now; edition.approvalCutoff = cutoff;
        return edition;
    }

    public void start(Instant now) { if (startedAt == null) startedAt = now; }
}
