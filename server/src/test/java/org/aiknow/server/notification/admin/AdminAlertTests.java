package org.aiknow.server.notification.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import org.aiknow.server.notification.batch.PushSender;
import org.aiknow.server.notification.domain.*;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.user.domain.*;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-alerts;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class AdminAlertTests {
    @Autowired AdminAlertService service;
    @Autowired AdminAlertRepository alerts;
    @Autowired AdminAlertDeliveryRepository deliveries;
    @Autowired AdminAlertLedger ledger;
    @Autowired UserRepository users;
    @Autowired DeviceTokenRepository devices;
    @Autowired MockMvc mvc;

    private User account(UserRole role, boolean suspended) {
        var user = User.createSocialUser("운영자"); user.updateAdministration(role, suspended);
        return users.saveAndFlush(user);
    }
    private DeviceToken device(User user, String token) {
        return devices.saveAndFlush(DeviceToken.register(user.getId(), token, DeviceType.IOS, LocalDateTime.now()));
    }
    @Test void onlyActiveAdministratorsReceiveAnIdempotentEventAndRoleChangesCancelDelivery() {
        var admin = account(UserRole.ADMIN, false); device(admin, "admin-token");
        device(account(UserRole.USER, false), "member-token");
        device(account(UserRole.ADMIN, true), "suspended-token");
        var alert = service.draftReceived(71L);
        assertThat(service.draftReceived(71L).getId()).isEqualTo(alert.getId());
        assertThat(alerts.count()).isEqualTo(1);
        assertThat(deliveries.count()).isEqualTo(1);
        admin.updateAdministration(UserRole.USER, false); users.flush();
        var delivery = deliveries.findAll().getFirst();
        assertThat(ledger.claim(delivery.getId(), Instant.now().plusSeconds(1))).isEmpty();
        assertThat(delivery.getStatus()).isEqualTo(AdminAlertDelivery.Status.CANCELLED);
    }
    @Test void adminPayloadHasNoPublicCardLinkAndRetriesCannotOverwriteANewerClaim() {
        device(account(UserRole.ADMIN, false), "admin-token");
        service.automation("workflow", "123", "FAILED", "서버 요청 변환");
        var row = deliveries.findAll().getFirst(); var now = Instant.now().plusSeconds(1);
        var first = ledger.claim(row.getId(), now).orElseThrow();
        assertThat(first.data()).containsKey("adminAlertId").doesNotContainKey("cardNewsId");
        assertThat(first.channelId()).isEqualTo("admin_operations");
        assertThat(ledger.claim(row.getId(), now.plusSeconds(1))).isEmpty();
        var second = ledger.claim(row.getId(), now.plusSeconds(301)).orElseThrow();
        ledger.complete(first, PushSender.Result.sent(), now.plusSeconds(302));
        assertThat(row.getStatus()).isEqualTo(AdminAlertDelivery.Status.PROCESSING);
        ledger.complete(second, PushSender.Result.sent(), now.plusSeconds(303));
        assertThat(row.getStatus()).isEqualTo(AdminAlertDelivery.Status.SENT);
    }
    @Test void operationsHistoryRequiresAnAdministrator() throws Exception {
        mvc.perform(get("/api/v1/admin/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/alerts").with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/alerts").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/internal/v1/automation/events").contentType("application/json")
            .content("{\"workflowId\":\"workflow\",\"executionId\":\"123\",\"status\":\"FAILED\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void providerRetryStopsAfterFiveAttempts() {
        var now = Instant.now(); var row = AdminAlertDelivery.pending(1L, 2L, 3L, now);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(row.claim(now)).isTrue();
            row.complete(attempt, new PushSender.Result(PushSender.Status.RETRY, "UNAVAILABLE"), now);
            now = now.plusSeconds(1000);
        }
        assertThat(row.getStatus()).isEqualTo(AdminAlertDelivery.Status.FAILED);
        assertThat(row.claim(now)).isFalse();
    }
}
