package org.aiknow.server;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Only creates and removes randomly named test schemas; never touches the application schema. */
@EnabledIfEnvironmentVariable(named = "AIKNOW_MYSQL_TEST_URL", matches = ".+")
class MySQLMigrationTests {
    private String database, url;
    private final String root = System.getenv("AIKNOW_MYSQL_TEST_URL");
    private final String user = System.getenv("AIKNOW_MYSQL_TEST_USER");
    private final String password = System.getenv("AIKNOW_MYSQL_TEST_PASSWORD");
    private boolean created;
    @BeforeEach void create() throws Exception {
        database = "aiknow_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(root, user, password); var s = c.createStatement()) {
            s.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"); created = true;
        }
        int query = root.indexOf('?');
        String base = query < 0 ? root : root.substring(0, query);
        url = base.substring(0, base.lastIndexOf('/') + 1) + database + (query < 0 ? "" : root.substring(query));
    }
    @AfterEach void removeOnlyCreatedTestDatabase() throws Exception {
        if (!created || !database.matches("aiknow_test_[a-f0-9]{32}")) return;
        try (var c = DriverManager.getConnection(root, user, password); var s = c.createStatement()) {
            s.execute("DROP DATABASE `" + database + "`");
        }
    }
    private org.flywaydb.core.api.configuration.FluentConfiguration config() {
        return Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
            .baselineOnMigrate(false).cleanDisabled(true);
    }
    @Test void newDatabaseMigratesOnceAndHibernateValidatesAllEntities() {
        var flyway = config().load(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(flyway.migrate().migrationsExecuted).isZero(); flyway.validate();
        try (var context = new SpringApplicationBuilder(ServerApplication.class).profiles("test").run(
            "--server.port=0", "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password, "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true", "--spring.batch.jdbc.initialize-schema=never")) {
            assertThat(context.isActive()).isTrue();
            verifyApprovalBeforePublication(context);
        }
    }
    @Test void existingSchemaRequiresExplicitBaselineAndPreservesRows() throws Exception {
        config().target("1").load().migrate();
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
            s.execute("INSERT INTO users(created_at,updated_at,is_onboarding_completed,nickname,user_id,user_role) VALUES(NOW(),NOW(),0,'preserved','migration-fixture','USER')");
            s.execute("DROP TABLE flyway_schema_history");
        }
        assertThatThrownBy(() -> config().load().migrate()).isInstanceOf(FlywayException.class);
        var adopted = config().baselineVersion("1").load(); adopted.baseline();
        assertThat(adopted.migrate().migrationsExecuted).isEqualTo(7);
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement();
             var rs = s.executeQuery("SELECT nickname FROM users WHERE user_id='migration-fixture'")) {
            assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("preserved");
        }
    }
    @Test void selectionContextUpgradePreservesExistingRequestsAndResponses() throws Exception {
        config().target("4").load().migrate();
        try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
            s.execute("INSERT INTO news_generation_record(attempt_key,source_url,task,request_hash,request_json,response_json,created_at,version) VALUES(REPEAT('a',64),'https://example.test/legacy','image_selection',REPEAT('b',64),'{}','{\"original\":true}',NOW(),0)");
        }
        assertThat(config().load().migrate().migrationsExecuted).isEqualTo(4);
        try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement();
             var r=s.executeQuery("SELECT request_json,response_json,context_json FROM news_generation_record")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("{}");
            assertThat(r.getString(2)).isEqualTo("{\"original\":true}"); assertThat(r.getString(3)).isNull();
        }
    }

    private void verifyApprovalBeforePublication(org.springframework.context.ConfigurableApplicationContext context) {
        var users = context.getBean(org.aiknow.server.user.repository.UserRepository.class);
        var reviewer = users.saveAndFlush(org.aiknow.server.user.domain.User.createSocialUser("migration-reviewer"));
        var service = context.getBean(org.aiknow.server.ingestion.NewsSubmissionService.class);
        var image = new org.aiknow.server.ingestion.NewsImportRequest.Image("https://example.test/image.png",
            org.aiknow.server.ingestion.NewsImportRequest.ImageOrigin.SOURCE, "https://example.test/source", "Author / CC BY 4.0");
        var draft = new org.aiknow.server.ingestion.NewsImportRequest("Source", "https://example.test/article",
            java.time.OffsetDateTime.parse("2026-10-22T00:00:00Z"), "Theory", "Summary", java.util.List.of("Key point"), image,
            java.util.List.of(new org.aiknow.server.ingestion.NewsImportRequest.Slide(1, "Slide", "Content", "text", image)));
        var received = service.receive(draft);
        var request = new org.aiknow.server.ingestion.NewsSubmissionService.Approve(
            org.aiknow.server.cardNews.domain.ContentType.NEWS, java.util.List.of());
        var approved = service.approve(received.id(), request, reviewer.getUserId());
        assertThat(approved.status()).isEqualTo(org.aiknow.server.cardNews.domain.InspectionStatus.APPROVED);
        assertThat(service.approve(received.id(), request, reviewer.getUserId()).cardNewsId()).isEqualTo(approved.cardNewsId());
        var repository = context.getBean(org.aiknow.server.cardNews.repository.CardNewsRepository.class);
        var article = repository.findById(approved.cardNewsId()).orElseThrow();
        assertThat(article.getPublicationDate()).isNull();
        assertThat(article.getPublicationStatus()).isEqualTo(org.aiknow.server.cardNews.domain.PublicationStatus.READY);
        assertThat(article.getTitleImgUrl()).isEqualTo(image.url());
        assertThat(article.getTitleImageCredit()).isEqualTo(image.credit());
        var date = java.time.LocalDate.of(2026, 10, 23);
        article.publishForDelivery(date, java.time.Instant.parse("2026-10-23T00:00:00Z"));
        repository.saveAndFlush(article);
        assertThat(repository.findById(article.getId()).orElseThrow().getPublicationDate()).isEqualTo(date);
    }

    @Test void publicationDateUpgradePreservesPublishedDatesAndAllowsReadyContent() throws Exception {
        config().target("5").load().migrate();
        String insert = "INSERT INTO card_news(title,created_at,updated_at,inspection_status,content_type,publication_status,publication_date) VALUES('fixture',NOW(),NOW(),'APPROVED','NEWS','READY',%s)";
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
            s.execute(insert.formatted("'2026-10-22'"));
            assertThatThrownBy(() -> s.execute(insert.formatted("NULL"))).isInstanceOf(SQLException.class);
        }
        assertThat(config().load().migrate().migrationsExecuted).isEqualTo(3);
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
            s.execute(insert.formatted("NULL"));
            try (var rs = s.executeQuery("SELECT publication_date FROM card_news ORDER BY id")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getDate(1).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 22));
                assertThat(rs.next()).isTrue(); assertThat(rs.getDate(1)).isNull();
            }
        }
    }

    @Test void theoryArchiveUpgradeOnlyPublishesApprovedReadyTheory() throws Exception {
        config().target("7").load().migrate();
        try (var c=DriverManager.getConnection(url,user,password);
             var s=c.prepareStatement("INSERT INTO card_news(title,inspection_status,content_type,publication_status,created_at,updated_at,approved_at) VALUES(?,?,?,?,NOW(),NOW(),'2026-10-22 18:00:00')")) {
            for (var row : new String[][] {{"eligible","APPROVED","AI_THEORY","READY"}, {"news","APPROVED","NEWS","READY"},
                {"pending","PENDING","AI_THEORY","READY"},{"hidden","APPROVED","AI_THEORY","HIDDEN"},
                {"published","APPROVED","AI_THEORY","PUBLISHED"}}) {
                for (int i=0; i<row.length; i++) s.setString(i+1,row[i]);
                s.executeUpdate();
            }
        }
        assertThat(config().load().migrate().migrationsExecuted).isOne();
        assertThat(config().load().migrate().migrationsExecuted).isZero();
        try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement();
             var r=s.executeQuery("SELECT title,publication_status,publication_date,version,first_used_at,last_used_at FROM card_news ORDER BY id")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(2)).isEqualTo("PUBLISHED");
            assertThat(r.getDate(3).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026,10,23));
            assertThat(r.getLong(4)).isEqualTo(1); assertThat(r.getTimestamp(5)).isNull(); assertThat(r.getTimestamp(6)).isNull();
            for (var state : java.util.List.of("READY","READY","HIDDEN","PUBLISHED")) {
                assertThat(r.next()).isTrue(); assertThat(r.getString(2)).isEqualTo(state);
                assertThat(r.getDate(3)).isNull(); assertThat(r.getLong(4)).isZero();
            }
        }
    }
}
