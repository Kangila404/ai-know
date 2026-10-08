package org.aiknow.server.auth.mobile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.aiknow.server.auth.domain.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mobile_login;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class MobileSocialLoginTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean SocialTokenVerifier verifier;

    private String csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn()
            .getResponse().getContentAsString()).path("token").asText();
    }
    private String challenge(MockHttpSession session, String csrf) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/social/APPLE/challenge").session(session)
            .header("X-CSRF-TOKEN", csrf)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .path("nonce").asText();
    }
    private Jwt token(String nonce) {
        return Jwt.withTokenValue("verified-token").header("alg", "RS256").subject("apple-test-subject")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("nonce", nonce).build();
    }
    @Test void loginRequiresCsrfRotatesSessionAndCsrfAndConsumesNonce() throws Exception {
        var session = new MockHttpSession(); String oldId = session.getId(), oldCsrf = csrf(session);
        mvc.perform(post("/api/v1/auth/social/APPLE/challenge").session(session)).andExpect(status().isForbidden());
        String nonce = challenge(session, oldCsrf);
        when(verifier.verify(SocialProvider.APPLE, "id-token")).thenReturn(token(nonce));
        var result = mvc.perform(post("/api/v1/auth/social/APPLE").session(session).header("X-CSRF-TOKEN", oldCsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"id-token\",\"nickname\":\"테스트\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").isNotEmpty()).andReturn();
        assertThat(session.getId()).isNotEqualTo(oldId);
        String freshCsrf = mapper.readTree(result.getResponse().getContentAsString()).path("csrfToken").asText();
        mvc.perform(get("/api/v1/users").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/social/APPLE/challenge").session(session).header("X-CSRF-TOKEN", oldCsrf))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/social/APPLE").session(session).header("X-CSRF-TOKEN", freshCsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"id-token\"}"))
            .andExpect(status().isUnauthorized());
        verify(verifier, times(1)).verify(any(), anyString());
        mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN", freshCsrf))
            .andExpect(status().isNoContent());
    }
    @Test void wrongNonceDoesNotAuthenticateAndCannotBeRetried() throws Exception {
        var session = new MockHttpSession(); String csrf = csrf(session); challenge(session, csrf);
        when(verifier.verify(SocialProvider.APPLE, "id-token")).thenReturn(token("wrong-nonce"));
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/auth/social/APPLE").session(session).header("X-CSRF-TOKEN", csrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"id-token\"}"))
            .andExpect(status().isUnauthorized());
        verify(verifier, times(1)).verify(any(), anyString());
        mvc.perform(get("/api/v1/users").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void googleLoginMaintainsSessionForProfileAndPushRegistration() throws Exception {
        var session = new MockHttpSession();
        String oldSessionId = session.getId(), anonymousCsrf = csrf(session);
        var challengeResult = mvc.perform(post("/api/v1/auth/social/GOOGLE/challenge").session(session)
            .header("X-CSRF-TOKEN", anonymousCsrf))
            .andExpect(status().isOk()).andReturn();
        String nonce = mapper.readTree(challengeResult.getResponse().getContentAsString()).path("nonce").asText();
        when(verifier.verify(SocialProvider.GOOGLE, "google-id-token")).thenReturn(token(nonce));
        var loginResult = mvc.perform(post("/api/v1/auth/social/GOOGLE").session(session)
            .header("X-CSRF-TOKEN", anonymousCsrf).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"google-id-token\",\"nickname\":\"Google 사용자\"}"))
            .andExpect(status().isOk()).andReturn();
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        var login = mapper.readTree(loginResult.getResponse().getContentAsString());
        String freshCsrf = login.path("csrfToken").asText();
        assertThat(login.path("csrfHeaderName").asText()).isEqualTo("X-CSRF-TOKEN");
        mvc.perform(get("/api/v1/users").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Google 사용자"));
        String registration = "{\"token\":\"test-google-fcm-token\",\"platform\":\"IOS\",\"installationId\":\"b1f8ebbf-c4b8-457d-b072-92ed50806d8d\"}";
        mvc.perform(put("/api/v1/device-tokens").session(session).header("X-CSRF-TOKEN", anonymousCsrf)
            .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/device-tokens").session(session).header("X-CSRF-TOKEN", freshCsrf)
            .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/social/GOOGLE").session(session).header("X-CSRF-TOKEN", freshCsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"google-id-token\"}"))
            .andExpect(status().isUnauthorized());
        verify(verifier, times(1)).verify(SocialProvider.GOOGLE, "google-id-token");
        mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN", freshCsrf))
            .andExpect(status().isNoContent());
    }

    @Test void appleLoginMaintainsSessionForProfileAndPushRegistration() throws Exception {
        var session = new MockHttpSession();
        String oldSessionId = session.getId(), anonymousCsrf = csrf(session);
        var challengeResult = mvc.perform(post("/api/v1/auth/social/APPLE/challenge").session(session)
            .header("X-CSRF-TOKEN", anonymousCsrf))
            .andExpect(status().isOk()).andReturn();
        String nonce = mapper.readTree(challengeResult.getResponse().getContentAsString()).path("nonce").asText();
        when(verifier.verify(SocialProvider.APPLE, "apple-id-token")).thenReturn(token(nonce));
        var loginResult = mvc.perform(post("/api/v1/auth/social/APPLE").session(session)
            .header("X-CSRF-TOKEN", anonymousCsrf).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"apple-id-token\",\"nickname\":\"Apple 사용자\"}"))
            .andExpect(status().isOk()).andReturn();
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        var login = mapper.readTree(loginResult.getResponse().getContentAsString());
        String freshCsrf = login.path("csrfToken").asText();
        assertThat(login.path("csrfHeaderName").asText()).isEqualTo("X-CSRF-TOKEN");
        mvc.perform(get("/api/v1/users").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Apple 사용자"));
        String registration = "{\"token\":\"test-apple-fcm-token\",\"platform\":\"IOS\",\"installationId\":\"f5963e49-5461-4769-a5d8-2533d7e22a64\"}";
        mvc.perform(put("/api/v1/device-tokens").session(session).header("X-CSRF-TOKEN", anonymousCsrf)
            .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/device-tokens").session(session).header("X-CSRF-TOKEN", freshCsrf)
            .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/social/APPLE").session(session).header("X-CSRF-TOKEN", freshCsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"apple-id-token\"}"))
            .andExpect(status().isUnauthorized());
        verify(verifier, times(1)).verify(SocialProvider.APPLE, "apple-id-token");
        mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN", freshCsrf))
            .andExpect(status().isNoContent());
    }

}
