package org.aiknow.server.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;
import org.aiknow.server.profile.domain.ProfileImg;
import org.aiknow.server.user.exception.UserErrorCode;
import org.aiknow.server.user.exception.UserException;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private String userId;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private ProfileImg profileImg;

    @Column(name = "is_onboarding_completed", nullable = false)
    private boolean isOnboardingCompleted;

    @Column(name = "last_logined_at")
    private LocalDateTime lastLogined_at;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    public static User createSocialUser(String nickname) {
        User user = new User();

        user.userId = UUID.randomUUID().toString();

        user.nickname = (nickname == null || nickname.isBlank())
            ? "새 사용자"
            : nickname.trim();

        user.isOnboardingCompleted = false;
        user.userRole = UserRole.USER;

        return user;
    }

    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new UserException(UserErrorCode.NICKNAME_REQUIRED);
        }

        String normalizedNickname = nickname.trim();

        if (normalizedNickname.length() > 20) {
            throw new UserException(UserErrorCode.NICKNAME_TOO_LONG);
        }

        this.nickname = normalizedNickname;
    }

    public void completeOnboarding() { this.isOnboardingCompleted = true; }

    public void recordLogin() {
        this.lastLogined_at = LocalDateTime.now();
    }

    public void updateProfileImg(ProfileImg profileImg) {
        this.profileImg = profileImg;
    }

}
