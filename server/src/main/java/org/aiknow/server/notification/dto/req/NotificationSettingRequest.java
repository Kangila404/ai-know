package org.aiknow.server.notification.dto.req;

import java.time.LocalTime;

public record NotificationSettingRequest(
    LocalTime settingTime,
    Boolean isAllowed
) {

}
