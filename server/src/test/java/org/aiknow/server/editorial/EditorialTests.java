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
        var edited = editorial.editSubmission(id, new EditorialService.Edit(before.version(), "사실 확인", draft("수정 제목")), actor);
        assertThat(edited.version()).isGreaterThan(before.version());
        assertThat(edited.draft().title()).isEqualTo("수정 제목");
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
