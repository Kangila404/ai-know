package org.aiknow.server.editorial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.PublicationStatus;
import org.aiknow.server.ingestion.NewsSubmissionService;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class EditorialController {
    private final EditorialService editorial;
    private final EditorialAuditRepository audits;
    @PutMapping("/news-submissions/{id}")
    public NewsSubmissionService.Review edit(@PathVariable Long id, @Valid @RequestBody EditorialService.Edit body,
        @AuthenticationPrincipal String actor) { return editorial.editSubmission(id, body, actor); }
    @PostMapping("/news-submissions/{id}/reopen")
    public NewsSubmissionService.Review reopen(@PathVariable Long id, @Valid @RequestBody EditorialService.Action body,
        @AuthenticationPrincipal String actor) { return editorial.reopen(id, body, actor); }
    @GetMapping("/card-news")
    public Page<EditorialService.Article> list(@RequestParam(required = false) PublicationStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return editorial.listArticles(status, page, size);
    }
    @GetMapping("/card-news/{id}")
    public EditorialService.Article get(@PathVariable Long id) { return editorial.getArticle(id); }
    @PutMapping("/card-news/{id}")
    public EditorialService.Article editArticle(@PathVariable Long id, @Valid @RequestBody EditorialService.Edit body,
        @AuthenticationPrincipal String actor) { return editorial.editArticle(id, body, actor); }
    @PostMapping("/card-news/{id}/hide")
    public EditorialService.Article hide(@PathVariable Long id, @Valid @RequestBody EditorialService.Action body,
        @AuthenticationPrincipal String actor) { return editorial.visibility(id, false, body, actor); }
    @PostMapping("/card-news/{id}/restore")
    public EditorialService.Article restore(@PathVariable Long id, @Valid @RequestBody EditorialService.Action body,
        @AuthenticationPrincipal String actor) { return editorial.visibility(id, true, body, actor); }
    @GetMapping("/editorial/history")
    public Page<EditorialAudit> history(@RequestParam @Pattern(regexp = "SUBMISSION|ARTICLE|GENERATION") String resourceType,
        @RequestParam @Positive Long resourceId, @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audits.findByResourceTypeAndResourceIdOrderByIdDesc(resourceType, resourceId, PageRequest.of(page, size));
    }
}
