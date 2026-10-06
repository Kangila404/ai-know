package org.aiknow.server.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.profile.repository.ProfileImgRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.domain.UserRole;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminProfileSecurityTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileImgRepository profiles;
    @Autowired SessionAuthenticationService sessions;
    @Autowired ObjectMapper objectMapper;

    private MockHttpSession login(UserRole role) {
        User user = User.createSocialUser("security-test");
        ReflectionTestUtils.setField(user, "userRole", role);
        users.saveAndFlush(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            sessions.login(user.getUserId(), request, new MockHttpServletResponse());
            return (MockHttpSession) request.getSession(false);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        String response = mvc.perform(get("/api/v1/auth/csrf").session(session))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void adminSessionWithCsrfCanCreateProfile() throws Exception {
        MockHttpSession session = login(UserRole.ADMIN);
        long count = profiles.count();
        mvc.perform(post("/api/v1/admin/profile").session(session)
                .header("X-CSRF-TOKEN", csrfToken(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"imgUrl\":\"/test.png\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("test"));
        assertThat(profiles.count()).isEqualTo(count + 1);
    }

    @Test
    void adminSessionWithoutCsrfCannotCreateProfile() throws Exception {
        MockHttpSession session = login(UserRole.ADMIN);
        long count = profiles.count();
        mvc.perform(post("/api/v1/admin/profile").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"imgUrl\":\"/test.png\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(profiles.count()).isEqualTo(count);
    }

    @Test
    void regularUserWithCsrfCannotCreateProfile() throws Exception {
        MockHttpSession session = login(UserRole.USER);
        long count = profiles.count();
        mvc.perform(post("/api/v1/admin/profile").session(session)
                .header("X-CSRF-TOKEN", csrfToken(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"imgUrl\":\"/test.png\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(profiles.count()).isEqualTo(count);
    }

    @Test
    void swaggerDocumentsCsrfHeaderForAdminWrites() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/admin/profile'].post.parameters[0].name")
                .value("X-CSRF-TOKEN"))
            .andExpect(jsonPath("$.paths['/api/v1/admin/profile'].post.parameters[0].required")
                .value(true));
    }
}
