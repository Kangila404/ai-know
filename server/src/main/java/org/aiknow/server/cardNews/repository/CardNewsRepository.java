package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardNews;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardNewsRepository extends JpaRepository<CardNews,Long> {
    List<CardNews> findByInspectionStatus(InspectionStatus inspectionStatus, Pageable pageable);
    default List<CardNews> findApproved(Pageable pageable){
        return findByInspectionStatus(InspectionStatus.APPROVED, pageable);
    }
    @Query("""
    SELECT c
    FROM CardNews c
    WHERE c.inspectionStatus = :inspectionStatus
      AND EXISTS (
          SELECT 1
          FROM Likes l
          WHERE l.cardNews = c
            AND l.user.id = :userId
      )
    """)
    List<CardNews> findUserLiked(@Param("userId") Long userId,
                                 @Param("inspectionStatus") InspectionStatus inspectionStatus,
                                 Pageable pageable);


    @Query("""
    SELECT cn
    FROM CardNews cn
    JOIN cn.cardNewsCategory cnc
    WHERE cnc.category.id = :categoryId and
    cn.inspectionStatus =:inspectionStatus
    """)
    List<CardNews> findByCategoryIdAndInspectionStatus(
            @Param("inspectionStatus") InspectionStatus inspectionStatus,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );
    @Query("""
    SELECT c
    FROM CardNews c
    JOIN c.cardNewsCategory cnc
    WHERE c.inspectionStatus = :inspectionStatus
      AND cnc.category.id = :categoryId
      AND EXISTS (
          SELECT 1
          FROM Likes l
          WHERE l.cardNews = c
            AND l.user.id = :userId
      )
    """)
    List<CardNews> findUserLikedAndCategoryId(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("inspectionStatus") InspectionStatus inspectionStatus,
            Pageable pageable
    );

    Optional<CardNews> findCardNewsByPublicationDate(LocalDate today, InspectionStatus inspectionStatus);

}
