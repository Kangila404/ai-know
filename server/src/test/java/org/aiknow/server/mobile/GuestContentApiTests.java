package org.aiknow.server.mobile;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.cardNews.repository.*;
import org.aiknow.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class GuestContentApiTests {
    @Autowired MockMvc mvc;
    @Autowired CardNewsRepository news;
    @Autowired CardViewRepository views;
    @Autowired CardReadRepository reads;
    @Autowired UserRepository users;
    @Autowired org.aiknow.server.notification.batch.DailyNewsEditionRepository editions;
    @Autowired org.aiknow.server.notification.batch.NewsDeliveryProperties delivery;
    @Autowired java.time.Clock clock;
    private CardNews article(String title, InspectionStatus inspection, PublicationStatus publication) {
        return news.saveAndFlush(CardNews.builder().title(title).summary("guest fixture")
            .publicationDate(LocalDate.now()).inspectionStatus(inspection).publicationStatus(publication).build());
    }
    @Test void guestTodayRequiresStartedEditionAndStillChecksPublication() throws Exception {
        var date=LocalDate.now(clock.withZone(delivery.zone()));
        var published=article("Guest today",InspectionStatus.APPROVED,PublicationStatus.PUBLISHED);
        editions.deleteAll(); editions.flush();
        var edition=org.aiknow.server.notification.batch.DailyNewsEdition.select(date,published.getId(),clock.instant(),clock.instant());
        editions.saveAndFlush(edition);
        mvc.perform(get("/api/v1/cardNews/today")).andExpect(status().isNotFound());
        edition.start(clock.instant()); editions.saveAndFlush(edition);
        mvc.perform(get("/api/v1/cardNews/today")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(published.getId()));
        org.springframework.test.util.ReflectionTestUtils.setField(published,"publicationStatus",PublicationStatus.HIDDEN);
        news.saveAndFlush(published);
        mvc.perform(get("/api/v1/cardNews/today")).andExpect(status().isNotFound());
    }
    @Test void guestSearchAndDetailOnlyExposePublishedApprovedContentWithoutHistory() throws Exception {
        var published=article("GuestUnique public",InspectionStatus.APPROVED,PublicationStatus.PUBLISHED);
        var ready=article("GuestUnique ready",InspectionStatus.APPROVED,PublicationStatus.READY);
        var hidden=article("GuestUnique hidden",InspectionStatus.APPROVED,PublicationStatus.HIDDEN);
        // An inconsistent publication flag must never turn an unreviewed card public.
        var draft=article("GuestUnique draft",InspectionStatus.PENDING,PublicationStatus.PUBLISHED);
        long userCount=users.count(),viewCount=views.count(),readCount=reads.count();
        mvc.perform(get("/api/v1/cardNews").param("query","guestunique").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(published.getId()))
            .andExpect(jsonPath("$[0].liked").value(false)).andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(get("/api/v1/cardNews").param("query","guestunique").param("size","1").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/cardNews/{id}",published.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.liked").value(false)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId",published.getId().toString())).andExpect(status().isOk());
        for(var privateCard:java.util.List.of(ready,hidden,draft)) {
            mvc.perform(get("/api/v1/cardNews/{id}",privateCard.getId())).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/cardNews/cardSlides").param("cardNewsId",privateCard.getId().toString())).andExpect(status().isNotFound());
        }
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(views.count()).isEqualTo(viewCount);
        assertThat(reads.count()).isEqualTo(readCount);
    }
    @Test void personalReadsAndAllMutationsStillRequireAuthenticationEvenWithCsrf() throws Exception {
        mvc.perform(get("/api/v1/cardNews").param("isOnlyLiked","true")).andExpect(status().isUnauthorized());
        for(var path:java.util.List.of("/api/v1/users","/api/v1/notification-setting","/api/v1/profile","/api/v1/admin/card-news","/api/v1/admin/news-submissions","/api/v1/admin/users","/api/v1/admin/generations"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/cardNews/1/like").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/cardNews/1/read").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/onboarding").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/notification-setting").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"isAllowed\":true,\"settingTime\":\"09:00:00\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/device-tokens").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cardNews/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notices")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cardNews/1/like")).andExpect(status().isUnauthorized());
    }
}
