package org.aiknow.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.concurrent.*;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.notification.batch.*;
import org.aiknow.server.notification.domain.*;
import org.aiknow.server.notification.repository.*;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest @ActiveProfiles("test")
class DailyNewsEditionTests {
    @Autowired DailyNewsEditionService selection;
    @Autowired DailyNewsEditionRepository editions;
    @Autowired NewsDeliveryWorker worker;
    @Autowired NewsDeliveryRepository deliveries;
    @Autowired CardNewsRepository news;
    @Autowired org.aiknow.server.cardNews.service.CardNewsService publicNews;
    @Autowired LikesRepository likes;
    @Autowired UserRepository users;
    @Autowired NotificationSettingRepository settings;
    @Autowired DeviceTokenRepository tokens;
    @MockitoBean Clock clock;
    @MockitoBean PushSender sender;
    private final LocalDate today = LocalDate.of(2026, 10, 7);
    private final ZoneId zone = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void clean() {
        deliveries.deleteAll(); editions.deleteAll(); tokens.deleteAll(); settings.deleteAll(); likes.deleteAll(); news.deleteAll();
        when(sender.send(any())).thenReturn(PushSender.Result.sent());
        when(clock.instant()).thenReturn(at(today, 0));
        when(clock.withZone(any())).thenAnswer(c -> Clock.fixed(clock.instant(), c.getArgument(0)));
    }

    private Instant at(LocalDate date, int hour) { return date.atTime(hour, 0).atZone(zone).toInstant(); }
    private CardNews article(ContentType type, Instant approved) {
        return news.save(CardNews.builder().title(type.name()).contentType(type)
            .inspectionStatus(InspectionStatus.APPROVED).approvedAt(approved).build());
    }
    private void userAt(int hour, String token) {
        var user = users.save(User.createSocialUser("구독자"));
        var setting = NotificationSetting.createDefault(user.getId());
        setting.updateAllowed(true); setting.updateTime(LocalTime.of(hour, 0)); settings.save(setting);
        tokens.save(DeviceToken.register(user.getId(), token, DeviceType.ANDROID, LocalDateTime.now()));
    }
    private void runAt(LocalDate date, int hour) {
        when(clock.instant()).thenReturn(at(date, hour)); worker.plan(); worker.dispatch();
    }

    @Test
    void latestApprovalBeforeMidnightWinsRegardlessOfIdAndDoesNotPublishAtSelection() {
        var latest = article(ContentType.NEWS, at(today.minusDays(1), 23));
        article(ContentType.NEWS, at(today.minusDays(2), 20));
        article(ContentType.NEWS, at(today, 0)); // Approval at exactly midnight belongs to tomorrow.
        article(ContentType.AI_THEORY, at(today.minusDays(1), 23).plusSeconds(1));
        var choice = selection.ensure(today, at(today, 0));
        assertThat(choice.getCardNewsId()).isEqualTo(latest.getId());
        assertThat(choice.getApprovalCutoff()).isEqualTo(at(today, 0));
        assertThat(choice.getStartedAt()).isNull();
        assertThat(news.findById(latest.getId()).orElseThrow().getPublicationStatus()).isEqualTo(PublicationStatus.READY);
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void eightAndTenOclockUsersGetSameArticleAndNineOclockApprovalWaitsUntilTomorrow() {
        var previous = article(ContentType.NEWS, at(today.minusDays(1), 18));
        userAt(8, "morning"); userAt(10, "later");
        runAt(today, 8);
        assertThat(deliveries.findAll()).hasSize(1).allMatch(d -> d.getCardNewsId().equals(previous.getId()));
        assertThat(news.findById(previous.getId()).orElseThrow().getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        var newer = article(ContentType.NEWS, at(today, 9));
        runAt(today, 10);
        assertThat(deliveries.findAll()).hasSize(2).allMatch(d -> d.getCardNewsId().equals(previous.getId()));
        verify(sender, times(2)).send(any());
        assertThat(editions.findById(today).orElseThrow().getStartedAt()).isEqualTo(at(today, 8));
        assertThat(selection.ensure(today.plusDays(1), at(today.plusDays(1), 0)).getCardNewsId()).isEqualTo(newer.getId());
    }

    @Test
    void restartAfterMidnightStillExcludesApprovalsFromThisMorning() {
        var previous = article(ContentType.NEWS, at(today.minusDays(1), 22));
        article(ContentType.NEWS, at(today, 9));
        var recovered = selection.ensure(today, at(today, 10));
        assertThat(recovered.getCardNewsId()).isEqualTo(previous.getId());
        assertThat(recovered.getApprovalCutoff()).isEqualTo(at(today, 0));
        assertThat(selection.ensure(today, at(today, 20)).getCardNewsId()).isEqualTo(previous.getId());
    }

    @Test
    void usesUnsentTheoryFirstThenReusesLeastRecentlySentWithoutDuplicatingPosts() {
        var older = article(ContentType.AI_THEORY, at(today.minusDays(10), 9));
        older.publishForDelivery(today.minusDays(8), at(today.minusDays(8), 9)); news.save(older);
        var recent = article(ContentType.AI_THEORY, at(today.minusDays(5), 9));
        recent.publishForDelivery(today.minusDays(2), at(today.minusDays(2), 9)); news.save(recent);
        var unused = article(ContentType.AI_THEORY, at(today.minusDays(1), 9));
        userAt(9, "theory-reader");
        runAt(today, 9);
        assertThat(editions.findById(today).orElseThrow().getCardNewsId()).isEqualTo(unused.getId());
        runAt(today.plusDays(1), 9);
        assertThat(editions.findById(today.plusDays(1)).orElseThrow().getCardNewsId()).isEqualTo(older.getId());
        var reused = news.findById(older.getId()).orElseThrow();
        assertThat(reused.getPublicationDate()).isEqualTo(today.minusDays(8));
        assertThat(reused.getLastUsedAt()).isEqualTo(at(today.plusDays(1), 9));
        assertThat(publicNews.getTodayCardNews().id()).isEqualTo(older.getId());
        assertThat(news.count()).isEqualTo(3);
        assertThat(selection.ensure(today.plusDays(2), at(today.plusDays(2), 0)).getCardNewsId()).isEqualTo(recent.getId());
    }

    @Test
    void usedNewsDoesNotReturnToTheQueueAndTheoryIsTheFallback() {
        var article = article(ContentType.NEWS, at(today.minusDays(1), 23));
        var theory = article(ContentType.AI_THEORY, at(today.minusDays(1), 22));
        userAt(9, "reader");
        runAt(today, 9);
        assertThat(editions.findById(today).orElseThrow().getCardNewsId()).isEqualTo(article.getId());
        assertThat(selection.ensure(today.plusDays(1), at(today.plusDays(1), 0)).getCardNewsId()).isEqualTo(theory.getId());
    }

    @Test
    void emptyDaysAreRecordedAndLateApprovalsStartTheNextDay() {
        var empty = selection.ensure(today, at(today, 0));
        assertThat(empty.getCardNewsId()).isNull();
        var late = article(ContentType.NEWS, at(today, 9));
        userAt(10, "reader"); runAt(today, 10);
        assertThat(editions.count()).isEqualTo(1);
        assertThat(deliveries.count()).isZero(); verifyNoInteractions(sender);
        assertThat(selection.ensure(today.plusDays(1), at(today.plusDays(1), 0)).getCardNewsId()).isEqualTo(late.getId());
    }

    @Test
    void unapprovedTheoryIsNeverUsedAndUnusedSelectionCanBeUsedOnAnotherDay() {
        var denied = news.save(CardNews.builder().title("반려된 이론").contentType(ContentType.AI_THEORY)
            .approvedAt(at(today.minusDays(1), 9)).inspectionStatus(InspectionStatus.DENIED).build());
        assertThat(selection.ensure(today, at(today, 0)).getCardNewsId()).isNull();
        var unused = article(ContentType.NEWS, at(today, 9));
        assertThat(selection.ensure(today.plusDays(1), at(today.plusDays(1), 0)).getCardNewsId()).isEqualTo(unused.getId());
        assertThat(selection.ensure(today.plusDays(2), at(today.plusDays(2), 0)).getCardNewsId()).isEqualTo(unused.getId());
        assertThat(news.findById(denied.getId()).orElseThrow().getPublicationStatus()).isEqualTo(PublicationStatus.READY);
    }

    @Test
    void simultaneousSelectorsPersistOnlyOneDailyChoice() throws Exception {
        var latest = article(ContentType.NEWS, at(today.minusDays(1), 23));
        article(ContentType.NEWS, at(today.minusDays(1), 22));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> task = () -> { start.await(); return selection.ensure(today, at(today, 0)).getCardNewsId(); };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(latest.getId());
            assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(latest.getId());
            assertThat(editions.count()).isEqualTo(1);
        }
    }
}
