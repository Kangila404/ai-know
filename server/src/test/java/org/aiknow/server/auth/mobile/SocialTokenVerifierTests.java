package org.aiknow.server.auth.mobile;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import org.aiknow.server.auth.domain.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class SocialTokenVerifierTests {
    private String token(RSAKey key, String issuer, String audience, Instant expiry) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("provider-subject").audience(audience)
            .issueTime(Date.from(Instant.now())).expirationTime(Date.from(expiry)).claim("nonce", "test-nonce").build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
    @Test void appleKakaoAndGoogleRequireSignatureIssuerAudienceAndExpiry() throws Exception {
        for (var provider : SocialProvider.values()) {
            String issuer = switch (provider) { case GOOGLE -> "https://accounts.google.com"; case KAKAO -> "https://kauth.kakao.com"; case APPLE -> "https://appleid.apple.com"; };
            String jwks = switch (provider) { case GOOGLE -> "https://www.googleapis.com/oauth2/v3/certs"; case KAKAO -> "https://kauth.kakao.com/.well-known/jwks.json"; case APPLE -> "https://appleid.apple.com/auth/keys"; };
            var key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            var other = new RSAKeyGenerator(2048).keyID("test-key").generate();
            var http = new RestTemplate(); var mock = MockRestServiceServer.bindTo(http).build();
            mock.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(), requestTo(jwks))
                .andRespond(withSuccess(new JWKSet(key.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
            var verifier = new SocialTokenVerifier(new SocialLoginProperties(Map.of(provider.name().toLowerCase(Locale.ROOT),
                new SocialLoginProperties.Provider(true, List.of("our-client")))), http);
            assertThat(verifier.verify(provider, token(key, issuer, "our-client", Instant.now().plusSeconds(300))).getSubject()).isEqualTo("provider-subject");
            for (String invalid : List.of(token(other, issuer, "our-client", Instant.now().plusSeconds(300)),
                token(key, "https://attacker.example", "our-client", Instant.now().plusSeconds(300)),
                token(key, issuer, "another-app", Instant.now().plusSeconds(300)), token(key, issuer, "our-client", Instant.now().minusSeconds(120)))) {
                assertThatThrownBy(() -> verifier.verify(provider, invalid)).isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("401");
            }
            mock.verify();
        }
    }
    @Test void unconfiguredProvidersAreDisabled() {
        var verifier = new SocialTokenVerifier(new SocialLoginProperties(Map.of()));
        assertThat(verifier.enabledProviders()).isEmpty();
        assertThatThrownBy(() -> verifier.verify(SocialProvider.APPLE, "not-a-token")).hasMessageContaining("503");
        assertThatThrownBy(() -> new SocialTokenVerifier(new SocialLoginProperties(Map.of("apple",new SocialLoginProperties.Provider(true,List.of())))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
