package org.example.school_wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbc;

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
                "parent_letters",
                "pm_accounts",
                "pm_schools",
                "pm_idempotency_keys",
                "pm_wallets",
                "pm_ledger_entries",
                "pm_cards",
                "pm_merchants",
                "pm_terminals",
                "pm_parent_student_links",
                "pm_topups",
                "pm_purchases",
                "pm_spending_rules",
                "pm_cash_outs",
                "pm_audit_log"
            }) {
                assertTrue(tableExists(connection, table), "missing table: " + table);
            }

            assertTrue(columnExists(connection, "students", "user_id"));
            assertTrue(columnExists(connection, "attendance_records", "teacher_id"));
            assertTrue(columnExists(connection, "attendance_records", "class_id"));
            assertTrue(columnExists(connection, "grade_records", "teacher_id"));
        }
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() {
        Timestamp now = Timestamp.from(Instant.now());
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String key = "trigger-test-" + UUID.randomUUID();

        jdbc.update(
                """
                insert into tenants
                    (id, code, name, o_level_enabled, a_level_enabled, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                "PM-TRIG-" + UUID.randomUUID().toString().substring(0, 8),
                "Ledger Trigger Test",
                true,
                false,
                "ACTIVE",
                0L,
                now,
                now);
        jdbc.update(
                """
                insert into students
                    (id, tenant_id, student_code, first_name, last_name, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                studentId,
                tenantId,
                "PM-TRIG-STU-" + UUID.randomUUID().toString().substring(0, 8),
                "Ledger",
                "Student",
                "ACTIVE",
                0L,
                now,
                now);
        jdbc.update(
                """
                insert into pm_idempotency_keys
                    (idempotency_key, scope, result_ref, created_at)
                values (?, ?, ?, ?)
                """,
                key,
                "TEST",
                UUID.randomUUID().toString(),
                now);
        jdbc.update(
                """
                insert into pm_wallets
                    (id, tenant_id, student_id, balance, status, daily_limit, weekly_limit,
                     monthly_limit, per_txn_limit, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                walletId,
                tenantId,
                studentId,
                1_000L,
                "ACTIVE",
                0L,
                0L,
                0L,
                0L,
                0L,
                now,
                now);
        jdbc.update(
                """
                insert into pm_ledger_entries
                    (id, tenant_id, wallet_id, reference_id, type, direction, amount, currency,
                     balance_after, account_code, counter_account_code, idempotency_key, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entryId,
                tenantId,
                walletId,
                UUID.randomUUID(),
                "TOP_UP",
                "CREDIT",
                1_000L,
                "TZS",
                1_000L,
                "STUDENT_WALLET",
                "PARENT_FUNDS_IN",
                key,
                null,
                now);

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update pm_ledger_entries set amount = ? where id = ?", 2_000L, entryId));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "delete from pm_ledger_entries where id = ?", entryId));
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
