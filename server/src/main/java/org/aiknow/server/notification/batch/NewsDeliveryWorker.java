package org.aiknow.server.notification.batch;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiknow.server.notification.repository.NotificationSettingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsDeliveryWorker {
    private final Clock clock;
    private final NewsDeliveryProperties properties;
    private final NotificationSettingRepository settings;
    private final NewsDeliveryRepository deliveries;
    private final NewsDeliveryPlanner planner;
    private final NewsDeliveryLedger ledger;
    private final PushSender sender;
    private final DailyNewsEditionService editions;

    public void plan() {
        var now = clock.instant();
        var local = now.atZone(properties.zone());
        var edition = editions.ensure(local.toLocalDate(), now);
        if (edition.getCardNewsId() == null) return;
        long cursor = 0;
        while (true) {
            var ids = settings.findDueIds(cursor, local.toLocalTime(), PageRequest.of(0, properties.pageSize()));
            if (ids.isEmpty()) break;
            for (Long id : ids) planner.plan(id, local.toLocalDate(), local.toLocalTime(), now);
            cursor = ids.getLast();
        }
    }

    public void dispatch() {
        // A bounded page per tick prevents a failing provider from monopolizing the scheduler.
        var ids = deliveries.findDue(List.of(NewsDelivery.Status.PENDING, NewsDelivery.Status.PROCESSING),
            clock.instant(), PageRequest.of(0, properties.pageSize()));
        for (Long id : ids) {
            var claim = ledger.claim(id, clock.instant());
            if (claim.isEmpty()) continue;
            var message = claim.get();
            PushSender.Result result;
            try { result = sender.send(message); }
            catch (RuntimeException exception) {
                // Never log FCM tokens or credentials, including provider exception text.
                log.warn("Push provider call failed for delivery {}", id);
                result = new PushSender.Result(PushSender.Status.RETRY, "PROVIDER_FAILURE");
            }
            ledger.complete(message, result, clock.instant());
        }
    }
}
