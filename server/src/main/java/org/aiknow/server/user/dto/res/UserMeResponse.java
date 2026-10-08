package org.aiknow.server.user.dto.res;

import java.time.LocalDateTime;
import org.aiknow.server.user.domain.User;

public record UserMeResponse(
    String nickname,
    String userId,
    String userRole,
    boolean onboardingCompleted,
    LocalDateTime lastLoginAt,
    org.aiknow.server.profile.dto.res.ProfileResponse profile
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getNickname(), user.getUserId(),
            user.getUserRole().name(),
            user.isOnboardingCompleted(),
            user.getLastLogined_at(),
            user.getProfileImg() == null ? null : org.aiknow.server.profile.dto.res.ProfileResponse.from(user.getProfileImg())
        );
    }

}
