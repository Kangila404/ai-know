package org.aiknow.server.notification.batch;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface DailyNewsEditionRepository extends JpaRepository<DailyNewsEdition, LocalDate> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DailyNewsEdition e where e.deliveryDate = :date")
    Optional<DailyNewsEdition> findForUpdate(LocalDate date);
}
