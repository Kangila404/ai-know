package org.aiknow.server.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "닉네임은 필수입니다."),
    NICKNAME_TOO_LONG(HttpStatus.BAD_REQUEST, "닉네임은 20자 이하여야 합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 프로필 이미지를 찾을 수 없습니다."),
    INCOMPLETE_ONBOARDING(HttpStatus.FORBIDDEN, "온보딩을 완료하지 않은 회원입니다.");

    private final HttpStatus status;
    private final String message;

}
