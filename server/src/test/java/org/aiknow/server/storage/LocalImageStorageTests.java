package org.aiknow.server.storage;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.aiknow.server.ingestion.NewsImportRequest;
import org.aiknow.server.ingestion.NewsSubmissionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.ingestion.token=local-image-test-secret-at-least-32-characters",
    "app.media.enabled=true", "app.media.mode=local", "app.media.public-base-url=http://localhost:8080/media/generated",
    "spring.datasource.url=jdbc:h2:mem:local_media;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@AutoConfigureMockMvc @ActiveProfiles({"local", "test"})
class LocalImageStorageTests {
    private static final String AUTH = "Bearer local-image-test-secret-at-least-32-characters";
    private static final String BASE = "http://localhost:8080/media/generated";
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.media.root", () -> directory.resolve("generated").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired NewsSubmissionRepository submissions;
    @Autowired org.aiknow.server.ingestion.GenerationCacheService cache;

    private NewsImportRequest draft(String url, NewsImportRequest.ImageOrigin origin) {
        var image = new NewsImportRequest.Image(url, origin, "https://example.test/source", "test attribution");
        return new NewsImportRequest("storage fixture", "https://example.test/local-image-test", OffsetDateTime.now(),
            "로컬 저장 테스트", "실제 뉴스가 아닌 테스트입니다.", List.of(), image,
            List.of(new NewsImportRequest.Slide(1, "제목", "내용", "image", image)));
    }

    @Test void localUploadCreatesDirectoryWithoutNasAndPersistsHttpImageUrl() throws Exception {
        mvc.perform(get("/internal/v1/images/storage").header("Authorization", AUTH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("local"))
            .andExpect(jsonPath("$.publicBaseUrl").value(BASE));
        var bytes = ImageStorageTests.png();
        var result = mvc.perform(multipart("/internal/v1/images").file(ImageStorageTests.upload(bytes)).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var receipt = mapper.readTree(result);
        String key = receipt.get("key").asText(), url = receipt.get("url").asText();
        assertThat(url).isEqualTo(BASE + "/" + key);
        assertThat(directory.resolve("generated/.aiknow-storage-id")).doesNotExist();
        assertThat(Files.readAllBytes(directory.resolve("generated").resolve(key))).isEqualTo(bytes);
        mvc.perform(get("/media/generated/" + key)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        var saved = mvc.perform(post("/internal/v1/card-news/import").header("Authorization", AUTH)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft(url, NewsImportRequest.ImageOrigin.GENERATED))))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        assertThat(submissions.findById(mapper.readTree(saved).get("id").asLong()).orElseThrow().getPayload()).contains(url);
    }

    @Test void localHttpExceptionCannotBeUsedForExternalOrSourcedImages() throws Exception {
        for (String url : List.of("http://other-host/media/generated/" + "a".repeat(64) + ".png",
                BASE + "/../secret", BASE + "/" + "a".repeat(64) + ".png?redirect=true")) {
            mvc.perform(post("/internal/v1/card-news/import").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft(url, NewsImportRequest.ImageOrigin.GENERATED))))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/internal/v1/card-news/import").header("Authorization", AUTH)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft(BASE + "/" + "a".repeat(64) + ".png", NewsImportRequest.ImageOrigin.SOURCE))))
            .andExpect(status().isBadRequest());
    }

    @Test void productionCannotEnableLocalModeOrHttpMedia() {
        var prod = new MockEnvironment(); prod.setActiveProfiles("local", "prod");
        assertThatThrownBy(() -> new ImageStorage(true, directory.toString(), BASE, "", "local", prod))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageStorage(true, directory.toString(), BASE, "volume", "mounted", prod))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void imageReservationStoresReceiptAndReplaysWithoutAnotherPaidCall() throws Exception {
        String url = "https://example.test/image-cache-test";
        var claim = cache.claim(url, "image_generation_concept", "{\"prompt\":\"concept\"}");
        assertThat(claim.decision()).isEqualTo("CALL");
        String receipt = mvc.perform(multipart("/internal/v1/images").file(ImageStorageTests.upload(ImageStorageTests.png()))
            .param("generationRecordId", claim.id().toString()).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var replay = cache.claim(url, "image_generation_concept", "{\"prompt\":\"concept\"}");
        assertThat(replay.decision()).isEqualTo("REPLAY");
        assertThat(mapper.readTree(replay.responseJson())).isEqualTo(mapper.readTree(receipt));
        var text = cache.claim(url, "draft", "{}");
        mvc.perform(multipart("/internal/v1/images").file(ImageStorageTests.upload(ImageStorageTests.png()))
            .param("generationRecordId", text.id().toString()).header("Authorization", AUTH))
            .andExpect(status().isBadRequest());
    }
}
