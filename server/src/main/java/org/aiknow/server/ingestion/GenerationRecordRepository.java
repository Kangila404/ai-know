package org.aiknow.server.ingestion;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface GenerationRecordRepository extends JpaRepository<GenerationRecord, Long> {
    Optional<GenerationRecord> findByAttemptKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GenerationRecord r where r.id = :id")
    Optional<GenerationRecord> findForUpdate(Long id);
}
