package org.aiknow.server.ingestion;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/internal/v1/card-news")
@SecurityRequirement(name = "ingestionAuth")
@Tag(name = "내부 뉴스 수집 API")
public class NewsImportController {
    private final NewsSubmissionService service;
    @PostMapping("/import") @ResponseStatus(HttpStatus.ACCEPTED)
    public NewsSubmissionService.Receipt receive(@Valid @RequestBody NewsImportRequest request) {
        return service.receive(request);
    }
}
