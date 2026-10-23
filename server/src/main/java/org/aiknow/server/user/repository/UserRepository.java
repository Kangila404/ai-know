package org.aiknow.server.user.repository;


import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.aiknow.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where (:query = '' or locate(:query, lower(u.nickname)) > 0 or locate(:query, lower(u.userId)) > 0) and (:role is null or u.userRole = :role) and (:suspended is null or u.suspended = :suspended)")
    org.springframework.data.domain.Page<User> search(String query, org.aiknow.server.user.domain.UserRole role, Boolean suspended, org.springframework.data.domain.Pageable pageable);

    long countBySuspended(boolean suspended);
    boolean existsByIdAndSuspendedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userRole = org.aiknow.server.user.domain.UserRole.ADMIN order by u.id")
    java.util.List<User> lockAdministrators();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(Long id);

    Optional<User> findById(Long id);

    Optional<User> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByUserIdForUpdate(@Param("userId") String userId);
}

