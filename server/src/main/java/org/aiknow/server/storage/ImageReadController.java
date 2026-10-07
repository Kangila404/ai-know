package org.aiknow.server.storage;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ImageReadController {
    private final ImageStorage storage;

    @GetMapping("/media/generated/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .body(storage.read(name));
    }
}
