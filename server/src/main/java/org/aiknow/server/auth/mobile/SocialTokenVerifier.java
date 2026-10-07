package org.aiknow.server.auth.mobile;

import java.util.*;
import org.aiknow.server.auth.domain.SocialProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service @EnableConfigurationProperties(SocialLoginProperties.class)
public class SocialTokenVerifier {
    private final Map<SocialProvider, JwtDecoder> decoders = new EnumMap<>(SocialProvider.class);

    @org.springframework.beans.factory.annotation.Autowired
    public SocialTokenVerifier(SocialLoginProperties properties) { this(properties, http()); }

    private static RestTemplate http() {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(5000); http.setReadTimeout(5000);
        return new RestTemplate(http);
    }

    SocialTokenVerifier(SocialLoginProperties properties, RestTemplate http) {
        for (var entry : Optional.ofNullable(properties.providers()).orElse(Map.of()).entrySet()) {
            var provider = SocialProvider.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            var settings = entry.getValue();
            if (!settings.enabled()) continue;
            var audiences = settings.audiences() == null ? List.<String>of()
                : settings.audiences().stream().filter(s -> s != null && !s.isBlank()).toList();
            if (audiences.isEmpty()) throw new IllegalArgumentException("Enabled social providers require allowed audiences: " + provider);
            String issuer = switch (provider) {
                case GOOGLE -> "https://accounts.google.com";
                case KAKAO -> "https://kauth.kakao.com";
                case APPLE -> "https://appleid.apple.com";
            };
            String jwks = switch (provider) {
                case GOOGLE -> "https://www.googleapis.com/oauth2/v3/certs";
                case KAKAO -> "https://kauth.kakao.com/.well-known/jwks.json";
                case APPLE -> "https://appleid.apple.com/auth/keys";
            };
            var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(http).build();
            OAuth2TokenValidator<Jwt> claims = jwt -> {
                boolean valid = jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                    && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                    && (jwt.getClaimAsString("azp") == null || audiences.contains(jwt.getClaimAsString("azp")));
                return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            };
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), claims));
            decoders.put(provider, decoder);
        }
    }

    public Set<SocialProvider> enabledProviders() { return Set.copyOf(decoders.keySet()); }
    public void requireEnabled(SocialProvider provider) {
        if (!decoders.containsKey(provider)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "해당 로그인 제공자가 설정되지 않았습니다.");
    }
    public Jwt verify(SocialProvider provider, String idToken) {
        requireEnabled(provider);
        try { return decoders.get(provider).decode(idToken); }
        catch (JwtException | IllegalArgumentException failure) {
            // Provider exceptions can contain the original token. Do not log or return them.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 인증 토큰 검증에 실패했습니다.");
        }
    }
}
