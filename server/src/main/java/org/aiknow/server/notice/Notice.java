package org.aiknow.server.notice;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notice", indexes = @Index(name = "idx_notice_published", columnList = "published,published_at"))
public class Notice extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Lob @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false) private boolean published;
    @Column(name = "published_at") private Instant publishedAt;
    @Version private Long version;

    public static Notice create(NoticeDtos.Write request, Instant now) {
        var notice = new Notice();
        notice.update(request, now);
        return notice;
    }

    public void update(NoticeDtos.Write request, Instant now) {
        title = request.title().strip();
        content = request.content().strip();
        if (request.published() && !published) publishedAt = now;
        if (!request.published()) publishedAt = null;
        published = request.published();
    }
}
