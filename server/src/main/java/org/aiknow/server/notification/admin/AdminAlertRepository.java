package org.aiknow.server.notification.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, Long> {
    Optional<AdminAlert> findByEventKey(String eventKey);
}
