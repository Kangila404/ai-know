package org.aiknow.server.notification.admin;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "admin_alert", uniqueConstraints = @UniqueConstraint(name = "uk_admin_alert_event", columnNames = "event_key"))
public class AdminAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String eventKey;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000) private String body;
    private Long submissionId;
    private Long cardNewsId;
    private LocalDate deliveryDate;
    @Column(nullable = false) private Instant createdAt;

    static AdminAlert create(String key, String type, String title, String body, Long submissionId, Long cardId, LocalDate date, Instant now) {
        var a = new AdminAlert(); a.eventKey = key; a.type = type; a.title = title; a.body = body;
        a.submissionId = submissionId; a.cardNewsId = cardId; a.deliveryDate = date; a.createdAt = now;
        return a;
    }
}
