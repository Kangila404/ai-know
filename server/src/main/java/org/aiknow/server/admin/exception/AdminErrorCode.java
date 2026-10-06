package org.aiknow.server.admin.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "관리자 계정이 아닙니다.");


    private final HttpStatus status;
    private final String message;
}
