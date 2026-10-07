package org.aiknow.server.notice;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notices")
@Tag(name = "관리자 공지사항 API")
public class AdminNoticeController {
    private final NoticeService service;

    @GetMapping
    public Page<NoticeDtos.View> list(@RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(true, page, size);
    }

    @GetMapping("/{id}")
    public NoticeDtos.View get(@PathVariable Long id) { return service.get(id, true); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public NoticeDtos.View create(@Valid @RequestBody NoticeDtos.Write request) { return service.create(request); }

    @PutMapping("/{id}")
    public NoticeDtos.View update(@PathVariable Long id, @Valid @RequestBody NoticeDtos.Write request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
