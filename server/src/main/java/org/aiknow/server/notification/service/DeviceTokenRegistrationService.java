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

@Service
@RequiredArgsConstructor
public class DeviceTokenRegistrationService {

    private final DeviceTokenRepository deviceTokenRepository;

    // 중복 INSERT 실패 후 재시도할 때도 새 트랜잭션에서 조회해야 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeviceTokenResponse upsert(Long userId, String token, DeviceType platform) {
        LocalDateTime now = LocalDateTime.now();
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
            .map(saved -> {
                saved.reactivate(userId, platform, now);
                return saved;
            })
            .orElseGet(() -> deviceTokenRepository.save(
                DeviceToken.register(userId, token, platform, now)
            ));

        return DeviceTokenResponse.of(deviceToken, deviceToken.getLastUsedAt());
    }
}
