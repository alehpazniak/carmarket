package com.carmarket.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway was added after the databases existed (ddl-auto created them). A fresh database must run
 * every migration; an existing one is baselined at V2 and only gets V3+ (same settings as application.yml).
 */
@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(String schema) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .baselineOnMigrate(true)
            .baselineVersion("2")
            .load();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void freshDatabaseRunsAllMigrations() {
        flyway("fresh").migrate();

        assertThat(jdbc().queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = 'fresh' AND table_name = 'users' AND column_name = 'last_login_at'""", Long.class))
            .isEqualTo(1);
        assertThat(jdbc().queryForObject("SELECT count(*) FROM fresh.flyway_schema_history WHERE success AND version IS NOT NULL", Long.class))
            .isEqualTo(3);
    }

    @Test
    void existingDatabaseIsBaselinedAndOnlyGetsTheNewMigrations() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE SCHEMA legacy");
        // As Hibernate ddl-auto created it: no flyway_schema_history, no last_login_at
        jdbc.execute("""
            CREATE TABLE legacy.users (id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE, display_name VARCHAR(255),
              avatar_url VARCHAR(255), role VARCHAR(255) NOT NULL CHECK (role IN ('USER','DEALER','ADMIN')),
              email_verified BOOLEAN, is_active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP)""");
        jdbc.execute("INSERT INTO legacy.users (id, email, role, is_active) VALUES (gen_random_uuid(), 'a@b.pl', 'ADMIN', true)");

        flyway("legacy").migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy.users WHERE last_login_at IS NULL", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT version FROM legacy.flyway_schema_history ORDER BY installed_rank", String.class))
            .containsExactly("2", "3");
    }
}
