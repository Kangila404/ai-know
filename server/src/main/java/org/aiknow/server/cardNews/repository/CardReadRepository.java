package org.aiknow.server.cardNews.repository;
import org.aiknow.server.cardNews.domain.CardRead;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CardReadRepository extends JpaRepository<CardRead,Long> {
    boolean existsByUserIdAndCardNewsId(Long userId, Long cardNewsId);
    void deleteAllByUserId(Long userId);
    @Query("select r.cardNewsId from CardRead r where r.userId = :userId and r.cardNewsId in :ids")
    Set<Long> readIds(Long userId, Collection<Long> ids);
}
