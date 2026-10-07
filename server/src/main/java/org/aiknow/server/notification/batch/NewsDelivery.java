package org.aiknow.server.notification.batch;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "news_delivery", uniqueConstraints = @UniqueConstraint(
    name = "uk_news_delivery_day", columnNames = {"user_id", "device_token_id", "delivery_date"}),
    indexes = @Index(name = "idx_news_delivery_due", columnList = "status,next_attempt_at"))
public class NewsDelivery {
    public enum Status { PENDING, PROCESSING, SENT, FAILED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "device_token_id", nullable = false) private Long deviceTokenId;
    @Column(name = "card_news_id", nullable = false) private Long cardNewsId;
    @Column(name = "delivery_date", nullable = false) private LocalDate deliveryDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    private Instant sentAt;
    @Column(length = 100) private String lastError;

    public static NewsDelivery pending(Long userId, Long deviceId, Long newsId, LocalDate date, Instant now) {
        var delivery = new NewsDelivery();
        delivery.userId = userId;
        delivery.deviceTokenId = deviceId;
        delivery.cardNewsId = newsId;
        delivery.deliveryDate = date;
        delivery.status = Status.PENDING;
        delivery.nextAttemptAt = now;
        return delivery;
    }

    public boolean claim(Instant now, java.time.Duration lease, int maxAttempts) {
        if ((status != Status.PENDING && status != Status.PROCESSING) || nextAttemptAt.isAfter(now)) return false;
        if (attempts >= maxAttempts) { status = Status.FAILED; return false; }
        status = Status.PROCESSING;
        attempts++;
        nextAttemptAt = now.plus(lease);
        return true;
    }

    public void cancel() { status = Status.CANCELLED; }

    public void defer(Instant dueAt) {
        status = Status.PENDING;
        attempts--;
        nextAttemptAt = dueAt;
    }

    public void complete(int attempt, PushSender.Result result, Instant now, int maxAttempts) {
        if (status != Status.PROCESSING || attempts != attempt) return;
        lastError = result.errorCode();
        switch (result.status()) {
            case SENT -> { status = Status.SENT; sentAt = now; }
            case INVALID_TOKEN, PERMANENT_FAILURE -> status = Status.FAILED;
            case RETRY -> {
                status = attempts >= maxAttempts ? Status.FAILED : Status.PENDING;
                nextAttemptAt = now.plusSeconds(Math.min(3600, 60L << Math.min(attempts - 1, 6)));
            }
        }
    }
}
