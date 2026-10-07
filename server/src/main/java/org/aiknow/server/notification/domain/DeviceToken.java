package org.aiknow.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;

@Getter
@Entity
@Table(name = "device_token", uniqueConstraints =
    @UniqueConstraint(name = "uk_device_installation", columnNames = {"platform", "installation_id"}),
    indexes = @jakarta.persistence.Index(name = "idx_device_user_active", columnList = "user_id,active"))
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseEntity {


    public static final int MAX_TOKEN_LENGTH = 512;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = MAX_TOKEN_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceType platform;

    @Column(name = "installation_id", length = 36)
    private String installationId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    private DeviceToken(Long userId, String token, DeviceType platform, LocalDateTime lastUsedAt) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.active = true;
        this.lastUsedAt = lastUsedAt;
    }

    public static DeviceToken register(Long userId, String token, DeviceType platform, LocalDateTime lastUsedAt) {
        return new DeviceToken(userId, token, platform, lastUsedAt);
    }

    public void reactivate(Long userId, DeviceType platform, LocalDateTime lastUsedAt) {
        this.userId = userId;
        this.platform = platform;
        this.active = true;
        this.lastUsedAt = lastUsedAt;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateRegistration(Long userId, String token, DeviceType platform,
                                   String installationId, LocalDateTime now) {
        reactivate(userId, platform, now);
        this.token = token;
        if (installationId != null) this.installationId = installationId;
    }


}
