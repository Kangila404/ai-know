package org.aiknow.server.notification.admin;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.notification.batch.PushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "app.news-delivery.fcm-enabled", havingValue = "true")
public class AdminAlertDispatcher {
    private final AdminAlertDeliveryRepository deliveries;
    private final AdminAlertLedger ledger;
    private final PushSender sender;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.admin-alerts.dispatch-delay-ms:30000}")
    public void dispatch() {
        for (Long id : deliveries.due(List.of(AdminAlertDelivery.Status.PENDING, AdminAlertDelivery.Status.PROCESSING),
            clock.instant(), PageRequest.of(0, 25))) {
            var claim = ledger.claim(id, clock.instant());
            if (claim.isEmpty()) continue;
            PushSender.Result result;
            try { result = sender.send(claim.get()); }
            catch (RuntimeException failure) { result = new PushSender.Result(PushSender.Status.RETRY, "PROVIDER_FAILURE"); }
            ledger.complete(claim.get(), result, clock.instant());
        }
    }
}
