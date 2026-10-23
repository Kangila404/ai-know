package org.aiknow.server.admin;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.user.domain.*;
import org.aiknow.server.user.repository.UserRepository;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminOperationsTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CardNewsRepository cards;
    @Autowired CardViewRepository views;
    @Autowired SessionAuthenticationService sessions;
    @Autowired ObjectMapper mapper;
    @MockitoBean Clock clock;
    private User admin, reader;
    private MockHttpSession adminSession, readerSession;
    private final Instant now = Instant.parse("2026-10-23T06:00:00Z");
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        admin = User.createSocialUser("operations-admin");
        admin.updateAdministration(UserRole.ADMIN, false); users.saveAndFlush(admin);
        reader = users.saveAndFlush(User.createSocialUser("operations-reader"));
        adminSession=login(admin); readerSession=login(reader);
    }
    private MockHttpSession login(User user) {
        var request=new MockHttpServletRequest();
        try { sessions.login(user.getUserId(),request,new MockHttpServletResponse()); return (MockHttpSession)request.getSession(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private String csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    private String update(User user, UserRole role, boolean suspended) throws Exception {
        return mapper.writeValueAsString(new UserAdministrationService.Update(user.getVersion(),role,suspended,"운영 검토"));
    }
    @Test void listsSearchesAndValidatesAdminOnlyMutations() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").session(adminSession).param("query","operations-reader"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(reader.getId()));
        mvc.perform(get("/api/v1/admin/users").session(adminSession).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession)
            .contentType(MediaType.APPLICATION_JSON).content(update(reader,UserRole.ADMIN,false))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(readerSession).header("X-CSRF-TOKEN",csrf(readerSession))
            .contentType(MediaType.APPLICATION_JSON).content(update(reader,UserRole.ADMIN,false))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/analytics/summary").session(readerSession)).andExpect(status().isForbidden());
    }
    @Test void roleChangesApplyToExistingSessionsAndAreVersionedAndAudited() throws Exception {
        var original=update(reader,UserRole.ADMIN,false); var token=csrf(adminSession);
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content(original)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/users").session(readerSession)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content(original)).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content(update(reader,UserRole.USER,false))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/editorial/history").session(adminSession)
            .param("resourceType","USER").param("resourceId",reader.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("ACCOUNT_UPDATE"));
    }
    @Test void suspensionRevokesSessionBlocksNewLoginAndCanBeReversed() throws Exception {
        var token=csrf(adminSession);
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content(update(reader,UserRole.USER,true))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").session(readerSession)).andExpect(status().isUnauthorized());
        assertThat(readerSession.isInvalid()).isTrue();
        assertThatThrownBy(() -> login(reader)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mvc.perform(patch("/api/v1/admin/users/{id}",reader.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
            .contentType(MediaType.APPLICATION_JSON).content(update(reader,UserRole.USER,false))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").session(login(reader))).andExpect(status().isOk());
    }
    @Test void administratorCannotRemoveOwnAccess() throws Exception {
        var token=csrf(adminSession);
        for (var body : java.util.List.of(update(admin,UserRole.USER,false),update(admin,UserRole.ADMIN,true))) {
            mvc.perform(patch("/api/v1/admin/users/{id}",admin.getId()).session(adminSession).header("X-CSRF-TOKEN",token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        }
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN); assertThat(admin.isSuspended()).isFalse();
    }
    @Test void countsDetailViewsAndDistinctUsersButNotListsHiddenCardsOrRapidRetries() throws Exception {
        var card=cards.saveAndFlush(CardNews.builder().title("view-fixture").inspectionStatus(InspectionStatus.APPROVED)
            .publicationStatus(PublicationStatus.PUBLISHED).build());
        var hidden=cards.saveAndFlush(CardNews.builder().title("hidden-view-fixture").inspectionStatus(InspectionStatus.APPROVED)
            .publicationStatus(PublicationStatus.HIDDEN).build());
        mvc.perform(get("/api/v1/cardNews").session(readerSession)).andExpect(status().isOk());
        assertThat(views.findByUserIdAndCardNewsId(reader.getId(),card.getId())).isEmpty();
        for (int i=0;i<3;i++) mvc.perform(get("/api/v1/cardNews/{id}",card.getId()).session(readerSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cardNews/{id}",hidden.getId()).session(readerSession)).andExpect(status().isNotFound());
        assertThat(views.findByUserIdAndCardNewsId(reader.getId(),hidden.getId())).isEmpty();
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        mvc.perform(get("/api/v1/cardNews/{id}",card.getId()).session(readerSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cardNews/{id}",card.getId()).session(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/analytics/cards").session(adminSession).param("query","view-fixture"))
            .andExpect(status().isOk());
        var stats=views.counts(java.util.List.of(card.getId())).getFirst();
        assertThat(stats.getTotalViews()).isEqualTo(3); assertThat(stats.getUniqueViewers()).isEqualTo(2);
        mvc.perform(get("/api/v1/admin/analytics/summary").session(adminSession)).andExpect(status().isOk());
    }
}
