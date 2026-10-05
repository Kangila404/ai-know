package org.aiknow.server.notification.dto.res;

import java.time.LocalTime;
import org.aiknow.server.notification.domain.NotificationSetting;

public record NotificationSettingResponse(
    LocalTime settingTime,
    boolean isAllowed
) {

    public static NotificationSettingResponse from(NotificationSetting notificationSetting) {
        return new NotificationSettingResponse(
            notificationSetting.getSettingTime(),
            notificationSetting.isAllowed()
        );
    }
}
