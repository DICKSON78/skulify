package org.example.school_wallet.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.school_wallet.tenant.SchoolTenants;
import org.example.school_wallet.user.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Creates the platform admin, a demo school and role-based demo accounts. */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class SchoolBootstrapSeeder implements ApplicationRunner {

    public static final UUID SUPER_ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");
    public static final UUID SCHOOL_ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TEACHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222223");
    public static final UUID PARENT_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222224");
    public static final UUID STUDENT_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222225");
    public static final UUID HEAD_MASTER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222226");
    public static final UUID SECOND_MASTER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222227");
    public static final UUID ACADEMIC_MASTER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222228");
    public static final UUID DISCIPLINE_MASTER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222229");
    public static final UUID HR_MANAGER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222230");
    public static final UUID CLASS_TEACHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222231");
    public static final UUID ACCOUNTANT_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222232");

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final String PLACEHOLDER_PASSWORD = "ChangeMe123!";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AppProperties properties;
    private final Environment environment;

    public SchoolBootstrapSeeder(
            JdbcTemplate jdbc, PasswordEncoder encoder, AppProperties properties, Environment environment) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.getBootstrap();
        validateCredentials(bootstrap);

        Instant now = Instant.now();
        seedTenant(SchoolTenants.SYSTEM_ID, "SYSTEM", "KADE System", now);
        seedTenant(
                SchoolTenants.DEMO_SCHOOL_ID,
                SchoolTenants.DEMO_SCHOOL_CODE,
                "Skulify Academy",
                now);

        seedUser(
                SUPER_ADMIN_ID,
                SchoolTenants.SYSTEM_ID,
                bootstrap.getSuperAdminUsername(),
                bootstrap.getSuperAdminPhone(),
                null,
                bootstrap.getSuperAdminFullName(),
                bootstrap.getSuperAdminPassword(),
                List.of(Role.SUPER_ADMIN),
                now);

        seedUser(
                SCHOOL_ADMIN_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "admin",
                "+255700000010",
                "admin@skulify.test",
                "School Administrator",
                "Admin123!",
                List.of(Role.TENANT_ADMIN, Role.HEAD_TEACHER),
                now);
        seedUser(
                TEACHER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "teacher",
                "+255700000011",
                "teacher@skulify.test",
                "Mary Sharma",
                "Teacher123!",
                List.of(Role.TEACHER, Role.CLASS_TEACHER),
                now);
        seedUser(
                PARENT_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "parent",
                "+255700000012",
                "parent@skulify.test",
                "Asha Parent",
                "Parent123!",
                List.of(Role.PARENT),
                now);
        seedUser(
                STUDENT_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "student",
                "+255700000013",
                "student@skulify.test",
                "Asha Mwangi",
                "Student123!",
                List.of(Role.STUDENT),
                now);
        seedUser(
                CLASS_TEACHER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "classteacher",
                "+255700000031",
                "classteacher@skulify.test",
                "Peter Kamau",
                "Classteacher123!",
                List.of(Role.TEACHER, Role.CLASS_TEACHER),
                now);
        seedUser(
                HEAD_MASTER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "headmaster",
                "+255700000026",
                "headmaster@skulify.test",
                "Edward Mkubwa",
                "Headmaster123!",
                List.of(Role.HEAD_MASTER),
                now);
        seedUser(
                SECOND_MASTER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "secondmaster",
                "+255700000027",
                "secondmaster@skulify.test",
                "Zawadi Mosi",
                "Secondmaster123!",
                List.of(Role.SECOND_MASTER),
                now);
        seedUser(
                ACADEMIC_MASTER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "academic",
                "+255700000028",
                "academic@skulify.test",
                "Sofia Alimi",
                "Academic123!",
                List.of(Role.ACADEMIC_MASTER),
                now);
        seedUser(
                DISCIPLINE_MASTER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "discipline",
                "+255700000029",
                "discipline@skulify.test",
                "Jumanne Adili",
                "Discipline123!",
                List.of(Role.DISCIPLINE_MASTER),
                now);
        seedUser(
                HR_MANAGER_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "hr",
                "+255700000030",
                "hr@skulify.test",
                "Neema Rasilimali",
                "HrManager123!",
                List.of(Role.HR_MANAGER),
                now);
        seedUser(
                ACCOUNTANT_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID,
                "accountant",
                "+255700000032",
                "accountant@skulify.test",
                "Amani Hesabu",
                "Accountant123!",
                List.of(Role.ACCOUNTANT),
                now);
    }

    private void validateCredentials(AppProperties.Bootstrap bootstrap) {
        if (!StringUtils.hasText(bootstrap.getSuperAdminUsername())) {
            throw new IllegalStateException(
                    "app.bootstrap.super-admin-username must not be blank when bootstrap is enabled");
        }
        String password = bootstrap.getSuperAdminPassword();
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "app.bootstrap.super-admin-password must not be blank when bootstrap is enabled. "
                            + "Set SUPER_ADMIN_PASSWORD.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.bootstrap.super-admin-password must be at least "
                            + MIN_PASSWORD_LENGTH
                            + " characters when bootstrap is enabled");
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) && PLACEHOLDER_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "Refusing to bootstrap production with the default SUPER_ADMIN_PASSWORD. "
                            + "Set a unique strong password.");
        }
    }

    private void seedTenant(UUID id, String code, String name, Instant now) {
        Integer count = jdbc.queryForObject("select count(*) from tenants where id = ?", Integer.class, id);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update(
                """
                insert into tenants
                    (id, code, name, status, o_level_enabled, a_level_enabled,
                     version, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', true, ?, 0, ?, ?)
                """,
                id,
                code,
                name,
                SchoolTenants.SYSTEM_ID.equals(id),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private void seedUser(
            UUID id,
            UUID tenantId,
            String username,
            String phone,
            String email,
            String fullName,
            String password,
            List<Role> roles,
            Instant now) {
        Integer count = jdbc.queryForObject("select count(*) from app_users where id = ?", Integer.class, id);
        if (count == null || count == 0) {
            jdbc.update(
                    """
                    insert into app_users
                        (id, tenant_id, username, phone, email, full_name, password_hash,
                         enabled, must_change_password, mfa_enabled, version, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, true, false, false, 0, ?, ?)
                    """,
                    id,
                    tenantId,
                    username,
                    phone,
                    email,
                    fullName,
                    encoder.encode(password),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        for (Role role : roles) {
            jdbc.update(
                    """
                    insert into user_roles (user_id, role)
                    select ?, ?
                    where not exists (
                        select 1 from user_roles where user_id = ? and role = ?
                    )
                    """,
                    id,
                    role.name(),
                    id,
                    role.name());
        }
    }
}
