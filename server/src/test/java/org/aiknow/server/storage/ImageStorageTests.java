package org.aiknow.server.storage;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.ingestion.token=test-ingestion-secret-at-least-32-characters", "app.media.enabled=true",
    "app.media.public-base-url=https://media.example.test/media/generated", "app.media.volume-id=test-volume"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ImageStorageTests {
    private static final String AUTH = "Bearer test-ingestion-secret-at-least-32-characters";
    @TempDir static Path root;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.media.root", () -> root.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ImageStorage storage;

    @BeforeEach void mountMarker() throws Exception { Files.writeString(root.resolve(".aiknow-storage-id"), "test-volume\n"); }
    static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    static MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "../../untrusted.png", "image/png", bytes); }

    @Test void machineTokenRequiredForUploadAndReadiness() throws Exception {
        mvc.perform(multipart("/internal/v1/images").file(upload(png()))).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/internal/v1/images").file(upload(png())).header("Authorization", "Bearer wrong"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/images/storage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/images/storage").header("Authorization", AUTH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
    }

    @Test void persistsBeforeReturningStableUrlAndServesExactPngWithoutLogin() throws Exception {
        byte[] bytes = png();
        String receipt = mvc.perform(multipart("/internal/v1/images").file(upload(bytes)).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String key = mapper.readTree(receipt).get("key").asText();
        assertThat(key).matches("[a-f0-9]{64}\\.png");
        assertThat(Files.readAllBytes(root.resolve(key))).isEqualTo(bytes);
        mvc.perform(multipart("/internal/v1/images").file(upload(bytes)).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andExpect(content().json(receipt));
        mvc.perform(get("/media/generated/" + key)).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes));
        mvc.perform(head("/media/generated/" + key)).andExpect(status().isOk());
        mvc.perform(get("/media/generated/.aiknow-storage-id")).andExpect(status().isNotFound());
        assertThatThrownBy(() -> storage.read("../secret.png")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void disconnectedOrWrongShareStopsUploadsInsteadOfWritingLocally() throws Exception {
        Files.delete(root.resolve(".aiknow-storage-id"));
        mvc.perform(multipart("/internal/v1/images").file(upload(png())).header("Authorization", AUTH))
            .andExpect(status().isServiceUnavailable());
        Files.writeString(root.resolve(".aiknow-storage-id"), "another-nas");
        mvc.perform(get("/internal/v1/images/storage").header("Authorization", AUTH))
            .andExpect(status().isServiceUnavailable());
    }

    @Test void rejectsDisguisedTruncatedAndOversizedUploads() throws Exception {
        mvc.perform(multipart("/internal/v1/images").file(upload("<svg/>".getBytes())).header("Authorization", AUTH))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/internal/v1/images").file(upload(Arrays.copyOf(png(), 20))).header("Authorization", AUTH))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/internal/v1/images").file(upload(new byte[10 * 1024 * 1024 + 1])).header("Authorization", AUTH))
            .andExpect(status().isPayloadTooLarge());
        var disabled = new ImageStorage(false, "", "", "", "mounted", new org.springframework.mock.env.MockEnvironment());
        assertThatThrownBy(disabled::verifyWritable).isInstanceOf(ResponseStatusException.class);
    }
}
