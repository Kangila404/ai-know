package org.aiknow.server.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.notification.dto.req.DeleteDeviceTokenRequest;
import org.aiknow.server.notification.dto.req.NotificationSettingRequest;
import org.aiknow.server.notification.dto.req.UpsertDeviceTokenRequest;
import org.aiknow.server.notification.dto.res.DeviceTokenResponse;
import org.aiknow.server.notification.dto.res.NotificationSettingResponse;
import org.aiknow.server.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 설정 조회")
    @GetMapping("/notification-setting")
    public ResponseEntity<NotificationSettingResponse> getNotificationSetting(
        @AuthenticationPrincipal String userId
    ){
        NotificationSettingResponse response = notificationService.getSetting(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/notification-setting")
    @Operation(summary = "알림 설정 수정")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
        description = "로그인 후 GET /api/v1/auth/csrf 응답의 token 값")
    public ResponseEntity<NotificationSettingResponse> updateNotificationSetting(
        @AuthenticationPrincipal String userId,
        @RequestBody(required = false) NotificationSettingRequest request
    ){
        NotificationSettingResponse response = notificationService.updateSetting(userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/device-tokens")
    @Operation(summary = "기기 토큰 등록 또는 갱신")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
        description = "로그인 후 GET /api/v1/auth/csrf 응답의 token 값")
    public ResponseEntity<DeviceTokenResponse> upsertDeviceToken(
        @AuthenticationPrincipal String userId,
        @RequestBody(required = false) UpsertDeviceTokenRequest request
    ) {
        DeviceTokenResponse response = notificationService.upsertDeviceToken(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/device-tokens")
    @Operation(summary = "기기 토큰 비활성화")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
        description = "로그인 후 GET /api/v1/auth/csrf 응답의 token 값")
    public ResponseEntity<Void> deactivateDeviceToken(
        @AuthenticationPrincipal String userId,
        @RequestBody(required = false) DeleteDeviceTokenRequest request
    ){
        notificationService.deactivateDeviceToken(userId, request);
        return ResponseEntity.noContent().build();
    }

}
