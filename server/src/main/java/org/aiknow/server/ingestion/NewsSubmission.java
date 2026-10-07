package org.aiknow.server.ingestion;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.aiknow.server.common.entity.BaseEntity;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "news_submission", uniqueConstraints = @UniqueConstraint(name = "uk_submission_source", columnNames = "source_hash"),
    indexes = @Index(name = "idx_submission_status", columnList = "status,id"))
public class NewsSubmission extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Lob @Column(columnDefinition = "MEDIUMTEXT") private String editedPayload;
    @Column(name = "source_hash", nullable = false, length = 64) private String sourceHash;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(nullable = false, length = 2048) private String sourceUrl;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InspectionStatus status;
    private Long reviewedBy;
    private Instant reviewedAt;
    @Column(length = 2000) private String reviewNote;
    private Long cardNewsId;

    public static NewsSubmission create(String sourceHash, String payloadHash, String sourceUrl, String payload) {
        var submission = new NewsSubmission();
        submission.sourceHash = sourceHash; submission.payloadHash = payloadHash;
        submission.sourceUrl = sourceUrl; submission.payload = payload; submission.status = InspectionStatus.PENDING;
        return submission;
    }

    public void approve(Long newsId, Long reviewer, Instant now) {
        status = InspectionStatus.APPROVED; cardNewsId = newsId; reviewedBy = reviewer; reviewedAt = now;
    }

    public void reject(String reason, Long reviewer, Instant now) {
        status = InspectionStatus.DENIED; reviewNote = reason; reviewedBy = reviewer; reviewedAt = now;
    }
    public void edit(String payload) { editedPayload = payload; }
    public void reopen() { status = InspectionStatus.PENDING; reviewedBy = null; reviewedAt = null; reviewNote = null; }
}
