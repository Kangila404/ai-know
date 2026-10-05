package org.aiknow.server.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AiknowException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String message;

    public AiknowException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = errorCode.getStatus();
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
    }

    public AiknowException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.status = errorCode.getStatus();
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
    }
}
