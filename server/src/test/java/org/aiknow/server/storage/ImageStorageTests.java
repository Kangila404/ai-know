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

    @Test void previewsAreSmallerCacheableAndLeaveOriginalUntouched() throws Exception {
        var original = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_ARGB);
        var random = new java.util.Random(42);
        for (int y = 0; y < 1200; y++) for (int x = 0; x < 1600; x++) original.setRGB(x, y, random.nextInt() | 0xff000000);
        original.setRGB(0, 0, 0); // transparent corner is composited onto white
        var out = new ByteArrayOutputStream(); ImageIO.write(original, "png", out);
        byte[] bytes = out.toByteArray();
        String result = mvc.perform(multipart("/internal/v1/images").file(upload(bytes)).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String key = mapper.readTree(result).get("key").asText();
        String path = "/media/generated/" + key;
        byte[] small = mvc.perform(get(path).param("preview", "320"))
            .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
            .andReturn().getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(small));
        assertThat(decoded.getWidth()).isEqualTo(320); assertThat(decoded.getHeight()).isEqualTo(240);
        assertThat(small.length).isLessThan(bytes.length / 5);
        mvc.perform(get(path).param("preview", "320")).andExpect(content().bytes(small));
        mvc.perform(get(path).param("preview", "1280")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get(path).param("preview", "9999")).andExpect(status().isBadRequest());
        mvc.perform(get(path)).andExpect(content().bytes(bytes));
        assertThat(MediaUrls.preview("https://aiknow.kro.kr" + path)).endsWith("?preview=1280");
        assertThat(MediaUrls.preview("https://example.com/logo.png?signature=abc")).isEqualTo("https://example.com/logo.png?signature=abc");
    }

    @Test void bundledAvatarsArePublicAndReadable() throws Exception {
        for (String key : java.util.List.of("lumi", "mori", "nova", "orbi", "pico")) {
            byte[] data = mvc.perform(get("/avatars/" + key + "-v1.png"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
            assertThat(ImageIO.read(new java.io.ByteArrayInputStream(data)).getWidth()).isEqualTo(512);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AIKNOW_IMAGE_SAMPLE", matches = ".+")
    void measureRequestedImageSample() throws Exception {
        byte[] original = Files.readAllBytes(Path.of(System.getenv("AIKNOW_IMAGE_SAMPLE")));
        String result = mvc.perform(multipart("/internal/v1/images").file(upload(original)).header("Authorization", AUTH))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String key = mapper.readTree(result).get("key").asText();
        for (int size : new int[]{320, 1280}) {
            long start = System.nanoTime();
            byte[] data = mvc.perform(get("/media/generated/" + key).param("preview", String.valueOf(size)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
            System.out.printf("IMAGE_SAMPLE original=%d preview=%d bytes=%d local_transform_ms=%.1f%n", original.length, size, data.length, (System.nanoTime() - start) / 1_000_000d);
            assertThat(data.length).isLessThan(original.length);
        }
    }
}
