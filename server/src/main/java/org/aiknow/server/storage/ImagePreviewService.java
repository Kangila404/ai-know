package org.aiknow.server.storage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Bounded local cache for immutable generated images; originals remain unchanged. */
@Service
public class ImagePreviewService {
    private static final int CACHE_BYTES = 32 * 1024 * 1024;
    private final ImageStorage storage;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(16, .75f, true);
    private int cachedBytes;

    public ImagePreviewService(ImageStorage storage) { this.storage = storage; }

    // Serial decoding bounds peak heap use even when many cold thumbnails arrive together.
    public synchronized byte[] read(String name, int size) {
        if (size != 320 && size != 1280) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported preview size");
        if (!name.matches("[a-f0-9]{64}\\.png")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String key = name + ":" + size;
        byte[] hit = cache.get(key);
        if (hit != null) return hit;
        try (var input = storage.read(name).getInputStream()) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) throw new IOException("Cannot decode image");
            double scale = Math.min(1d, (double) size / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = preview.createGraphics();
            try {
                graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally { graphics.dispose(); }
            var output = new ByteArrayOutputStream();
            var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (var imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                var options = writer.getDefaultWriteParam();
                options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); options.setCompressionQuality(.85f);
                writer.write(null, new IIOImage(preview, null, null), options);
            } finally { writer.dispose(); }
            byte[] data = output.toByteArray();
            while (!cache.isEmpty() && cachedBytes + data.length > CACHE_BYTES) {
                var iterator = cache.entrySet().iterator();
                cachedBytes -= iterator.next().getValue().length; iterator.remove();
            }
            if (data.length <= CACHE_BYTES) { cache.put(key, data); cachedBytes += data.length; }
            return data;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image preview unavailable", ex);
        }
    }
}
