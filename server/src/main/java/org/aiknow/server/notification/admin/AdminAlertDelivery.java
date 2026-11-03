package org.aiknow.server.notification.admin;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import org.aiknow.server.notification.batch.PushSender;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "admin_alert_delivery", uniqueConstraints = @UniqueConstraint(name = "uk_admin_alert_device", columnNames = {"alert_id", "device_token_id"}),
    indexes = @Index(name = "idx_admin_alert_due", columnList = "status,next_attempt_at"))
public class AdminAlertDelivery {
    public enum Status { PENDING, PROCESSING, SENT, FAILED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long alertId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long deviceTokenId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant sentAt;
    @Column(length = 100) private String lastError;

    static AdminAlertDelivery pending(Long alert, Long user, Long device, Instant now) {
        var d = new AdminAlertDelivery(); d.alertId = alert; d.userId = user; d.deviceTokenId = device;
        d.status = Status.PENDING; d.nextAttemptAt = now; return d;
    }
    boolean claim(Instant now) {
        if ((status != Status.PENDING && status != Status.PROCESSING) || nextAttemptAt.isAfter(now)) return false;
        if (attempts >= 5) { status = Status.FAILED; return false; }
        status = Status.PROCESSING; attempts++; nextAttemptAt = now.plusSeconds(300); return true;
    }
    void cancel() { status = Status.CANCELLED; }
    void complete(int attempt, PushSender.Result result, Instant now) {
        if (status != Status.PROCESSING || attempts != attempt) return;
        lastError = result.errorCode();
        switch (result.status()) {
            case SENT -> { status = Status.SENT; sentAt = now; }
            case INVALID_TOKEN, PERMANENT_FAILURE -> status = Status.FAILED;
            case RETRY -> { status = attempts >= 5 ? Status.FAILED : Status.PENDING; nextAttemptAt = now.plusSeconds(60L << (attempts - 1)); }
        }
    }
}
