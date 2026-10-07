package org.aiknow.server.notification.batch;

import java.time.Duration;
import java.time.ZoneId;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.news-delivery")
public record NewsDeliveryProperties(boolean enabled, @NotNull ZoneId zone,
    @Min(1) @Max(1000) int pageSize, @Min(1) @Max(10) int maxAttempts,
    @NotNull Duration lease, boolean fcmEnabled, String credentialsPath) {
    public NewsDeliveryProperties {
        if (lease != null && lease.compareTo(Duration.ofMinutes(2)) < 0)
            throw new IllegalArgumentException("News delivery lease must be at least two minutes");
        if (enabled && !fcmEnabled)
            throw new IllegalArgumentException("NEWS_BATCH_ENABLED requires FCM_ENABLED");
    }
}
