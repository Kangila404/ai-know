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
}
