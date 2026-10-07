package org.aiknow.server.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/generations")
public class AdminGenerationController {
    private final GenerationRecordRepository records;
    private final GenerationRecoveryService recovery;
    public record Recover(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String reason,
        @NotBlank @Size(max = 1000000) String responseJson) {}
    public record Summary(Long id, long version, String sourceUrl, String task, java.time.Instant createdAt, boolean responseSaved, boolean recovered) {
        static Summary of(GenerationRecord r) { return new Summary(r.getId(), r.getVersion(), r.getSourceUrl(), r.getTask(), r.getCreatedAt(), r.getResponseJson() != null, r.getRecoveredResponseJson() != null); }
    }
    @GetMapping
    public Page<Summary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return records.findAll(PageRequest.of(page, size, Sort.by("id").descending())).map(Summary::of);
    }
    @GetMapping("/{id}")
    public ResponseEntity<GenerationRecord> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
    @PostMapping("/{id}/recover")
    public Summary recover(@PathVariable Long id, @Valid @RequestBody Recover body, @AuthenticationPrincipal String actor) {
        return Summary.of(recovery.recover(id, body.version(), body.reason(), body.responseJson(), actor));
    }
    @PostMapping(value = "/{id}/recover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Summary recoverImage(@PathVariable Long id, @RequestParam @PositiveOrZero long version,
        @RequestParam @NotBlank @Size(max = 2000) String reason, @RequestPart("file") MultipartFile file,
        @AuthenticationPrincipal String actor) {
        return Summary.of(recovery.recoverImage(id, version, reason, file, actor));
    }
}
