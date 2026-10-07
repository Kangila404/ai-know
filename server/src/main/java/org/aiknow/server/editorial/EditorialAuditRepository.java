package org.aiknow.server.editorial;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EditorialAuditRepository extends JpaRepository<EditorialAudit, Long> {
    Page<EditorialAudit> findByResourceTypeAndResourceIdOrderByIdDesc(String resourceType, Long resourceId, Pageable pageable);
}
