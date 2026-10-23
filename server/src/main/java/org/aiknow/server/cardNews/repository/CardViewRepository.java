package org.aiknow.server.cardNews.repository;
import org.aiknow.server.cardNews.domain.CardView;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CardViewRepository extends JpaRepository<CardView,Long> {
    Optional<CardView> findByUserIdAndCardNewsId(Long userId, Long cardNewsId);
    interface Counts { Long getCardId(); long getTotalViews(); long getUniqueViewers(); }
    @Query("select v.cardNewsId as cardId, sum(v.viewCount) as totalViews, count(v) as uniqueViewers from CardView v where v.cardNewsId in :ids group by v.cardNewsId")
    List<Counts> counts(Collection<Long> ids);
    @Query("select coalesce(sum(v.viewCount),0) from CardView v") long totalViews();
    @Query("select count(distinct v.userId) from CardView v") long uniqueViewers();
}
