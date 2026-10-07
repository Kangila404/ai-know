package org.aiknow.server.notice;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDateTime;

public final class NoticeDtos {
    private NoticeDtos() {}

    public record Write(@NotBlank @Size(max = 200) String title,
                        @NotBlank @Size(max = 10000) String content, @NotNull Boolean published) {}

    public record View(Long id, String title, String content, boolean published,
                       Instant publishedAt, LocalDateTime updatedAt) {
        public static View from(Notice notice) {
            return new View(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPublished(),
                notice.getPublishedAt(), notice.getUpdatedAt());
        }
    }
}
