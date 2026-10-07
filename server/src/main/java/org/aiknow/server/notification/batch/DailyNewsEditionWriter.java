package org.aiknow.server.notification.batch;

import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class DailyNewsEditionWriter {
    private final DailyNewsEditionRepository editions;
    private final CardNewsRepository news;
    private final NewsDeliveryProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DailyNewsEdition select(LocalDate date, Instant now) {
        var existing = editions.findById(date);
        if (existing.isPresent()) return existing.get();
        // Recovery after downtime uses the same midnight cutoff, never the restart time.
        var cutoff = date.atStartOfDay(properties.zone()).toInstant();
        var candidates = news.findUnusedNews(cutoff, PageRequest.of(0, 1));
        if (candidates.isEmpty()) candidates = news.findTheoryForDelivery(cutoff, PageRequest.of(0, 1));
        Long articleId = candidates.isEmpty() ? null : candidates.getFirst().getId();
        return editions.saveAndFlush(DailyNewsEdition.select(date, articleId, now, cutoff));
    }
}
