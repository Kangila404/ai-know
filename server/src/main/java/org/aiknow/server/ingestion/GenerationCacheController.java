package org.aiknow.server.ingestion;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @SecurityRequirement(name = "ingestionAuth")
@RequestMapping("/internal/v1/generation")
public class GenerationCacheController {
    private final GenerationCacheService service;
    public record Lookup(@NotNull @Size(max = 100) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+") String> sourceUrls) {}
    public record Request(@NotBlank @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+") String sourceUrl,
        @NotNull @Pattern(regexp = "draft|image_selection|image_generation_[a-z][a-z0-9_]{0,39}") String task, @NotBlank @Size(max = 250000) String requestJson) {}
    public record Save(@NotNull @Positive Long id, @NotBlank @Size(max = 1000000) String responseJson) {}
    @PostMapping("/existing")
    public List<GenerationCacheService.Existing> existing(@Valid @RequestBody Lookup request) { return service.existing(request.sourceUrls()); }
    @PostMapping("/claim")
    public GenerationCacheService.Claim claim(@Valid @RequestBody Request request) {
        return service.claim(request.sourceUrl(), request.task(), request.requestJson());
    }
    @PostMapping("/response")
    public Map<String, Boolean> response(@Valid @RequestBody Save request) {
        service.complete(request.id(), request.responseJson()); return Map.of("saved", true);
    }
}
