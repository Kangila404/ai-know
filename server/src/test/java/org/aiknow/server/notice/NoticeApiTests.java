package org.aiknow.server.notice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.user.domain.*;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NoticeApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SessionAuthenticationService sessions;
    @Autowired ObjectMapper mapper;

    private MockHttpSession login(UserRole role) {
        var user = User.createSocialUser("notice-test");
        ReflectionTestUtils.setField(user, "userRole", role); users.saveAndFlush(user);
        var request = new MockHttpServletRequest();
        try { sessions.login(user.getUserId(), request, new MockHttpServletResponse()); return (MockHttpSession) request.getSession(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private String csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session))
            .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void draftPublishUnpublishDeleteLifecycle() throws Exception {
        var admin = login(UserRole.ADMIN); var reader = login(UserRole.USER); var token = csrf(admin);
        var draft = "{\"title\":\"공지\",\"content\":\"본문\",\"published\":false}";
        var result = mvc.perform(post("/api/v1/admin/notices").session(admin).header("X-CSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON).content(draft)).andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/notices/{id}", id).session(reader)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notices").session(reader)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/notices/{id}", id).session(admin)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/notices/{id}", id).session(admin).header("X-CSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON).content(draft.replace("false", "true")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").isNotEmpty());
        mvc.perform(get("/api/v1/notices").session(reader)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/v1/admin/notices/{id}", id).session(admin).header("X-CSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON).content(draft)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notices/{id}", id).session(reader)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/notices/{id}", id).session(admin).header("X-CSRF-TOKEN", token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/notices/{id}", id).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void enforcesRolesCsrfAndInputLimits() throws Exception {
        var admin = login(UserRole.ADMIN); var reader = login(UserRole.USER);
        var body = "{\"title\":\"공지\",\"content\":\"본문\",\"published\":true}";
        mvc.perform(get("/api/v1/notices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/notices").session(reader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notices").session(admin).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notices").session(reader).header("X-CSRF-TOKEN", csrf(reader))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notices").session(admin).header("X-CSRF-TOKEN", csrf(admin))
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("공지", " "))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notices?size=101").session(reader)).andExpect(status().isBadRequest());
    }
}
