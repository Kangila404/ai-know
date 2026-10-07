package org.aiknow.server.notification.repository;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.aiknow.server.notification.domain.DeviceType;
import org.springframework.data.jpa.repository.Lock;
import org.aiknow.server.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceToken> findByPlatformAndInstallationId(DeviceType platform, String installationId);

    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);
}
