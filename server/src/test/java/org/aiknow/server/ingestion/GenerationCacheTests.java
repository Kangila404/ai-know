package org.aiknow.server.ingestion;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.ingestion.token=test-ingestion-secret-at-least-32-characters",
    "spring.datasource.url=jdbc:h2:mem:generation_test;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class GenerationCacheTests {
    private static final String AUTH = "Bearer test-ingestion-secret-at-least-32-characters";
    private static final String URL = "https://example.test/article";
    @Autowired GenerationCacheService service;
    @Autowired GenerationRecordRepository records;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @BeforeEach void clean() { records.deleteAll(); }

    @Test void savesRawInvalidModelResponsesAndReplaysWithoutAnotherReservation() {
        var claim = service.claim(URL, "draft", "{\"model\":\"test\"}");
        assertThat(claim.decision()).isEqualTo("CALL");
        assertThat(service.claim(URL, "draft", "{\"model\":\"test\"}").decision()).isEqualTo("BLOCKED");
        // Even malformed/refused model output is retained, before validation.
        String raw = "{\"body\":{\"output\":\"invalid draft\"},\"statusCode\":200}";
        service.complete(claim.id(), raw);
        var replay = service.claim(URL, "draft", "{\"model\":\"test\"}");
        assertThat(replay.decision()).isEqualTo("REPLAY");
        assertThat(replay.responseJson()).isEqualTo(raw);
        service.complete(claim.id(), raw);
        assertThatThrownBy(() -> service.complete(claim.id(), "{}"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.claim(URL, "draft", "{\"model\":\"changed\"}").decision()).isEqualTo("BLOCKED");
        assertThat(records.count()).isOne();
    }

    @Test void concurrentRequestsAllowOnlyOnePaidCall() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<String> claim = () -> { gate.await(); return service.claim(URL, "draft", "{}").decision(); };
            var a = pool.submit(claim); var b = pool.submit(claim); gate.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("CALL", "BLOCKED");
            assertThat(records.count()).isOne();
        }
    }

    @Test void oneDurableTextRetryIsSharedAcrossTasksAndManualExecutions() {
        var first = service.claim(URL,"draft","{}");
        assertThatThrownBy(() -> service.claim(URL,"text_retry","{}",null,first.id()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.complete(first.id(),"{\"invalid\":true}");
        var retry = service.claim(URL,"text_retry","{\"repair\":true}",null,first.id());
        assertThat(retry.decision()).isEqualTo("CALL");
        assertThat(service.claim(URL,"text_retry","{\"repair\":true}",null,first.id()).decision()).isEqualTo("BLOCKED");
        service.complete(retry.id(),"{\"corrected\":true}");
        assertThat(service.claim(URL,"text_retry","{\"repair\":true}",null,first.id()).decision()).isEqualTo("REPLAY");
        assertThat(service.claim(URL,"text_retry","{\"repair\":false}",null,first.id()).decision()).isEqualTo("BLOCKED");
        assertThatThrownBy(() -> service.claim(URL,"text_retry","{}",null,retry.id()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.claim(URL+"/other","text_retry","{}",null,first.id()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var selection = service.claim(URL,"image_selection","{}",CONTEXT);
        service.complete(selection.id(),"{}");
        assertThat(service.claim(URL,"text_retry","{\"repair\":true}",CONTEXT,selection.id()).decision()).isEqualTo("BLOCKED");
        assertThat(records.count()).isEqualTo(3);
    }

    @Test void retryApiRequiresParentAndConcurrentRetryReservesOnlyOneCall() throws Exception {
        var first = service.claim(URL,"draft","{}"); service.complete(first.id(),"{}");
        String missing=mapper.writeValueAsString(new GenerationCacheController.Request(URL,"text_retry","{}"));
        mvc.perform(post("/internal/v1/generation/claim").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON).content(missing))
            .andExpect(status().isBadRequest());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<String> claim=()->{gate.await();return service.claim(URL,"text_retry","{}",null,first.id()).decision();};
            var a=pool.submit(claim);var b=pool.submit(claim);gate.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("CALL","BLOCKED");
        }
        assertThat(records.count()).isEqualTo(2);
    }

    @Test void requiresMachineAuthAndValidJson() throws Exception {
        String body = mapper.writeValueAsString(new GenerationCacheController.Request(URL,"draft","{}"));
        mvc.perform(post("/internal/v1/generation/claim").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/generation/claim").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("CALL"));
        mvc.perform(post("/internal/v1/generation/existing").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceUrls\":[\""+URL+"\"]}")).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThatThrownBy(() -> service.claim(URL,"draft","not json"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static final String DRAFT = "{\"title\":\"Original\",\"slides\":[]}";
    private static final String CONTEXT = "{\"version\":1,\"draft\":" + DRAFT + ",\"assets\":[{\"key\":\"music\",\"candidates\":[{\"candidateId\":\"old\",\"url\":\"https://example.test/original.jpg\"}]}]}";

    @Test void completedSelectionRestoresOriginalRequestAndCandidatesBeforeNewSearch() {
        assertThat(service.selectionContext(URL,DRAFT).decision()).isEqualTo("NONE");
        var first = service.claim(URL,"image_selection","{\"input\":\"old candidates\"}",CONTEXT);
        assertThat(service.selectionContext(URL,DRAFT).decision()).isEqualTo("BLOCKED");
        service.complete(first.id(),"{\"selected\":\"old\"}");
        var restored = service.selectionContext(URL,DRAFT);
        assertThat(restored.decision()).isEqualTo("RESTORE");
        assertThat(restored.contextJson()).isEqualTo(CONTEXT);
        assertThat(service.claim(URL,"image_selection",restored.requestJson(),restored.contextJson()).contextJson()).isEqualTo(CONTEXT);
        assertThat(service.claim(URL,"image_selection",restored.requestJson(),restored.contextJson()).decision()).isEqualTo("REPLAY");
        assertThat(service.claim(URL,"image_selection","{\"input\":\"changed candidates\"}",CONTEXT).decision()).isEqualTo("BLOCKED");
        assertThat(service.selectionContext(URL,"{\"title\":\"different\"}").decision()).isEqualTo("BLOCKED");
        assertThat(records.count()).isOne();
    }

    @Test void legacyContextRecoveryRequiresExactOriginalRequestAndNeverOverwritesData() {
        var first = service.claim(URL,"image_selection","{}");
        service.complete(first.id(),"{\"selected\":\"old\"}");
        assertThat(service.selectionContext(URL,DRAFT).decision()).isEqualTo("BLOCKED");
        assertThatThrownBy(()->service.recoverContext(first.id(),"{\"changed\":true}",CONTEXT))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.recoverContext(first.id(),"{}",CONTEXT);
        service.recoverContext(first.id(),"{}",CONTEXT);
        assertThat(service.selectionContext(URL,DRAFT).decision()).isEqualTo("RESTORE");
        assertThatThrownBy(()->service.recoverContext(first.id(),"{}",CONTEXT.replace("Original","Changed")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(records.findById(first.id()).orElseThrow().getResponseJson()).isEqualTo("{\"selected\":\"old\"}");
        assertThat(records.count()).isOne();
    }

    @Test void recoveringContextDoesNotReleaseAnUncertainPaidCall() {
        var first = service.claim(URL,"image_selection","{}");
        service.recoverContext(first.id(),"{}",CONTEXT);
        assertThat(service.selectionContext(URL,DRAFT).decision()).isEqualTo("BLOCKED");
        assertThat(service.claim(URL,"image_selection","{}",CONTEXT).decision()).isEqualTo("BLOCKED");
        var draft = service.claim(URL,"draft","{}");
        assertThatThrownBy(()->service.recoverContext(draft.id(),"{}",CONTEXT))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void selectionEndpointsRequireAuthAndValidateSnapshot() throws Exception {
        String lookup=mapper.writeValueAsString(new GenerationCacheController.SelectionLookup(URL,DRAFT));
        mvc.perform(post("/internal/v1/generation/selection-context").contentType(MediaType.APPLICATION_JSON).content(lookup))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/generation/selection-context").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON).content(lookup))
            .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("NONE"));
        String bad=mapper.writeValueAsString(new GenerationCacheController.Request(URL,"image_selection","{}","{}"));
        mvc.perform(post("/internal/v1/generation/claim").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/v1/generation/selection-context/recover").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new GenerationCacheController.ContextRecovery(4L,"{}",CONTEXT))))
            .andExpect(status().isUnauthorized());
        var legacy=service.claim(URL,"image_selection","{}");
        service.complete(legacy.id(),"{\"original\":true}");
        mvc.perform(post("/internal/v1/generation/selection-context/recover").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new GenerationCacheController.ContextRecovery(legacy.id(),"{}",CONTEXT))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));
        mvc.perform(post("/internal/v1/generation/selection-context").header("Authorization",AUTH).contentType(MediaType.APPLICATION_JSON).content(lookup))
            .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("RESTORE"));
    }
}
