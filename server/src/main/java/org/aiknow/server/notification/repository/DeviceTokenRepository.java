package org.aiknow.server.notification.repository;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.aiknow.server.notification.domain.DeviceType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.aiknow.server.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    void deleteAllByUserId(Long userId);

    Optional<DeviceToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceToken> findByPlatformAndInstallationId(DeviceType platform, String installationId);

    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);

    @Modifying
    @Query("update DeviceToken d set d.active = false where d.id = :id and d.token = :token")
    int deactivateIfTokenMatches(Long id, String token);
}
