package org.aiknow.server.notice;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notices")
@Tag(name = "공지사항 API")
public class NoticeController {
    private final NoticeService service;

    @GetMapping
    public Page<NoticeDtos.View> list(@RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(false, page, size);
    }

    @GetMapping("/{id}")
    public NoticeDtos.View get(@PathVariable Long id) { return service.get(id, false); }
}
