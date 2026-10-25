# Public image previews and profile avatars

Generated image originals remain available at `/media/generated/{sha256}.png` without query parameters. `GET`/`HEAD` with `?preview=320` or `?preview=1280` returns a JPEG with that maximum side length, preserved aspect ratio, and a white transparency background. Other nonzero sizes return 400. Originals are not rewritten. Preview responses are immutable and cacheable for one year; the process retains at most 32 MiB of encoded previews in LRU order. Cold transforms are serialized to bound decoding memory. Restarting the process clears this memory cache.

Card cover and slide response DTOs advertise `?preview=1280` for generated-image paths without existing query parameters/fragments. External image URLs remain unchanged. Existing clients can display this image normally. Updated iOS clients request 320px for list thumbnails, 1280px for reading, and remove the preview parameter when opening the original zoom image. The updated client does not request previews until the server advertises support, so either deployment order works.

Five original 512px profile PNGs are served at `/avatars/{lumi,mori,nova,orbi,pico}-v1.png`. V9 inserts missing choices by URL and preserves existing profile records/user selections. The canonical relative paths fit the current profile API URL length limit. The same images are bundled in iOS; static assets must be deployed for older apps and web clients. These five URLs have already been registered in the production profile catalog using the admin API; V9 is intentionally idempotent with those registrations.

Validation on 2026-10-23:

- ImageStorageTests: original upload/read, preview sizes/cache/content type, unsupported sizes, public avatar PNGs, existing upload validation.
- MobileContentApiTests and AdminProfileSecurityTests passed.
- MySQLMigrationTests: all six cases passed against local MySQL 8.4, including V9 after an existing avatar registration.
- A production 3,028,143-byte PNG took 3.21 seconds to download in the observed network (0.93 seconds to first byte). Transforming that file through the local preview controller produced 39,223 bytes at 320px and 373,748 bytes at 1280px; cold local transforms took about 0.33 seconds each. These are local size/transform measurements, not deployed server latency or end-to-end app benchmarks.

To reproduce the optional real-image check, set `AIKNOW_IMAGE_SAMPLE` to a local PNG path and run `bash ./gradlew test --tests '*ImageStorageTests'` from `server/`. Do not commit downloaded production images. Deployment and production preview latency remain to be verified after merge/release.
