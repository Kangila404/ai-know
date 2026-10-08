package org.aiknow.server.cardNews.repository;

import org.aiknow.server.cardNews.domain.CardNews;
import org.aiknow.server.cardNews.domain.Likes;
import org.aiknow.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    void deleteAllByUser(User user);
    @org.springframework.data.jpa.repository.Query("select l.cardNews.id from Likes l where l.user.id = :userId and l.cardNews.id in :ids")
    java.util.Set<Long> likedIds(Long userId, java.util.Collection<Long> ids);
    Optional<Likes> findByUserAndCardNews(User user, CardNews cardNews);
}
