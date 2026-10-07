package org.aiknow.server.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.hibernate.validator.constraints.URL;

public record NewsImportRequest(
    @NotBlank @Size(max = 500) String sourceTitle,
    @NotBlank @Size(max = 2048) @URL(protocol = "https") String sourceUrl,
    @NotNull OffsetDateTime publishedAt,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 5000) String summary,
    @Size(max = 10) List<@NotBlank @Size(max = 1000) String> keyPoints,
    @Valid Image titleImage,
    @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Slide> slides
) {
    public enum ImageOrigin { SOURCE, GENERATED }

    public record Image(
        @NotBlank @Size(max = 2048) @URL(protocol = "https") String url,
        @NotNull ImageOrigin origin,
        @Size(max = 2048) @URL(protocol = "https") String sourceUrl,
        @Size(max = 500) String credit
    ) {
        @JsonIgnore @AssertTrue(message = "외부 이미지는 원문 URL과 출처 표기가 필요합니다.")
        public boolean isAttributionValid() {
            return origin != ImageOrigin.SOURCE || (sourceUrl != null && !sourceUrl.isBlank() && credit != null && !credit.isBlank());
        }
    }

    public record Slide(@Min(1) @Max(10) int sequence,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @Size(max = 100) String layout, @Valid Image image) {}

    @JsonIgnore @AssertTrue(message = "슬라이드 순서는 배열 순서대로 1부터 연속이어야 합니다.")
    public boolean isSequenceValid() {
        return slides == null || IntStream.range(0, slides.size())
            .allMatch(i -> slides.get(i) != null && slides.get(i).sequence() == i + 1);
    }
}
