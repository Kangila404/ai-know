package org.aiknow.server.notification.domain;

import java.util.Locale;
import org.aiknow.server.notification.exception.NotificationErrorCode;
import org.aiknow.server.notification.exception.NotificationException;

public enum DeviceType {
    ANDROID,
    IOS;

    public static DeviceType from(String value){
        if(value == null || value.isBlank()){
            throw new NotificationException(NotificationErrorCode.INVALID_PLATFORM);
        }

        try {
            return DeviceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception){
            throw new NotificationException(NotificationErrorCode.INVALID_PLATFORM);
        }
    }
}
