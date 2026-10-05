package org.aiknow.server.user.exception;

import lombok.Getter;
import org.aiknow.server.common.exception.AiknowException;

@Getter
public class UserException extends AiknowException {



    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
