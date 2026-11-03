package org.aiknow.server.notification.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AdminAlertController {
    private final AdminAlertRepository alerts;
    private final AdminAlertService service;

    @GetMapping("/api/v1/admin/alerts")
    public Page<AdminAlert> list(@RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return alerts.findAll(PageRequest.of(page, size, Sort.by("id").descending()));
    }
    public record Event(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String workflowId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String executionId,
        @NotBlank @Pattern(regexp = "FAILED|NO_CONTENT") String status, @Size(max = 80) String stage) {}

    @PostMapping("/internal/v1/automation/events")
    public AdminAlert event(@Valid @RequestBody Event event) {
        try { return service.automation(event.workflowId(), event.executionId(), event.status(), event.stage()); }
        catch (DataIntegrityViolationException race) {
            return alerts.findByEventKey("automation:"+event.workflowId()+":"+event.executionId()).orElseThrow(() -> race);
        }
    }
}
