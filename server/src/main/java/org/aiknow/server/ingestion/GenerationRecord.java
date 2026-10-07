package org.aiknow.server.ingestion;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "news_generation_record", uniqueConstraints = @UniqueConstraint(name = "uk_generation_attempt", columnNames = "attempt_key"))
public class GenerationRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(name = "attempt_key", nullable = false, length = 64) private String attemptKey;
    @Column(nullable = false, length = 2048) private String sourceUrl;
    @Column(nullable = false, length = 64) private String task;
    @Column(nullable = false, length = 64) private String requestHash;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String requestJson;
    @Lob @Column(columnDefinition = "MEDIUMTEXT") private String responseJson;
    @Lob @Column(columnDefinition = "MEDIUMTEXT") private String recoveredResponseJson;
    @Column(nullable = false) private Instant createdAt;

    static GenerationRecord create(String key, String url, String task, String hash, String request) {
        var record = new GenerationRecord();
        record.attemptKey = key; record.sourceUrl = url; record.task = task;
        record.requestHash = hash; record.requestJson = request; record.createdAt = Instant.now();
        return record;
    }
    void complete(String response) { responseJson = response; }
    void recover(String response) { recoveredResponseJson = response; }
}
