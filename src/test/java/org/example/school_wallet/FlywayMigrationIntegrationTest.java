package org.example.school_wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies Flyway migrations and Hibernate {@code ddl-auto: validate} on real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("school_wallet_test")
            .withUsername("school_wallet")
            .withPassword("school_wallet");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("app.bootstrap.enabled", () -> "false");
    }

    @Test
    void migrationsCreateEveryTableMappedByEntities() throws Exception {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));

        try (Connection connection = dataSource.getConnection()) {
            for (String table : new String[] {
                "tenants",
                "app_users",
                "user_roles",
                "refresh_tokens",
                "password_reset_tokens",
                "tenant_modules",
                "audit_logs",
                "students",
                "teachers",
                "school_classes",
                "subjects",
                "attendance_records",
                "grade_records",
                "fee_records",
                "timetable_entries",
                "report_definitions",
                "app_settings",
                "academic_years",
                "terms",
                "enrolments",
                "teacher_assignments",
                "fee_payments",
                "notification_outbox",
                "fee_categories",
                "fee_plans",
                "invoices",
                "invoice_items",
                "pocket_cards",
                "spending_limits",
                "pocket_transactions",
                "store_items",
                "store_terminals",
                "inventory_items",
                "stock_movements",
                "purchase_requests",
                "departments",
                "staff_attendance",
                "school_tasks",
                "books",
                "book_copies",
                "book_loans",
                "book_reservations",
                "lab_sessions",
                "periods",
                "exams",
                "exam_sessions",
                "duty_assignments",
                "report_cards",
                "staff_leaves",
                "school_events",
                "online_tests",
                "test_attempts",
                "parent_letters"
            }) {
                assertTrue(tableExists(connection, table), "missing table: " + table);
            }

            assertTrue(columnExists(connection, "students", "user_id"));
            assertTrue(columnExists(connection, "attendance_records", "teacher_id"));
            assertTrue(columnExists(connection, "attendance_records", "class_id"));
            assertTrue(columnExists(connection, "grade_records", "teacher_id"));
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

}
