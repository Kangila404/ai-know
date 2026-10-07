package org.aiknow.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aiknow.server.notification.dto.req.UpsertDeviceTokenRequest;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.notification.repository.NotificationSettingRepository;
import org.aiknow.server.notification.service.NotificationService;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DeviceTokenRepository tokens;
    @Autowired NotificationSettingRepository settings;
    @Autowired NotificationService service;

    private User user;

    @BeforeEach
    void setUp() {
        tokens.deleteAll();
        settings.deleteAll();
        users.deleteAll();
        user = users.save(User.createSocialUser("알림 테스트"));
    }

    private RequestPostProcessor signedIn(User signedInUser) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            signedInUser.getUserId(), null, List.of()
        ));
    }

    @Test
    void rotatesTokensPerInstallationAndTransfersAccountOwnership() throws Exception {
        String installation = "19c9f4f2-9ee4-4389-bb69-038853593c73";
        service.upsertDeviceToken(user.getUserId(), new UpsertDeviceTokenRequest("old", "IOS", installation));
        Long id = tokens.findByToken("old").orElseThrow().getId();
        User anotherUser = users.save(User.createSocialUser("다른 계정"));
        service.upsertDeviceToken(anotherUser.getUserId(), new UpsertDeviceTokenRequest("new", "IOS", installation));
        assertThat(tokens.findByToken("old")).isEmpty();
        assertThat(tokens.findByToken("new").orElseThrow().getId()).isEqualTo(id);
        assertThat(tokens.findByToken("new").orElseThrow().getUserId()).isEqualTo(anotherUser.getId());
        service.upsertDeviceToken(anotherUser.getUserId(), new UpsertDeviceTokenRequest(
            "android", "ANDROID", "7a774af0-3736-47d6-800f-3b08566d3a54"));
        assertThat(tokens.findByUserIdAndActiveTrue(anotherUser.getId())).hasSize(2);
        mvc.perform(put("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"test\",\"platform\":\"IOS\",\"installationId\":\"invalid\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentInstallationRotationLeavesOneActiveRegistration() throws Exception {
        String installation = "ebc60b17-daf8-4c34-af76-8b5743104519";
        runConcurrently(List.of(
            () -> service.upsertDeviceToken(user.getUserId(), new UpsertDeviceTokenRequest("token-a", "ANDROID", installation)),
            () -> service.upsertDeviceToken(user.getUserId(), new UpsertDeviceTokenRequest("token-b", "ANDROID", installation))
        ));
        assertThat(tokens.findByUserIdAndActiveTrue(user.getId())).hasSize(1);
    }

    @Test
    void createsDefaultSettingAndPersistsPartialUpdates() throws Exception {
        mvc.perform(get("/api/v1/notification-setting").with(signedIn(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settingTime").value("09:00:00"))
            .andExpect(jsonPath("$.isAllowed").value(false));
        assertThat(settings.count()).isEqualTo(1);

        mvc.perform(put("/api/v1/notification-setting").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settingTime\":\"21:30:00\",\"isAllowed\":true}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/notification-setting").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"isAllowed\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notification-setting").with(signedIn(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settingTime").value("21:30:00"))
            .andExpect(jsonPath("$.isAllowed").value(false));
    }

    @Test
    void registersDeactivatesAndReactivatesTheSameToken() throws Exception {
        String body = "{\"token\":\" device-001 \",\"platform\":\" android \"}";
        mvc.perform(put("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platform").value("ANDROID"))
            .andExpect(jsonPath("$.active").value(true));
        Long id = tokens.findByToken("device-001").orElseThrow().getId();

        User anotherUser = users.save(User.createSocialUser("다른 사용자"));
        mvc.perform(delete("/api/v1/device-tokens").with(signedIn(anotherUser)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"device-001\"}"))
            .andExpect(status().isNoContent());
        assertThat(tokens.findById(id).orElseThrow().isActive()).isTrue();

        mvc.perform(delete("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"device-001\"}"))
            .andExpect(status().isNoContent());
        assertThat(tokens.findById(id).orElseThrow().isActive()).isFalse();

        mvc.perform(put("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceTokenId").value(id.intValue()))
            .andExpect(jsonPath("$.active").value(true));
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void returnsDomainErrorsForMissingBodiesAndInvalidFields() throws Exception {
        mvc.perform(delete("/api/v1/device-tokens").with(signedIn(user)).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));
        mvc.perform(put("/api/v1/notification-setting").with(signedIn(user)).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_SETTING"));
        mvc.perform(put("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"platform\":\"WEB\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PLATFORM"));
        for (String token : List.of("   ", "a".repeat(513))) {
            mvc.perform(put("/api/v1/device-tokens").with(signedIn(user)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));
        }
    }

    @Test
    void rejectsWritesWithoutCsrfAsJson() throws Exception {
        mvc.perform(put("/api/v1/device-tokens").with(signedIn(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"device-001\",\"platform\":\"IOS\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(tokens.count()).isZero();
    }

    @Test
    void allowsPutPreflightFromFrontend() throws Exception {
        mvc.perform(options("/api/v1/device-tokens")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void documentsCsrfHeaderForNotificationWrites() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/notification-setting'].put.parameters[0].name")
                .value("X-CSRF-TOKEN"))
            .andExpect(jsonPath("$.paths['/api/v1/device-tokens'].put.parameters[0].name")
                .value("X-CSRF-TOKEN"))
            .andExpect(jsonPath("$.paths['/api/v1/device-tokens'].delete.parameters[0].name")
                .value("X-CSRF-TOKEN"));
    }

    @Test
    void concurrentFirstSettingRequestsCreateOnlyOneRow() throws Exception {
        runConcurrently(List.of(
            () -> service.getSetting(user.getUserId()),
            () -> service.getSetting(user.getUserId()),
            () -> service.getSetting(user.getUserId()),
            () -> service.getSetting(user.getUserId())
        ));
        assertThat(settings.count()).isEqualTo(1);
    }

    @Test
    void concurrentTokenRegistrationsAcrossUsersCreateOnlyOneRow() throws Exception {
        User anotherUser = users.save(User.createSocialUser("다른 사용자"));
        UpsertDeviceTokenRequest request = new UpsertDeviceTokenRequest("shared-device", "IOS");
        runConcurrently(List.of(
            () -> service.upsertDeviceToken(user.getUserId(), request),
            () -> service.upsertDeviceToken(anotherUser.getUserId(), request),
            () -> service.upsertDeviceToken(user.getUserId(), request),
            () -> service.upsertDeviceToken(anotherUser.getUserId(), request)
        ));
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(tokens.findByToken("shared-device").orElseThrow().isActive()).isTrue();
    }

    private void runConcurrently(List<Callable<?>> tasks) throws Exception {
        var executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (Callable<?> task : tasks) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test start timed out");
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
