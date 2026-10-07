package org.aiknow.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.aiknow.server.common.entity.BaseEntity;
import org.aiknow.server.user.domain.User;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "notification_setting", indexes = @jakarta.persistence.Index(
    name = "idx_notification_due", columnList = "is_allowed,setting_time,id"))
public class NotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "setting_time", nullable = false)
    private LocalTime settingTime;

    @Column(name = "is_allowed", nullable = false)
    private boolean isAllowed;

    public static NotificationSetting createDefault(Long userId) {
        NotificationSetting setting = new NotificationSetting();

        setting.userId = userId;
        setting.settingTime = LocalTime.of(9, 0);
        setting.isAllowed = false;

        return setting;
    }

    public void updateTime(LocalTime settingTime) {
        this.settingTime = settingTime;
    }

    public void updateAllowed(boolean allowed) {
        this.isAllowed = allowed;
    }
}
