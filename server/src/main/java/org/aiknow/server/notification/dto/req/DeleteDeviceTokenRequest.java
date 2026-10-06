package org.aiknow.server.notification.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.aiknow.server.notification.domain.DeviceToken;

public record DeleteDeviceTokenRequest(
    @NotBlank
    @Size(max = DeviceToken.MAX_TOKEN_LENGTH)
    String token
) {

}
