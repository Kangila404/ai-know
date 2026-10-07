package org.aiknow.server.ingestion;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NewsSubmissionRepository extends JpaRepository<NewsSubmission, Long> {
    Optional<NewsSubmission> findBySourceHash(String hash);
    Page<NewsSubmission> findByStatusOrderByIdDesc(InspectionStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NewsSubmission s where s.id = :id")
    Optional<NewsSubmission> findForUpdate(Long id);
}
