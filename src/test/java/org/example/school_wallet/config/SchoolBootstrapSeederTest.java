package org.example.school_wallet.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

class SchoolBootstrapSeederTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @Test
    void rejectsBlankUsername() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> seeder(properties("   ", "StrongPassword123!"), new MockEnvironment()).run(args));

        assertTrue(error.getMessage().contains("super-admin-username"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsBlankPassword() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> seeder(properties("superadmin", ""), new MockEnvironment()).run(args));

        assertTrue(error.getMessage().contains("SUPER_ADMIN_PASSWORD"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsShortPassword() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> seeder(properties("superadmin", "short"), new MockEnvironment()).run(args));

        assertTrue(error.getMessage().contains("at least 12"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsPlaceholderPasswordInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> seeder(properties("superadmin", "ChangeMe123!"), environment).run(args));

        assertTrue(error.getMessage().contains("default SUPER_ADMIN_PASSWORD"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void acceptsDevDefaults() {
        assertDoesNotThrow(
                () -> seeder(properties("superadmin", "ChangeMe123!"), new MockEnvironment()).run(args));
    }

    private SchoolBootstrapSeeder seeder(AppProperties properties, Environment environment) {
        return new SchoolBootstrapSeeder(jdbc, encoder, properties, environment);
    }

    private AppProperties properties(String username, String password) {
        AppProperties properties = new AppProperties();
        properties.getBootstrap().setSuperAdminUsername(username);
        properties.getBootstrap().setSuperAdminPassword(password);
        return properties;
    }
}
