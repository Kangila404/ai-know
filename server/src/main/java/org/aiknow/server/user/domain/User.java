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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;

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

    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 비어 있거나 공백만으로 구성될 수 없습니다.");
        }

        String normalizedNickname = nickname.trim();

        if (this.nickname.equals(normalizedNickname)) {
            throw new IllegalArgumentException("현재 사용 중인 닉네임과 같습니다.");
        }

        this.nickname = normalizedNickname;
    }

}
