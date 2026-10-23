package org.aiknow.server.admin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService service;
    @GetMapping public Page<UserAdministrationService.Account> list(
        @RequestParam(defaultValue="") @Size(max=200) String query, @RequestParam(required=false) UserRole role,
        @RequestParam(required=false) Boolean suspended, @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(query, role, suspended, page, size);
    }
    @GetMapping("/{id}") public UserAdministrationService.Account get(@PathVariable @Positive Long id) { return service.get(id); }
    @PatchMapping("/{id}") public UserAdministrationService.Account update(@PathVariable @Positive Long id,
        @Valid @RequestBody UserAdministrationService.Update body, @AuthenticationPrincipal String actor) {
        return service.update(id, body, actor);
    }
}
