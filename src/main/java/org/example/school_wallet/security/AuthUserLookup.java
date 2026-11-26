package org.example.school_wallet.security;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.tenant.TenantStatus;
import org.example.school_wallet.user.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC read model for authentication. This deliberately avoids Hibernate
 * multi-tenancy: login must resolve a user before a tenant is known.
 * The role lookup uses a portable query so both H2 (dev) and PostgreSQL (prod)
 * behave the same.
 */
@Repository
public class AuthUserLookup {

    private static final String BASE_SELECT = """
            select u.id, u.tenant_id, u.username, u.phone, u.email, u.full_name,
                   u.password_hash, u.enabled, u.must_change_password, u.mfa_enabled,
                   u.mfa_secret, t.status as tenant_status
              from app_users u
              join tenants t on t.id = u.tenant_id
            """;

    private final JdbcTemplate jdbc;

    public AuthUserLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthUser> findById(UUID userId) {
        return findOne(" where u.id = ?", userId);
    }

    public Optional<AuthUser> findByLogin(String login, String normalizedPhone) {
        return findOne(
                " where lower(u.username) = lower(?) or u.phone = ? or u.phone = ?",
                login,
                login,
                normalizedPhone);
    }

    public Optional<AuthUser> findByUsername(String username) {
        return findOne(" where lower(u.username) = lower(?)", username);
    }

    public Optional<AuthUser> findByPhone(String phone) {
        return findOne(" where u.phone = ?", phone);
    }

    private Optional<AuthUser> findOne(String where, Object... arguments) {
        return jdbc.query(BASE_SELECT + where, this::map, arguments).stream().findFirst();
    }

    private AuthUser map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID userId = rs.getObject("id", UUID.class);
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String raw : jdbc.queryForList(
                "select role from user_roles where user_id = ?", String.class, userId)) {
            if (raw != null) {
                roles.add(Role.valueOf(raw));
            }
        }
        String tenantStatus = rs.getString("tenant_status");

        return new AuthUser(
                userId,
                rs.getObject("tenant_id", UUID.class),
                rs.getString("username"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                rs.getBoolean("enabled"),
                rs.getBoolean("must_change_password"),
                rs.getBoolean("mfa_enabled"),
                rs.getString("mfa_secret"),
                tenantStatus == null ? TenantStatus.ACTIVE : TenantStatus.valueOf(tenantStatus),
                new HashSet<>(roles));
    }
}
