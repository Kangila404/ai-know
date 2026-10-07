package org.aiknow.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.*;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.notification.batch.*;
import org.aiknow.server.notification.domain.*;
import org.aiknow.server.notification.repository.*;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.BatchStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NewsDeliveryTests {
    @Autowired NewsDeliveryWorker worker;
    @Autowired NewsDeliveryLedger ledger;
    @Autowired NewsDeliveryRepository deliveries;
    @Autowired DailyNewsEditionRepository editions;
    @Autowired NotificationSettingRepository settings;
    @Autowired DeviceTokenRepository tokens;
    @Autowired CardNewsRepository news;
    @Autowired UserRepository users;
    @Autowired JobOperator operator;
    @Autowired Job dailyNewsJob;
    @MockitoBean PushSender sender;
    @MockitoBean Clock clock;
    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    private User user;
    private NotificationSetting setting;

    @BeforeEach
    void setUp() {
        deliveries.deleteAll(); editions.deleteAll(); tokens.deleteAll(); settings.deleteAll(); news.deleteAll(); users.deleteAll();
        user = users.save(User.createSocialUser("배치 사용자"));
        setting = NotificationSetting.createDefault(user.getId()); setting.updateAllowed(true);
        setting = settings.save(setting);
        tokens.save(DeviceToken.register(user.getId(), "android-token", DeviceType.ANDROID, LocalDateTime.now()));
        tokens.save(DeviceToken.register(user.getId(), "ios-token", DeviceType.IOS, LocalDateTime.now()));
        when(clock.instant()).thenReturn(now);
        when(sender.send(any())).thenReturn(PushSender.Result.sent());
    }

    private void article(InspectionStatus status) {
        news.save(CardNews.builder().title("어제 승인한 뉴스").approvedAt(now.minusSeconds(86400)).inspectionStatus(status).build());
    }

    @Test
    void jobSendsApprovedNewsToBothPlatformsOnlyOncePerDay() throws Exception {
        article(InspectionStatus.APPROVED);
        var execution = operator.start(dailyNewsJob, new JobParametersBuilder().addString("test", java.util.UUID.randomUUID().toString()).toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        worker.plan(); worker.dispatch();
        assertThat(deliveries.findAll()).hasSize(2).allMatch(d -> d.getStatus() == NewsDelivery.Status.SENT);
        verify(sender, times(2)).send(any());
    }

    @Test
    void skipsEarlyTimeAndCatchesUpThenHonorsOptOut() {
        article(InspectionStatus.PENDING);
        article(InspectionStatus.APPROVED);
        when(clock.instant()).thenReturn(now.minusSeconds(1)); worker.plan(); assertThat(deliveries.count()).isZero();
        when(clock.instant()).thenReturn(now.plusSeconds(3600)); worker.plan(); assertThat(deliveries.count()).isEqualTo(2);
        setting.updateAllowed(false); settings.save(setting); worker.dispatch();
        verifyNoInteractions(sender);
        assertThat(news.findAll()).noneMatch(n -> n.getPublicationStatus() == PublicationStatus.PUBLISHED);
    }

    @Test
    void retriesOnlyFailedDeviceAndInvalidatesUnregisteredToken() {
        article(InspectionStatus.APPROVED);
        when(sender.send(any())).thenAnswer(call -> {
            var message = (PushSender.Message) call.getArgument(0);
            return message.platform() == DeviceType.IOS ? PushSender.Result.sent()
                : new PushSender.Result(PushSender.Status.RETRY, "UNAVAILABLE");
        });
        worker.plan(); worker.dispatch(); worker.dispatch(); verify(sender, times(2)).send(any());
        when(clock.instant()).thenReturn(now.plusSeconds(61));
        doReturn(new PushSender.Result(PushSender.Status.INVALID_TOKEN, "UNREGISTERED")).when(sender).send(any());
        worker.dispatch(); verify(sender, times(3)).send(any());
        assertThat(tokens.findByToken("android-token").orElseThrow().isActive()).isFalse();
        assertThat(tokens.findByToken("ios-token").orElseThrow().isActive()).isTrue();
    }

    @Test
    void leasePreventsDuplicateClaimAndRotatedTokenSurvivesStaleFailure() {
        article(InspectionStatus.APPROVED); worker.plan();
        Long id = deliveries.findAll().getFirst().getId();
        var message = ledger.claim(id, now).orElseThrow();
        assertThat(ledger.claim(id, now.plusSeconds(1))).isEmpty();
        var token = tokens.findByToken(message.token()).orElseThrow();
        token.updateRegistration(user.getId(), "rotated-token", token.getPlatform(), null, LocalDateTime.now()); tokens.save(token);
        ledger.complete(message, new PushSender.Result(PushSender.Status.INVALID_TOKEN, "UNREGISTERED"), now);
        assertThat(tokens.findByToken("rotated-token").orElseThrow().isActive()).isTrue();
    }

    @Test
    void recoversExpiredClaimButDoesNotSendToNewDeviceOwner() {
        article(InspectionStatus.APPROVED); worker.plan();
        Long id = deliveries.findAll().getFirst().getId();
        var message = ledger.claim(id, now).orElseThrow();
        assertThat(ledger.claim(id, now.plusSeconds(301))).isPresent();
        var token = tokens.findByToken(message.token()).orElseThrow();
        var other = users.save(User.createSocialUser("새 사용자"));
        token.reactivate(other.getId(), token.getPlatform(), LocalDateTime.now()); tokens.save(token);
        assertThat(ledger.claim(id, now.plusSeconds(602))).isEmpty();
        assertThat(deliveries.findById(id).orElseThrow().getStatus()).isEqualTo(NewsDelivery.Status.CANCELLED);
    }
}
