package org.aiknow.server.editorial;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.time.*;
import java.util.*;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.CardNewsRepository;
import org.aiknow.server.ingestion.*;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:editorial;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class EditorialTests {
    @Autowired EditorialService editorial;
    @Autowired NewsSubmissionService reviews;
    @Autowired NewsSubmissionRepository submissions;
    @Autowired CardNewsRepository news;
    @Autowired org.aiknow.server.cardNews.repository.CardViewRepository views;
    @Autowired GenerationCacheService cache;
    @Autowired GenerationRecordRepository records;
    @Autowired GenerationRecoveryService recovery;
    @Autowired EditorialAuditRepository audits;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    private String actor, url;
    @BeforeEach void prepare() {
        actor = users.save(User.createSocialUser("편집자")).getUserId();
        url = "https://example.test/" + UUID.randomUUID();
    }
    private NewsImportRequest draft(String title) {
        return new NewsImportRequest("Source title", url, OffsetDateTime.parse("2026-10-06T00:00:00Z"), title,
            "근거에 맞는 요약", List.of("핵심"), null,
            List.of(new NewsImportRequest.Slide(1, "설명", "내용", "text", null)));
    }
    @Test void editorialChangesKeepOriginalIngestionIdempotentAndApprovalUsesEditedDraft() {
        var original = draft("원래 제목"); long id = reviews.receive(original).id();
        var before = reviews.get(id);
        assertThat(before.createdAt()).isNotNull();
        var receipt = reviews.receive(original);
        assertThat(receipt.title()).isEqualTo("원래 제목");
        assertThat(receipt.createdAt()).isEqualTo(before.createdAt());
        var edited = editorial.editSubmission(id, new EditorialService.Edit(before.version(), "사실 확인", draft("수정 제목")), actor);
        assertThat(edited.version()).isGreaterThan(before.version());
        assertThat(edited.draft().title()).isEqualTo("수정 제목");
        assertThat(edited.createdAt()).isEqualTo(before.createdAt());
        assertThat(reviews.list(InspectionStatus.PENDING, 0, 100).getContent())
            .anySatisfy(row -> {
                assertThat(row.id()).isEqualTo(id);
                assertThat(row.title()).isEqualTo("수정 제목");
                assertThat(row.createdAt()).isEqualTo(before.createdAt());
            });
        assertThat(reviews.receive(original).id()).isEqualTo(id);
        assertThatThrownBy(() -> editorial.editSubmission(id, new EditorialService.Edit(before.version(), "이전 화면", original), actor))
            .isInstanceOf(ResponseStatusException.class);
        long articleId = reviews.approve(id, new NewsSubmissionService.Approve(ContentType.NEWS, List.of()), actor).cardNewsId();
        assertThat(news.findById(articleId).orElseThrow().getTitle()).isEqualTo("수정 제목");
        assertThatThrownBy(() -> editorial.editSubmission(id, new EditorialService.Edit(edited.version(), "승인 후 변경", original), actor))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(audits.count()).isGreaterThanOrEqualTo(2);
    }
    @Test void rejectedDraftCanReopenButOriginCannotBeChanged() {
        long id = reviews.receive(draft("제목")).id();
        reviews.reject(id, new NewsSubmissionService.Reject("추가 확인"), actor);
        var denied = reviews.get(id);
        var pending = editorial.reopen(id, new EditorialService.Action(denied.version(), "검토 완료"), actor);
        assertThat(pending.status()).isEqualTo(InspectionStatus.PENDING);
        assertThat(pending.reviewNote()).isNull();
        url += "/changed";
        assertThatThrownBy(() -> editorial.editSubmission(id, new EditorialService.Edit(pending.version(), "출처 변조", draft("제목")), actor))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void hiddenArticleDisappearsFromPublicReadsAndRestorePreservesFirstUse() throws Exception {
        long id = reviews.receive(draft("게시물")).id();
        long articleId = reviews.approve(id, new NewsSubmissionService.Approve(ContentType.NEWS, List.of()), actor).cardNewsId();
        var ready = editorial.getArticle(articleId);
        assertThatThrownBy(() -> editorial.visibility(articleId, true, new EditorialService.Action(ready.version(), "조기 게시"), actor))
            .isInstanceOf(ResponseStatusException.class);
        var firstUse = Instant.parse("2026-10-07T00:00:00Z");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> news.findById(articleId).orElseThrow()
            .publishForDelivery(LocalDate.of(2026,10,7), firstUse));
        var published = editorial.getArticle(articleId);
        var hidden = editorial.visibility(articleId, false, new EditorialService.Action(published.version(), "내용 점검"), actor);
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId", "" + articleId).with(user(actor)))
            .andExpect(status().isNotFound());
        assertThatThrownBy(() -> news.findById(articleId).orElseThrow().publishForDelivery(LocalDate.now(), Instant.now()))
            .isInstanceOf(IllegalStateException.class);
        editorial.visibility(articleId, true, new EditorialService.Action(hidden.version(), "점검 완료"), actor);
        assertThat(news.findById(articleId).orElseThrow().getFirstUsedAt()).isEqualTo(firstUse);
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId", "" + articleId).with(user(actor)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/card-news").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/card-news/{id}/hide",articleId).with(user(actor).roles("ADMIN"))
            .contentType("application/json").content("{\"version\":0,\"reason\":\"test\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void adminCardsSeparatePublicationFromDeliveryAndIncludeViewsWithoutRecordingVisits() throws Exception {
        long theoryId = reviews.approve(reviews.receive(draft("아카이브 이론")).id(),
            new NewsSubmissionService.Approve(ContentType.AI_THEORY, List.of()), actor).cardNewsId();
        url += "/news";
        long newsId = reviews.approve(reviews.receive(draft("대기 뉴스")).id(),
            new NewsSubmissionService.Approve(ContentType.NEWS, List.of()), actor).cardNewsId();
        var theory = editorial.getArticle(theoryId);
        assertThat(theory.publicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(theory.approvedAt()).isNotNull();
        assertThat(theory.hasStartedDelivery()).isFalse();
        assertThat(theory.firstUsedAt()).isNull();
        assertThat(theory.totalViews()).isZero();
        var visit = new CardView(101L, theoryId, Instant.parse("2026-10-25T00:00:00Z"));
        visit.record(Instant.parse("2026-10-25T00:01:00Z"));
        views.save(visit);
        views.save(new CardView(102L, theoryId, Instant.now()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/admin/card-news/{id}", theoryId).with(user(actor).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalViews").value(3))
                .andExpect(jsonPath("$.uniqueViewers").value(2))
                .andExpect(jsonPath("$.hasStartedDelivery").value(false));
        }
        assertThat(editorial.listArticles(PublicationStatus.READY, 0, 100).getContent())
            .allSatisfy(row -> assertThat(row.publicationStatus()).isEqualTo(PublicationStatus.READY))
            .anySatisfy(row -> assertThat(row.news().id()).isEqualTo(newsId));
        assertThat(editorial.listArticles(PublicationStatus.PUBLISHED, 0, 100).getContent())
            .allSatisfy(row -> assertThat(row.publicationStatus()).isEqualTo(PublicationStatus.PUBLISHED))
            .anySatisfy(row -> {
                assertThat(row.news().id()).isEqualTo(theoryId);
                assertThat(row.totalViews()).isEqualTo(3);
                assertThat(row.uniqueViewers()).isEqualTo(2);
            });
        var firstUse = Instant.parse("2026-10-26T00:00:00Z");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> news.findById(newsId).orElseThrow()
            .publishForDelivery(LocalDate.of(2026, 10, 26), firstUse));
        var sent = editorial.getArticle(newsId);
        assertThat(sent.hasStartedDelivery()).isTrue();
        assertThat(sent.firstUsedAt()).isEqualTo(firstUse);
        assertThat(sent.lastUsedAt()).isEqualTo(firstUse);
        editorial.visibility(newsId, false, new EditorialService.Action(sent.version(), "검토"), actor);
        assertThat(editorial.listArticles(PublicationStatus.HIDDEN, 0, 100).getContent())
            .anySatisfy(row -> assertThat(row.news().id()).isEqualTo(newsId));
        assertThat(editorial.listArticles(PublicationStatus.PUBLISHED, 0, 100).getContent())
            .noneSatisfy(row -> assertThat(row.news().id()).isEqualTo(newsId));
    }
    @Test void responseRecoveryPreservesRawOutputAndNeverReleasesPaidReservation() {
        var claim = cache.claim(url, "draft", "{}"); cache.complete(claim.id(), "{\"bad\":true}");
        var record = records.findById(claim.id()).orElseThrow();
        var fixed = recovery.recover(claim.id(), record.getVersion(), "근거 대조", "{\"corrected\":true}", actor);
        assertThat(fixed.getResponseJson()).isEqualTo("{\"bad\":true}");
        assertThat(cache.claim(url, "draft", "{}").responseJson()).isEqualTo("{\"corrected\":true}");
        assertThat(cache.claim(url, "draft", "{\"changed\":true}").decision()).isEqualTo("BLOCKED");
        assertThatThrownBy(() -> recovery.recover(claim.id(), record.getVersion(), "이전 화면", "{}", actor))
            .isInstanceOf(ResponseStatusException.class);
        reviews.receive(draft("저장 완료"));
        assertThatThrownBy(() -> recovery.recover(claim.id(), fixed.getVersion(), "이미 저장됨", "{}", actor))
            .isInstanceOf(ResponseStatusException.class);
        var image = cache.claim(url + "image", "image_generation_topic", "{}");
        assertThatThrownBy(() -> recovery.recover(image.id(), 0, "외부 주소 주입", "{\"url\":\"http://bad.test\"}", actor))
            .isInstanceOf(ResponseStatusException.class);
    }
}
