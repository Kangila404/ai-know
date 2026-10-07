package org.aiknow.server.notification.batch;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.notification.repository.*;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsDeliveryLedger {
    private final NewsDeliveryRepository deliveries;
    private final DeviceTokenRepository tokens;
    private final NotificationSettingRepository settings;
    private final CardNewsRepository news;
    private final UserRepository users;
    private final NewsDeliveryProperties properties;

    @Transactional
    public Optional<PushSender.Message> claim(Long id, Instant now) {
        var delivery = deliveries.findForUpdate(id).orElse(null);
        if (delivery == null || !delivery.claim(now, properties.lease(), properties.maxAttempts())) return Optional.empty();
        var token = tokens.findById(delivery.getDeviceTokenId()).orElse(null);
        var setting = settings.findByUserId(delivery.getUserId()).orElse(null);
        var article = news.findById(delivery.getCardNewsId()).orElse(null);
        var localNow = now.atZone(properties.zone());
        if (token == null || !token.isActive() || !token.getUserId().equals(delivery.getUserId())
            || !users.existsById(delivery.getUserId()) || setting == null || !setting.isAllowed()
            || !localNow.toLocalDate().equals(delivery.getDeliveryDate())
            || article == null || article.getInspectionStatus() != InspectionStatus.APPROVED) {
            delivery.cancel();
            return Optional.empty();
        }
        // A user may move today's notification to a later time after planning.
        if (setting.getSettingTime().isAfter(localNow.toLocalTime())) {
            delivery.defer(delivery.getDeliveryDate().atTime(setting.getSettingTime()).atZone(properties.zone()).toInstant());
            return Optional.empty();
        }
        return Optional.of(new PushSender.Message(delivery.getId(), delivery.getAttempts(), token.getToken(),
            token.getPlatform(), article.getId(), article.getTitle()));
    }

    @Transactional
    public void complete(PushSender.Message message, PushSender.Result result, Instant now) {
        var delivery = deliveries.findForUpdate(message.deliveryId()).orElseThrow();
        delivery.complete(message.attempt(), result, now, properties.maxAttempts());
        if (result.status() == PushSender.Status.INVALID_TOKEN) {
            tokens.deactivateIfTokenMatches(delivery.getDeviceTokenId(), message.token());
        }
    }
}
