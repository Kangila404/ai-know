package org.aiknow.server.notification.batch;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface NewsDeliveryRepository extends JpaRepository<NewsDelivery, Long> {
    void deleteAllByUserId(Long userId);

    boolean existsByUserIdAndDeviceTokenIdAndDeliveryDate(Long userId, Long deviceTokenId, LocalDate date);

    @Query("select d.id from NewsDelivery d where d.status in :statuses and d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id")
    List<Long> findDue(List<NewsDelivery.Status> statuses, Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NewsDelivery d where d.id = :id")
    Optional<NewsDelivery> findForUpdate(Long id);
}
