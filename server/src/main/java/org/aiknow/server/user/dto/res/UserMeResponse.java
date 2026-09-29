package org.aiknow.server.user.dto.res;

import java.time.LocalDateTime;
import org.aiknow.server.user.domain.User;

public record UserMeResponse(
    String nickname,
    String userRole,
    boolean onboardingCompleted,
    LocalDateTime lastLoginAt
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getNickname(),
            user.getUserRole().name(),
            user.isOnboardingCompleted(),
            user.getLastLogined_at()
        );
    }

}
