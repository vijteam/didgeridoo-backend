package io.github.vijteam.didgeridoo.tasks.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import javax.sql.DataSource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskMigrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private final String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");

    private Flyway latestMigration() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .load();
    }

    @AfterEach
    void removeTestSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void shouldUpgradeVersionTwoWithoutLosingUsers() {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target("2")
                .load()
                .migrate();
        UUID user = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO "
                        + schema
                        + ".users (id, username, display_name, email, password_hash) VALUES (?, 'UpgradeUser', 'User', 'upgrade@example.com', 'hash')",
                user);
        Flyway flyway = latestMigration();
        flyway.migrate();
        assertEquals("6", flyway.info().current().getVersion().toString());
        assertEquals(
                user,
                jdbc.queryForObject(
                        "SELECT id FROM " + schema + ".users WHERE username = 'UpgradeUser'",
                        UUID.class));
    }

    @Test
    void shouldRollBackTaskAndHistoryOnDataTransactionFailure() {
        latestMigration().migrate();
        TransactionTemplate transaction =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    UUID space = UUID.randomUUID();
                                    UUID statusId = UUID.randomUUID();
                                    UUID taskId = UUID.randomUUID();
                                    jdbc.update(
                                            "INSERT INTO "
                                                    + schema
                                                    + ".task_statuses (id,space_id,name,kind,position) VALUES (?, ?, 'New', 'INITIAL', 0)",
                                            statusId,
                                            space);
                                    jdbc.update(
                                            "INSERT INTO "
                                                    + schema
                                                    + ".tasks (id,space_id,status_id,title) VALUES (?, ?, ?, 'Rollback')",
                                            taskId,
                                            space,
                                            statusId);
                                    jdbc.update(
                                            "INSERT INTO "
                                                    + schema
                                                    + ".task_changes (task_id,kind,changed_fields) VALUES (?, 'CREATED', '{}'::jsonb)",
                                            taskId);
                                    throw new IllegalStateException(
                                            "Simulated task transaction failure");
                                }));
        assertEquals(
                0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".tasks", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + schema + ".task_changes", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + schema + ".task_statuses", Integer.class));
    }
}
