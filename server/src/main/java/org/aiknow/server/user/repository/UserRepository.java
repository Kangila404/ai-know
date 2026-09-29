package org.aiknow.server.user.repository;


import java.util.Optional;
import org.aiknow.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findById(Long id);

    Optional<User> findByUserId(String userId);
}

