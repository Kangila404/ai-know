package org.aiknow.server.notification.batch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.notification.repository.*;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsDeliveryPlanner {
    private final NotificationSettingRepository settings;
    private final DeviceTokenRepository tokens;
    private final CardNewsRepository news;
    private final UserRepository users;
    private final NewsDeliveryRepository deliveries;

    @Transactional
    public void plan(Long settingId, LocalDate date, LocalTime time, Instant now) {
        var setting = settings.findForUpdate(settingId).orElse(null);
        if (setting == null || !setting.isAllowed() || setting.getSettingTime().isAfter(time)
            || !users.existsById(setting.getUserId())) return;
        var article = news.findFirstByPublicationDateAndInspectionStatusOrderByIdDesc(date, InspectionStatus.APPROVED);
        if (article.isEmpty()) return;
        for (var token : tokens.findByUserIdAndActiveTrue(setting.getUserId())) {
            if (!deliveries.existsByUserIdAndDeviceTokenIdAndDeliveryDate(setting.getUserId(), token.getId(), date)) {
                deliveries.save(NewsDelivery.pending(setting.getUserId(), token.getId(), article.get().getId(), date, now));
            }
        }
    }
}
