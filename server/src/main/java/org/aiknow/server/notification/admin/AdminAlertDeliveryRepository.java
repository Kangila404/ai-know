package org.aiknow.server.notification.admin;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AdminAlertDeliveryRepository extends JpaRepository<AdminAlertDelivery, Long> {
    @Query("select d.id from AdminAlertDelivery d where d.status in :statuses and d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id")
    List<Long> due(List<AdminAlertDelivery.Status> statuses, Instant now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from AdminAlertDelivery d where d.id = :id")
    Optional<AdminAlertDelivery> lock(Long id);
}
