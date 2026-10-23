package org.aiknow.server.admin;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    @GetMapping("/summary") public AnalyticsService.Summary summary() { return service.summary(); }
    @GetMapping("/cards") public Page<AnalyticsService.CardStats> cards(@RequestParam(defaultValue="") @Size(max=200) String query,
        @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.cards(query,page,size);
    }
}
