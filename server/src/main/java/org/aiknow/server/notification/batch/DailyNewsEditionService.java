package org.aiknow.server.notification.batch;

import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class DailyNewsEditionService {
    private final DailyNewsEditionRepository editions;
    private final DailyNewsEditionWriter writer;

    public DailyNewsEdition ensure(LocalDate date, Instant now) {
        var existing = editions.findById(date);
        if (existing.isPresent()) return existing.get();
        try { return writer.select(date, now); }
        catch (DataIntegrityViolationException race) {
            return editions.findById(date).orElseThrow(() -> race);
        }
    }
}
