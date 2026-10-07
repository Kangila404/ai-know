package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardNews;
import org.aiknow.server.cardNews.domain.Like;
import org.aiknow.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserAndCardNews(User user, CardNews cardNews);
}
