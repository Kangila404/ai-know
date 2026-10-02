package org.aiknow.server.auth.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.aiknow.server.auth.domain.SocialProvider;

public record LoginRequest(
    @NotNull(message = "소셜 제공자는 필수입니다.")
    SocialProvider provider,

    @NotBlank(message = "소셜 사용자 ID는 필수입니다.")
    String providerId,

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
    String nickname
) {
}