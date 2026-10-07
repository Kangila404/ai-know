package org.aiknow.server.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.user.domain.*;
import org.aiknow.server.user.repository.UserRepository;
import org.aiknow.server.notification.batch.*;
import org.aiknow.server.notification.domain.*;
import org.aiknow.server.notification.repository.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.ingestion.token=test-ingestion-secret-at-least-32-characters")
@AutoConfigureMockMvc @ActiveProfiles("test")
class NewsIngestionTests {
    private static final String AUTH = "Bearer test-ingestion-secret-at-least-32-characters";
    private static final String IMPORT = "/internal/v1/card-news/import";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired CardNewsRepository news;
    @Autowired LikesRepository likes;
    @Autowired NewsSubmissionRepository submissions;
    @Autowired NewsSubmissionService service;
    @Autowired SessionAuthenticationService sessions;
    @Autowired DailyNewsEditionService editions;
    @Autowired DailyNewsEditionRepository editionRepository;
    @Autowired NewsDeliveryPlanner planner;
    @Autowired NewsDeliveryLedger ledger;
    @Autowired NewsDeliveryRepository deliveries;
    @Autowired DeviceTokenRepository tokens;
    @Autowired NotificationSettingRepository settings;
    @Autowired org.aiknow.server.editorial.EditorialService editorial;
    @MockitoBean Clock clock;
    private final Instant approvalTime = Instant.parse("2026-10-06T00:00:00Z");
    private final LocalDate deliveryDate = LocalDate.of(2026, 10, 7);
    private User admin;

    @BeforeEach
    void clean() {
        deliveries.deleteAll(); editionRepository.deleteAll(); tokens.deleteAll(); settings.deleteAll();
        submissions.deleteAll(); likes.deleteAll(); news.deleteAll();
        when(clock.instant()).thenReturn(approvalTime);
        when(clock.withZone(org.mockito.ArgumentMatchers.any())).thenAnswer(c -> Clock.fixed(clock.instant(), c.getArgument(0)));
        admin = User.createSocialUser("reviewer");
        ReflectionTestUtils.setField(admin, "userRole", UserRole.ADMIN); users.saveAndFlush(admin);
    }

    private MockHttpSession login(User user) {
        var req = new MockHttpServletRequest();
        try { sessions.login(user.getUserId(), req, new MockHttpServletResponse()); return (MockHttpSession) req.getSession(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private String csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session))
            .andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    private NewsImportRequest draft() {
        var image = new NewsImportRequest.Image("https://example.com/image.png", NewsImportRequest.ImageOrigin.SOURCE,
            "https://example.com/image-source", "Example 제공 / 사용 허가 확인");
        return new NewsImportRequest("Original title", "https://example.com/ai-news", OffsetDateTime.parse("2026-10-07T00:00:00Z"),
            "긴 한국어 제목도 저장할 수 있는 AI 카드뉴스 제목", "근거 기반 요약", List.of("핵심 내용"), image,
            List.of(new NewsImportRequest.Slide(1, "슬라이드 제목", "기사에 맞게 구성하는 본문", "timeline", image),
                new NewsImportRequest.Slide(2, "두 번째 제목", "추가 설명", "comparison", null)));
    }
    private long receive() throws Exception {
        var result = mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(draft()))).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private String approval() throws Exception {
        return mapper.writeValueAsString(new NewsSubmissionService.Approve(ContentType.NEWS, List.of()));
    }

    @Test
    void isolatesMachineCredentialsFromSessionsAndAdminPrivileges() throws Exception {
        var session = login(admin);
        var body = mapper.writeValueAsString(draft());
        mvc.perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(IMPORT).session(session).header("X-CSRF-TOKEN", csrf(session))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(IMPORT).header("Authorization", "Bearer wrong").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/news-submissions").header("Authorization", AUTH)).andExpect(status().isUnauthorized());
        long id = receive();
        mvc.perform(post("/api/v1/admin/news-submissions/{id}/approve", id).session(session)
            .contentType(MediaType.APPLICATION_JSON).content(approval())).andExpect(status().isForbidden());
        var reader = login(users.save(User.createSocialUser("reader")));
        mvc.perform(post("/api/v1/admin/news-submissions/{id}/approve", id).session(reader).header("X-CSRF-TOKEN", csrf(reader))
            .contentType(MediaType.APPLICATION_JSON).content(approval())).andExpect(status().isForbidden());
        assertThat(news.count()).isZero();
    }

    @Test
    void approvalStaysPrivateUntilFirstDeliveryThenPreservesSourcesAndDynamicSlides() throws Exception {
        long id = receive(); assertThat(news.count()).isZero();
        var session = login(admin); var csrf = csrf(session);
        mvc.perform(get("/api/v1/cardNews").session(session)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/cardNews/today").session(session)).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/admin/news-submissions/{id}/approve", id).session(session).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(approval()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        }
        assertThat(news.count()).isEqualTo(1);
        long newsId = news.findAll().getFirst().getId();
        assertThat(news.findById(newsId).orElseThrow().getPublicationStatus()).isEqualTo(PublicationStatus.READY);
        mvc.perform(get("/api/v1/cardNews").session(session)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId", Long.toString(newsId)).session(session))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/cardNews/{id}/like", newsId).session(session).header("X-CSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/news-submissions/{id}", id).session(session))
            .andExpect(jsonPath("$.publicationStatus").value("READY"));
        var sendTime = deliveryDate.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        when(clock.instant()).thenReturn(sendTime);
        editions.ensure(deliveryDate, sendTime);
        var setting = NotificationSetting.createDefault(admin.getId()); setting.updateAllowed(true); settings.save(setting);
        tokens.save(DeviceToken.register(admin.getId(), "review-test-device", DeviceType.IOS, LocalDateTime.now()));
        planner.plan(setting.getId(), deliveryDate, LocalTime.of(9, 0), sendTime);
        ledger.claim(deliveries.findAll().getFirst().getId(), sendTime).orElseThrow();
        mvc.perform(get("/api/v1/cardNews").session(session)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/cardNews/today").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceUrl").value(draft().sourceUrl()))
            .andExpect(jsonPath("$.titleImageCredit").value(draft().titleImage().credit()));
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId", Long.toString(newsId)).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].layout").value("timeline"))
            .andExpect(jsonPath("$[0].imageSourceUrl").value("https://example.com/image-source"));
        mvc.perform(get("/api/v1/admin/news-submissions/{id}", id).session(session))
            .andExpect(jsonPath("$.reviewedBy").value(admin.getId())).andExpect(jsonPath("$.reviewedAt").isNotEmpty())
            .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"));
        mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(draft()))).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/v1/admin/news-submissions/{id}/reject", id).session(session).header("X-CSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"이미 승인됨\"}")).andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidSlidesMissingAttributionAndConflictingSourceRetries() throws Exception {
        var body = mapper.writeValueAsString(draft());
        mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"sequence\":2", "\"sequence\":1"))).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace(draft().titleImage().credit(), ""))).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("https://example.com/ai-news", "file:///etc/passwd"))).andExpect(status().isBadRequest());
        receive();
        mvc.perform(post(IMPORT).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("근거 기반 요약", "바뀐 요약"))).andExpect(status().isConflict());
        assertThat(submissions.count()).isEqualTo(1);
    }

    @Test
    void rejectionIsRecordedAndCannotBePublished() throws Exception {
        long id = receive(); var session = login(admin);
        mvc.perform(post("/api/v1/admin/news-submissions/{id}/reject", id).session(session).header("X-CSRF-TOKEN", csrf(session))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"출처 확인 필요\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DENIED"));
        mvc.perform(get("/api/v1/admin/news-submissions/{id}", id).session(session))
            .andExpect(jsonPath("$.reviewNote").value("출처 확인 필요"));
        mvc.perform(post("/api/v1/admin/news-submissions/{id}/approve", id).session(session).header("X-CSRF-TOKEN", csrf(session))
            .contentType(MediaType.APPLICATION_JSON).content(approval())).andExpect(status().isConflict());
        assertThat(news.count()).isZero();
    }

    @Test
    void concurrentImportsAndApprovalsCreateOneArticle() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Long> importTask = () -> { gate.await(); return service.receive(draft()).id(); };
            var first = pool.submit(importTask); var second = pool.submit(importTask); gate.countDown();
            long id = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(submissions.count()).isEqualTo(1);
            var approvalGate = new CountDownLatch(1);
            Callable<Long> approveTask = () -> { approvalGate.await(); return service.approve(id,
                new NewsSubmissionService.Approve(ContentType.NEWS, List.of()), admin.getUserId()).cardNewsId(); };
            var a = pool.submit(approveTask); var b = pool.submit(approveTask); approvalGate.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
            assertThat(news.count()).isEqualTo(1);
        }
    }

    @Test
    void legacyUnapprovedNewsCannotBeReadOrLiked() throws Exception {
        var pending = news.save(CardNews.builder().title("비공개").publicationDate(LocalDate.now()).inspectionStatus(InspectionStatus.PENDING).build());
        var session = login(admin);
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId", pending.getId().toString()).session(session))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/cardNews/{id}/like", pending.getId()).session(session).header("X-CSRF-TOKEN", csrf(session))
            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cardNews/today").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void documentsBearerWithoutCsrfForIngestion() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/internal/v1/card-news/import'].post.security[0].ingestionAuth").exists())
            .andExpect(jsonPath("$.paths['/internal/v1/card-news/import'].post.parameters").doesNotExist());
    }

    @Test
    void hidingAfterFirstSendCancelsRemainingDeliveryAndRestoreDoesNotResendIt() {
        long articleId = service.approve(service.receive(draft()).id(),
            new NewsSubmissionService.Approve(ContentType.NEWS, List.of()), admin.getUserId()).cardNewsId();
        var sendTime = deliveryDate.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        when(clock.instant()).thenReturn(sendTime); editions.ensure(deliveryDate, sendTime);
        var setting = NotificationSetting.createDefault(admin.getId()); setting.updateAllowed(true); settings.save(setting);
        tokens.save(DeviceToken.register(admin.getId(), "hide-ios", DeviceType.IOS, LocalDateTime.now()));
        tokens.save(DeviceToken.register(admin.getId(), "hide-android", DeviceType.ANDROID, LocalDateTime.now()));
        planner.plan(setting.getId(), deliveryDate, LocalTime.of(9,0), sendTime);
        var pending = deliveries.findAll(); assertThat(pending).hasSize(2);
        assertThat(ledger.claim(pending.getFirst().getId(), sendTime)).isPresent();
        var current = editorial.getArticle(articleId);
        var hidden = editorial.visibility(articleId, false,
            new org.aiknow.server.editorial.EditorialService.Action(current.version(), "내용 점검"), admin.getUserId());
        assertThat(ledger.claim(pending.getLast().getId(), sendTime)).isEmpty();
        editorial.visibility(articleId, true,
            new org.aiknow.server.editorial.EditorialService.Action(hidden.version(), "점검 완료"), admin.getUserId());
        assertThat(ledger.claim(pending.getLast().getId(), sendTime.plusSeconds(60))).isEmpty();
        assertThat(news.findById(articleId).orElseThrow().getFirstUsedAt()).isEqualTo(sendTime);
    }
}
