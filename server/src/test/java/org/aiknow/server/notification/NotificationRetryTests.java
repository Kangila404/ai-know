package org.aiknow.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.aiknow.server.notification.domain.DeviceToken;
import org.aiknow.server.notification.domain.DeviceType;
import org.aiknow.server.notification.dto.req.UpsertDeviceTokenRequest;
import org.aiknow.server.notification.dto.res.DeviceTokenResponse;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.notification.repository.NotificationSettingRepository;
import org.aiknow.server.notification.service.DeviceTokenRegistrationService;
import org.aiknow.server.notification.service.NotificationService;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationRetryTests {

    private final UserRepository users = mock(UserRepository.class);
    private final DeviceTokenRepository tokens = mock(DeviceTokenRepository.class);
    private final DeviceTokenRegistrationService registration = mock(DeviceTokenRegistrationService.class);
    private final NotificationService service = new NotificationService(
        users, mock(NotificationSettingRepository.class), tokens, registration
    );

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(users.findByUserId("user")).thenReturn(Optional.of(user));
    }

    @Test
    void retriesOnceWhenAnotherRequestInsertedTheToken() {
        var response = new DeviceTokenResponse(10L, "IOS", true, OffsetDateTime.now());
        when(registration.upsert(1L, "token", DeviceType.IOS))
            .thenThrow(new DataIntegrityViolationException("duplicate token"))
            .thenReturn(response);
        when(tokens.findByToken("token")).thenReturn(Optional.of(mock(DeviceToken.class)));

        assertThat(service.upsertDeviceToken("user", new UpsertDeviceTokenRequest("token", "ios")))
            .isEqualTo(response);
        verify(registration, times(2)).upsert(1L, "token", DeviceType.IOS);
    }

    @Test
    void doesNotHideUnrelatedIntegrityFailures() {
        var failure = new DataIntegrityViolationException("other constraint");
        when(registration.upsert(1L, "token", DeviceType.IOS)).thenThrow(failure);
        when(tokens.findByToken("token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertDeviceToken(
            "user", new UpsertDeviceTokenRequest("token", "IOS")
        )).isSameAs(failure);
        verify(registration).upsert(1L, "token", DeviceType.IOS);
    }
}
