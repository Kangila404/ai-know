package org.aiknow.server.auth.mobile;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.social")
public record SocialLoginProperties(Map<String, Provider> providers) {
    public record Provider(boolean enabled, List<String> audiences) {}
}
