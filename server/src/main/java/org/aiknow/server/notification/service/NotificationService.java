package org.aiknow.server.notification.service;

import lombok.RequiredArgsConstructor;
import org.aiknow.server.notification.domain.DeviceToken;
import org.aiknow.server.notification.domain.DeviceType;
import org.aiknow.server.notification.domain.NotificationSetting;
import org.aiknow.server.notification.dto.req.DeleteDeviceTokenRequest;
import org.aiknow.server.notification.dto.req.NotificationSettingRequest;
import org.aiknow.server.notification.dto.req.UpsertDeviceTokenRequest;
import org.aiknow.server.notification.dto.res.DeviceTokenResponse;
import org.aiknow.server.notification.dto.res.NotificationSettingResponse;
import org.aiknow.server.notification.exception.NotificationErrorCode;
import org.aiknow.server.notification.exception.NotificationException;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.notification.repository.NotificationSettingRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.exception.UserErrorCode;
import org.aiknow.server.user.exception.UserException;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenRegistrationService deviceTokenRegistrationService;

    @Transactional
    public NotificationSettingResponse getSetting(String userId){
        User user = findUserForSettingUpdate(userId);

        NotificationSetting notificationSetting = notificationSettingRepository.findByUserId(user.getId())
            .orElseGet(()->notificationSettingRepository.save(NotificationSetting.createDefault(user.getId())));

        return NotificationSettingResponse.from(notificationSetting);
    }

    @Transactional
    public NotificationSettingResponse updateSetting(
        String userId,
        NotificationSettingRequest request
    ) {
        validateNotificationSettingRequest(request);
        User user = findUserForSettingUpdate(userId);

        NotificationSetting notificationSetting =
            notificationSettingRepository.findByUserId(user.getId())
                .orElseGet(() -> notificationSettingRepository.save(
                    NotificationSetting.createDefault(user.getId())
                ));

        if (request.isAllowed() != null) {
            notificationSetting.updateAllowed(request.isAllowed());
        }

        if (request.settingTime() != null) {
            notificationSetting.updateTime(request.settingTime());
        }

        return NotificationSettingResponse.from(notificationSetting);
    }

    public DeviceTokenResponse upsertDeviceToken(String userId, UpsertDeviceTokenRequest request){
        User user = findUserByUserId(userId);
        validateUpsertDeviceTokenRequest(request);

        String token = validateAndNormalizedToken(request.token());
        DeviceType platform = DeviceType.from(request.platform());

        try {
            return deviceTokenRegistrationService.upsert(user.getId(), token, platform);
        } catch (DataIntegrityViolationException exception) {
            // 다른 요청이 같은 토큰을 먼저 등록한 경우에만 한 번 재시도한다.
            if (deviceTokenRepository.findByToken(token).isEmpty()) {
                throw exception;
            }
            return deviceTokenRegistrationService.upsert(user.getId(), token, platform);
        }
    }

    @Transactional
    public void deactivateDeviceToken(String userId, DeleteDeviceTokenRequest request){
        User user = findUserByUserId(userId);

        if (request == null) {
            throw new NotificationException(
                NotificationErrorCode.INVALID_DEVICE_TOKEN
            );
        }

        String token = validateAndNormalizedToken(request.token());
        deviceTokenRepository.findByToken(token)
            .filter(deviceToken -> deviceToken.getUserId().equals(user.getId()))
            .ifPresent(DeviceToken::deactivate);
    }
    // ======= 메서드 ======= //
    private User findUserByUserId(String userId){
        return userRepository.findByUserId(userId)
            .orElseThrow(()-> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private User findUserForSettingUpdate(String userId) {
        // 아직 설정 행이 없어도 존재하는 사용자 행을 잠가 최초 생성을 직렬화한다.
        return userRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private void validateNotificationSettingRequest(NotificationSettingRequest request){
        if(request == null){
            throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_SETTING);
        }
    }

    private void validateUpsertDeviceTokenRequest(UpsertDeviceTokenRequest request) {
        if (request == null) {
            throw new NotificationException(NotificationErrorCode.INVALID_DEVICE_TOKEN);
        }
    }

    private String validateAndNormalizedToken(String token){
        if(token == null){
            throw new NotificationException(NotificationErrorCode.INVALID_DEVICE_TOKEN);
        }

        String normalizedToken = token.trim();
        if(normalizedToken.isEmpty() || normalizedToken.length() > DeviceToken.MAX_TOKEN_LENGTH){
            throw new NotificationException(NotificationErrorCode.INVALID_DEVICE_TOKEN);
        }
        return normalizedToken;
    }

}
