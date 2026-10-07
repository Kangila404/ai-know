package org.aiknow.server.mobile;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.aiknow.server.notification.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MobileContentApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired CardNewsRepository news;
    @Autowired CardReadRepository reads;
    @Autowired LikesRepository likes;
    @Autowired DeviceTokenRepository devices;
    @Autowired NotificationSettingRepository settings;
    @Autowired SessionAuthenticationService sessions;

    MockHttpSession login(User user) {
        users.saveAndFlush(user);
        var req = new MockHttpServletRequest();
        try { sessions.login(user.getUserId(),req,new MockHttpServletResponse()); return (MockHttpSession)req.getSession(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    String csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn()
            .getResponse().getContentAsString()).get("token").asText();
    }
    CardNews article(String title, PublicationStatus state) {
        return news.saveAndFlush(CardNews.builder().title(title).summary("mobile search fixture")
            .publicationDate(LocalDate.now()).inspectionStatus(InspectionStatus.APPROVED).publicationStatus(state).build());
    }
    @Test void searchPaginationAndPerUserStateExcludeHiddenContent() throws Exception {
        var user=User.createSocialUser("reader"); var session=login(user); var token=csrf(session);
        var first=article("UniqueMobile needle A",PublicationStatus.PUBLISHED);
        var second=article("UniqueMobile needle B",PublicationStatus.PUBLISHED);
        var hidden=article("UniqueMobile needle hidden",PublicationStatus.HIDDEN);
        mvc.perform(get("/api/v1/cardNews").session(session).param("query","uniquemobile needle").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(second.getId()));
        mvc.perform(get("/api/v1/cardNews").session(session).param("query","uniquemobile needle").param("size","1").param("page","1"))
            .andExpect(jsonPath("$[0].id").value(first.getId()));
        mvc.perform(patch("/api/v1/cardNews/{id}/like",first.getId()).session(session).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}")).andExpect(status().isOk());
        for(int n=0;n<2;n++) mvc.perform(put("/api/v1/cardNews/{id}/read",first.getId()).session(session).header("X-CSRF-TOKEN",token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cardNews/{id}",first.getId()).session(session))
            .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/cardNews").session(session).param("isOnlyLiked","true"))
            .andExpect(jsonPath("$.length()").value(1));
        var other=login(User.createSocialUser("other"));
        mvc.perform(get("/api/v1/cardNews/{id}",first.getId()).session(other))
            .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.read").value(false));
        mvc.perform(get("/api/v1/cardNews/{id}",hidden.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/cardNews/{id}/read",hidden.getId()).session(session).header("X-CSRF-TOKEN",token))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/cardNews/{id}/read",first.getId()).session(session)).andExpect(status().isForbidden());
        assertThat(reads.readIds(user.getId(),java.util.List.of(first.getId(),hidden.getId()))).containsExactly(first.getId());
    }
    @Test void onboardingAndWithdrawalPersistAndClearUserOwnedData() throws Exception {
        var user=User.createSocialUser("mobile-user"); var session=login(user); var token=csrf(session);
        mvc.perform(patch("/api/v1/users/onboarding").session(session)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/onboarding").session(session).header("X-CSRF-TOKEN",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingCompleted").value(true));
        mvc.perform(get("/api/v1/users").session(session)).andExpect(jsonPath("$.onboardingCompleted").value(true));
        var card=article("withdraw fixture",PublicationStatus.PUBLISHED);
        mvc.perform(patch("/api/v1/cardNews/{id}/like",card.getId()).session(session).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/cardNews/{id}/read",card.getId()).session(session).header("X-CSRF-TOKEN",token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notification-setting").session(session)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/device-tokens").session(session).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fixture-mobile-token\",\"platform\":\"IOS\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/users/me").session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/me").session(session).header("X-CSRF-TOKEN",token)).andExpect(status().isNoContent());
        users.flush();
        assertThat(users.findByUserId(user.getUserId())).isEmpty();
        assertThat(likes.findByUserAndCardNews(user,card)).isEmpty();
        assertThat(reads.existsByUserIdAndCardNewsId(user.getId(),card.getId())).isFalse();
        assertThat(settings.findByUserId(user.getId())).isEmpty();
        assertThat(devices.findByToken("fixture-mobile-token")).isEmpty();
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }
}
