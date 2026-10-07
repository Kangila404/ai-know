package org.aiknow.server.editorial;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_editorial_resource", columnList = "resource_type,resource_id,id"))
public class EditorialAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32) private String resourceType;
    @Column(nullable = false) private Long resourceId;
    @Column(nullable = false, length = 32) private String action;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 2000) private String reason;
    @Lob @Column(columnDefinition = "MEDIUMTEXT") private String beforeJson;
    @Lob @Column(columnDefinition = "MEDIUMTEXT") private String afterJson;
    public static EditorialAudit of(String type, Long resourceId, String action, Long actorId, Instant time, String reason, String before, String after) {
        var audit = new EditorialAudit(); audit.resourceType = type; audit.resourceId = resourceId;
        audit.action = action; audit.actorId = actorId; audit.occurredAt = time; audit.reason = reason;
        audit.beforeJson = before; audit.afterJson = after; return audit;
    }
}
