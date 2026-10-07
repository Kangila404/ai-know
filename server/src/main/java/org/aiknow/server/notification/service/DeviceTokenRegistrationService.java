package org.aiknow.server.notification.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.notification.domain.DeviceToken;
import org.aiknow.server.notification.domain.DeviceType;
import org.aiknow.server.notification.dto.res.DeviceTokenResponse;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
public class DeviceTokenRegistrationService {

    private final DeviceTokenRepository deviceTokenRepository;

    // 중복 INSERT 실패 후 재시도할 때도 새 트랜잭션에서 조회해야 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DeviceTokenResponse upsert(Long userId, String token, DeviceType platform) {
        return register(userId, token, platform, null);
    }

    // READ_COMMITTED avoids MySQL gap-lock deadlocks when the installation does not exist yet.
    // The unique constraint and caller's retry protect concurrent first registrations.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DeviceTokenResponse upsert(Long userId, String token, DeviceType platform, String installationId) {
        return register(userId, token, platform, installationId);
    }

    private DeviceTokenResponse register(Long userId, String token, DeviceType platform, String installationId) {
        LocalDateTime now = LocalDateTime.now();
        DeviceToken deviceToken = installationId == null ? null : deviceTokenRepository
            .findByPlatformAndInstallationId(platform, installationId).orElse(null);
        DeviceToken tokenOwner = deviceTokenRepository.findByToken(token).orElse(null);
        if (deviceToken != null && tokenOwner != null && !deviceToken.getId().equals(tokenOwner.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "토큰이 다른 설치에 등록되어 있습니다.");
        }
        if (deviceToken == null) deviceToken = tokenOwner;
        if (deviceToken == null) deviceToken = DeviceToken.register(userId, token, platform, now);
        if (deviceToken.getInstallationId() != null && deviceToken.getPlatform() != platform) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "설치된 기기의 플랫폼을 변경할 수 없습니다.");
        }
        deviceToken.updateRegistration(userId, token, platform, installationId, now);
        deviceTokenRepository.saveAndFlush(deviceToken);

        return DeviceTokenResponse.of(deviceToken, deviceToken.getLastUsedAt());
    }
}
