package org.aiknow.server.profile.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements ErrorCode {

    INVALID_PROFILE_IMAGE_REQUEST(HttpStatus.BAD_REQUEST, "프로필 이미지 업로드 요청 값이 올바르지 않습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

}
