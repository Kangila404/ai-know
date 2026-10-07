package org.aiknow.server.ingestion;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/news-submissions")
@Tag(name = "관리자 뉴스 검수 API")
public class AdminNewsSubmissionController {
    private final NewsSubmissionService service;
    @GetMapping
    public Page<NewsSubmissionService.Receipt> list(@RequestParam(defaultValue = "PENDING") InspectionStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.list(status, page, size); }
    @GetMapping("/{id}")
    public NewsSubmissionService.Review get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/{id}/approve")
    public NewsSubmissionService.Receipt approve(@PathVariable Long id, @Valid @RequestBody NewsSubmissionService.Approve request,
        @AuthenticationPrincipal String userId) { return service.approve(id, request, userId); }
    @PostMapping("/{id}/reject")
    public NewsSubmissionService.Receipt reject(@PathVariable Long id, @Valid @RequestBody NewsSubmissionService.Reject request,
        @AuthenticationPrincipal String userId) { return service.reject(id, request, userId); }
}
