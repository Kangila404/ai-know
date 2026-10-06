package org.aiknow.server.notification.exception;

import lombok.Getter;
import org.aiknow.server.common.exception.AiknowException;

@Getter
public class NotificationException extends AiknowException {

    public NotificationException(NotificationErrorCode errorCode) {
        super(errorCode);
    }
}
