package org.aiknow.server.notification.dto.res;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.aiknow.server.notification.domain.DeviceToken;

public record DeviceTokenResponse(
    Long deviceTokenId,
    String platform,
    boolean active,
    OffsetDateTime lastUsedAt
) {

    public static DeviceTokenResponse of(
        DeviceToken deviceToken,
        LocalDateTime lastUsedAt
    ) {
        return new DeviceTokenResponse(
            deviceToken.getId(),
            deviceToken.getPlatform().name(),
            deviceToken.isActive(),
            lastUsedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        );
    }

}
