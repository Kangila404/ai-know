package org.aiknow.server.notification.admin;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.notification.batch.PushSender;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.user.domain.UserRole;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminAlertLedger {
    private final AdminAlertDeliveryRepository deliveries;
    private final AdminAlertRepository alerts;
    private final DeviceTokenRepository devices;
    private final UserRepository users;

    @Transactional
    public Optional<PushSender.Message> claim(Long id, Instant now) {
        var d = deliveries.lock(id).orElse(null);
        if (d == null || !d.claim(now)) return Optional.empty();
        var user = users.findById(d.getUserId()).orElse(null);
        var token = devices.findById(d.getDeviceTokenId()).orElse(null);
        var alert = alerts.findById(d.getAlertId()).orElse(null);
        if (user == null || user.isSuspended() || user.getUserRole() != UserRole.ADMIN
            || token == null || !token.isActive() || !token.getUserId().equals(d.getUserId()) || alert == null
            || alert.getCreatedAt().isBefore(now.minus(Duration.ofDays(2)))) {
            d.cancel(); return Optional.empty();
        }
        return Optional.of(new PushSender.Message(d.getId(), d.getAttempts(), token.getToken(), token.getPlatform(), null,
            alert.getBody(), alert.getTitle(), Map.of("adminAlertId", alert.getId().toString(), "type", alert.getType()),
            "admin-"+alert.getId(), "admin_operations"));
    }

    @Transactional
    public void complete(PushSender.Message message, PushSender.Result result, Instant now) {
        var d = deliveries.lock(message.deliveryId()).orElseThrow();
        d.complete(message.attempt(), result, now);
        if (result.status() == PushSender.Status.INVALID_TOKEN) devices.deactivateIfTokenMatches(d.getDeviceTokenId(), message.token());
    }
}
