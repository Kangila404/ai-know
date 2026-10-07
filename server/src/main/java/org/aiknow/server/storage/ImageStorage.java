package org.aiknow.server.storage;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Uses an OS-mounted SMB/NFS dataset; contains no NAS-vendor API or credentials. */
@Service
public class ImageStorage {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final String FILE_PATTERN = "[a-f0-9]{64}\\.png";
    private final boolean enabled;
    private final Path root;
    private final String publicBaseUrl;
    private final String volumeId;
    private final boolean local;

    public ImageStorage(@Value("${app.media.enabled:false}") boolean enabled,
        @Value("${app.media.root:}") String root,
        @Value("${app.media.public-base-url:}") String publicBaseUrl,
        @Value("${app.media.volume-id:}") String volumeId,
        @Value("${app.media.mode:mounted}") String mode, Environment environment) {
        this.enabled = enabled;
        this.root = root.isBlank() ? null : Path.of(root).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.volumeId = volumeId;
        this.local = "local".equals(mode);
        if (!local && !"mounted".equals(mode)) throw new IllegalArgumentException("MEDIA_STORAGE_MODE must be local or mounted");
        if (local && (!java.util.Arrays.asList(environment.getActiveProfiles()).contains("local")
            || java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")))
            throw new IllegalArgumentException("Local media storage requires the local profile and cannot run with prod");
        if (enabled) {
            URI uri = URI.create(this.publicBaseUrl);
            if (this.root == null || (!local && (volumeId.isBlank() || volumeId.length() > 200 || !volumeId.equals(volumeId.strip())
                || volumeId.contains("\n") || volumeId.contains("\r")))
                || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Check MEDIA_ROOT, MEDIA_PUBLIC_BASE_URL and MEDIA_VOLUME_ID (mounted storage requires HTTPS and a volume ID)");
        }
    }

    public java.util.Map<String, Object> readiness() {
        verifyWritable();
        return java.util.Map.of("ready", true, "mode", local ? "local" : "mounted", "publicBaseUrl", publicBaseUrl);
    }

    /** HTTP is allowed only for generated files under this local server's configured media URL. */
    public boolean acceptsLocalGeneratedUrl(String url) {
        String prefix = publicBaseUrl + "/";
        return enabled && local && url != null && url.startsWith(prefix)
            && url.substring(prefix.length()).matches(FILE_PATTERN);
    }

    public Receipt store(MultipartFile file) {
        checkVolume();
        if (file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PNG must be between 1 byte and 10 MiB");
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read uploaded image", ex);
        }
        if (bytes.length > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PNG exceeds 10 MiB");
        validatePng(bytes);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        String name = hash + ".png";
        Path target = root.resolve(name);
        Path temporary = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) != bytes.length
                    || !MessageDigest.isEqual(bytes, Files.readAllBytes(target)))
                    throw new IOException("Existing image does not match its content hash");
            } else {
                temporary = Files.createTempFile(root, ".upload-", ".tmp");
                Files.write(temporary, bytes);
                checkVolume();
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) {
                    // Do not publish partially written images on an incompatible filesystem.
                    throw new IOException("The media share must support atomic rename", ex);
                }
            }
            return new Receipt(name, publicBaseUrl + "/" + name, "image/png", bytes.length);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage unavailable", ex);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Never turn a failed upload into success. */ }
            }
        }
    }

    public Resource read(String name) {
        if (!name.matches(FILE_PATTERN)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        checkVolume();
        Path file = root.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new FileSystemResource(file);
    }

    public void verifyWritable() {
        checkVolume();
        Path probe = null;
        try {
            probe = Files.createTempFile(root, ".probe-", ".tmp");
            Files.writeString(probe, "ready");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media share is not writable", ex);
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); } catch (IOException ignored) { }
            }
        }
    }

    private void checkVolume() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        try {
            if (local) {
                Files.createDirectories(root);
                return;
            }
            Path marker = root.resolve(".aiknow-storage-id");
            if (!Files.isDirectory(root) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) > 256 || !Files.readString(marker).strip().equals(volumeId))
                throw new IOException("Missing or mismatched media volume marker");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media share is not mounted or volume ID differs", ex);
        }
    }

    private static void validatePng(byte[] bytes) {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < 8 || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(bytes, 8)))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG image bytes are accepted");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid PNG");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096)
                    throw new IOException("Image dimensions must not exceed 4096 x 4096");
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IOException("Invalid image pixels");
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or oversized PNG image", ex);
        }
    }

    public record Receipt(String key, String url, String contentType, long size) {}
}
