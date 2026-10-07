package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardNews;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.aiknow.server.cardNews.domain.PublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardNewsRepository extends JpaRepository<CardNews,Long> {
    @Query("""
        select c from CardNews c
        where c.inspectionStatus = org.aiknow.server.cardNews.domain.InspectionStatus.APPROVED
          and c.publicationStatus = org.aiknow.server.cardNews.domain.PublicationStatus.PUBLISHED
          and (:categoryId is null or exists (select 1 from CardNewsCategory cc where cc.cardNews = c and cc.category.id = :categoryId))
          and (:likedOnly = false or exists (select 1 from Likes l where l.cardNews = c and l.user.id = :userId))
          and (:query = '' or locate(:query, lower(c.title)) > 0 or locate(:query, lower(c.summary)) > 0
            or exists (select 1 from CardNewsCategory cc where cc.cardNews = c and locate(:query, lower(cc.category.name)) > 0))
        """)
    List<CardNews> searchPublished(Long userId, Long categoryId, boolean likedOnly, String query, Pageable pageable);

    org.springframework.data.domain.Page<CardNews> findByPublicationStatus(PublicationStatus status, Pageable pageable);
    List<CardNews> findByInspectionStatusAndPublicationStatus(InspectionStatus inspectionStatus, PublicationStatus publicationStatus, Pageable pageable);
    default List<CardNews> findApproved(Pageable pageable){
        return findByInspectionStatusAndPublicationStatus(InspectionStatus.APPROVED, PublicationStatus.PUBLISHED, pageable);
    }
    @Query("""
    SELECT c
    FROM CardNews c
    WHERE c.inspectionStatus = :inspectionStatus
      and exists (select 1 from Likes l where l.cardNews = c and l.user.id = :userId)
      and c.publicationStatus = org.aiknow.server.cardNews.domain.PublicationStatus.PUBLISHED
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
    and cn.publicationStatus = org.aiknow.server.cardNews.domain.PublicationStatus.PUBLISHED
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
        WHERE exists (select 1 from Likes l where l.cardNews = c and l.user.id = :userId)
          and cnc.category.id = :categoryId
          and c.inspectionStatus = :inspectionStatus
          and c.publicationStatus = org.aiknow.server.cardNews.domain.PublicationStatus.PUBLISHED
        """)
    List<CardNews> findUserLikedAndCategoryId(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("inspectionStatus") InspectionStatus inspectionStatus,
            Pageable pageable
    );

    Optional<CardNews> findByIdAndInspectionStatusAndPublicationStatus(Long id, InspectionStatus status, PublicationStatus publicationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardNews c where c.id = :id")
    Optional<CardNews> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from CardNews c
        where c.inspectionStatus = org.aiknow.server.cardNews.domain.InspectionStatus.APPROVED
          and c.publicationStatus = org.aiknow.server.cardNews.domain.PublicationStatus.READY
          and c.contentType = org.aiknow.server.cardNews.domain.ContentType.NEWS
          and c.approvedAt < :cutoff
        order by c.approvedAt desc, c.id desc
        """)
    List<CardNews> findUnusedNews(Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from CardNews c
        where c.inspectionStatus = org.aiknow.server.cardNews.domain.InspectionStatus.APPROVED
          and c.contentType = org.aiknow.server.cardNews.domain.ContentType.AI_THEORY
          and c.publicationStatus <> org.aiknow.server.cardNews.domain.PublicationStatus.HIDDEN
          and c.approvedAt < :cutoff
        order by case when c.firstUsedAt is null then 0 else 1 end,
          c.lastUsedAt asc, c.approvedAt desc, c.id desc
        """)
    List<CardNews> findTheoryForDelivery(Instant cutoff, Pageable pageable);

}
