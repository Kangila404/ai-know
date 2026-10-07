package org.aiknow.server.notification.repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.aiknow.server.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByUserId(Long userId);

    @Query("select s.id from NotificationSetting s where s.id > :afterId and s.isAllowed = true and s.settingTime <= :time order by s.id")
    List<Long> findDueIds(Long afterId, LocalTime time, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NotificationSetting s where s.id = :id")
    Optional<NotificationSetting> findForUpdate(Long id);
}
