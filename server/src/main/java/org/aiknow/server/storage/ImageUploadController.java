package org.aiknow.server.storage;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "ingestionAuth")
public class ImageUploadController {
    private final ImageStorage storage;
    private final org.aiknow.server.ingestion.GenerationCacheService generations;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @GetMapping("/internal/v1/images/storage")
    public java.util.Map<String, Object> readiness() {
        return storage.readiness();
    }

    @PostMapping(value = "/internal/v1/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageStorage.Receipt upload(@RequestPart("file") MultipartFile file,
        @RequestParam(required = false) Long generationRecordId) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (generationRecordId != null) generations.requireImageReservation(generationRecordId);
        var receipt = storage.store(file);
        if (generationRecordId != null) generations.complete(generationRecordId, mapper.writeValueAsString(receipt));
        return receipt;
    }
}
