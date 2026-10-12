package com.carmarket.car;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway was added after the databases existed (ddl-auto created them, including a CHECK constraint
 * on status). A fresh database runs every migration; an existing one is baselined at V6 and only
 * gets V7+ (same settings as application.yml).
 */
@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(String schema) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .table("flyway_schema_history_car")
            .baselineOnMigrate(true)
            .baselineVersion("6")
            .load();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static String insert(String schema, String status) {
        return "INSERT INTO " + schema + ".car_listings (id, seller_id, make, model, year, price, status) "
            + "VALUES (gen_random_uuid(), gen_random_uuid(), 'Audi', 'A4', 2019, 10000, '" + status + "')";
    }

    @Test
    void freshDatabaseRunsAllMigrationsAndAcceptsSuspended() {
        flyway("fresh").migrate();

        jdbc().execute(insert("fresh", "SUSPENDED"));
        assertThatThrownBy(() -> jdbc().execute(insert("fresh", "BOGUS")));
    }

    @Test
    void existingHibernateDatabaseGetsTheNewStatusAllowed() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE SCHEMA legacy");
        // As Hibernate ddl-auto created it: enum CHECK constraint, no flyway_schema_history
        jdbc.execute("""
            CREATE TABLE legacy.car_listings (id UUID PRIMARY KEY, seller_id UUID NOT NULL, make VARCHAR(255) NOT NULL,
              model VARCHAR(255) NOT NULL, year INT NOT NULL, price NUMERIC(38,2) NOT NULL,
              status VARCHAR(255) NOT NULL CONSTRAINT car_listings_status_check
                CHECK (status IN ('ACTIVE','SOLD','DRAFT','REMOVED')))""");
        assertThatThrownBy(() -> jdbc.execute(insert("legacy", "SUSPENDED")));

        flyway("legacy").migrate();

        jdbc.execute(insert("legacy", "SUSPENDED"));
        assertThat(jdbc.queryForList("SELECT version FROM legacy.flyway_schema_history_car ORDER BY installed_rank", String.class))
            .containsExactly("6", "7");
    }

    @Test
    void anotherServicesFlywayHistoryInTheSameSchemaIsIgnored() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE SCHEMA shared");
        // auction-import-service's history and tables, as on a car_db it was pointed at by default
        jdbc.execute("CREATE TABLE shared.flyway_schema_history (installed_rank INT, version VARCHAR(50), description VARCHAR(200))");
        jdbc.execute("INSERT INTO shared.flyway_schema_history VALUES (1, '1', 'auction tables'), (2, '2', 'add customs duty')");
        jdbc.execute("CREATE TABLE shared.auction_lots (id UUID PRIMARY KEY)");

        flyway("shared").migrate();

        assertThat(jdbc.queryForList("SELECT version FROM shared.flyway_schema_history_car ORDER BY installed_rank", String.class))
            .containsExactly("6", "7");
    }
}
