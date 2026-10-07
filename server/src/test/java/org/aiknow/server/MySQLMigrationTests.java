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
        var flyway = config().load(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(flyway.migrate().migrationsExecuted).isZero(); flyway.validate();
        try (var context = new SpringApplicationBuilder(ServerApplication.class).profiles("test").run(
            "--server.port=0", "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password, "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true", "--spring.batch.jdbc.initialize-schema=never")) {
            assertThat(context.isActive()).isTrue();
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
        assertThat(adopted.migrate().migrationsExecuted).isEqualTo(2);
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement();
             var rs = s.executeQuery("SELECT nickname FROM users WHERE user_id='migration-fixture'")) {
            assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("preserved");
        }
    }
}
