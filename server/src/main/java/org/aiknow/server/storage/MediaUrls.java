package org.aiknow.server.storage;

import java.net.URI;

public final class MediaUrls {
    private MediaUrls() { }
    public static String preview(String raw) {
        if (raw == null) return null;
        try {
            URI uri = URI.create(raw);
            if (uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getPath() != null
                && uri.getPath().matches("/media/generated/[a-f0-9]{64}\\.png")) return raw + "?preview=1280";
        } catch (IllegalArgumentException ignored) { }
        return raw;
    }
}
