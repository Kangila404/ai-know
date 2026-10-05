package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardNews;
import org.aiknow.server.cardNews.domain.CardSlides;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardNewsRepository extends JpaRepository<CardNews,Long> {
    List<CardNews> findByInspectionStatus(InspectionStatus inspectionStatus, Pageable pageable);
    List<CardNews> findByCategoryIdAndInspectionStatus(InspectionStatus inspectionStatus,Long categoryId, Pageable pageable);


    default List<CardNews> findApproved(Pageable pageable){
        return findByInspectionStatus(InspectionStatus.APPROVED, pageable);
    }
    default List<CardNews> findApprovedAndCategoryId(Pageable pageable,Long categoryId){
        return findByCategoryIdAndInspectionStatus(InspectionStatus.APPROVED, categoryId, pageable );
    }
    @Query("""
    SELECT c
    FROM Like l
    JOIN l.cardNews c
    WHERE l.user.id =:userId and c.inspection_status = :inspectionStatus
    ORDER BY c.id DESC
""")
    List<CardNews> findUserLiked(@Param("userId") Long userId,
                                 @Param("inspectionStatus") InspectionStatus inspectionStatus = InspectionStatus.APPROVED,
                                 Pageable pageable);

    List<CardNews> findByUserIdAndInspectionStatusAndIsLikedIsTrue(Long userId,InspectionStatus inspectionStatus,Pageable pageable);






    Optional<CardNews> findCardNewsByPublicationDate(LocalDate today, InspectionStatus inspectionStatus);
    List<CardSlides> findCardSlidesBycardNewsId(Long cardNewsId);
}
