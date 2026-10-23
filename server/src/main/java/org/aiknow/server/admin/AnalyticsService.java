package org.aiknow.server.admin;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.PublicationStatus;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AnalyticsService {
    private final UserRepository users;
    private final CardNewsRepository cards;
    private final CardViewRepository views;
    public record Summary(long users, long suspendedUsers, long readyCards, long publishedCards, long totalViews, long uniqueViewers) {}
    public record CardStats(Long id, String title, PublicationStatus publicationStatus, long totalViews, long uniqueViewers) {}
    public Summary summary() {
        return new Summary(users.count(), users.countBySuspended(true), cards.countByPublicationStatus(PublicationStatus.READY),
            cards.countByPublicationStatus(PublicationStatus.PUBLISHED), views.totalViews(), views.uniqueViewers());
    }
    public Page<CardStats> cards(String query, int page, int size) {
        var results = cards.searchForAnalytics(query.strip().toLowerCase(Locale.ROOT), PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
        var counts = results.isEmpty() ? Map.<Long,CardViewRepository.Counts>of() :
            views.counts(results.getContent().stream().map(c -> c.getId()).toList()).stream()
                .collect(Collectors.toMap(CardViewRepository.Counts::getCardId, c -> c));
        return results.map(c -> {
            var count = counts.get(c.getId());
            return new CardStats(c.getId(),c.getTitle(),c.getPublicationStatus(),count == null ? 0 : count.getTotalViews(),
                count == null ? 0 : count.getUniqueViewers());
        });
    }
}
