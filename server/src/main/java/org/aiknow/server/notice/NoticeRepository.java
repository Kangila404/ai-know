package org.aiknow.server.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findByPublishedTrueOrderByPublishedAtDescIdDesc(Pageable pageable);
    Page<Notice> findAllByOrderByIdDesc(Pageable pageable);
}
