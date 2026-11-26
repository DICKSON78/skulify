package org.example.school_wallet.security;

import java.util.Set;
import org.example.school_wallet.tenant.TenantStatus;
import org.example.school_wallet.user.Role;

/**
 * Flat read model used by authentication and the JWT filter. It is loaded with
 * JDBC so that login can find a user before a tenant is known (Hibernate would
 * otherwise filter by the current tenant).
 */
public record AuthUser(
        java.util.UUID id,
        java.util.UUID tenantId,
        String username,
        String phone,
        String email,
        String fullName,
        String passwordHash,
        boolean enabled,
        boolean mustChangePassword,
        boolean mfaEnabled,
        String mfaSecret,
        TenantStatus tenantStatus,
        Set<Role> roles) {

    public boolean requiresMfa() {
        return roles.stream().anyMatch(Role::requiresMfa);
    }

    public boolean canAuthenticate() {
        return enabled && tenantStatus == TenantStatus.ACTIVE;
    }

    public AuthenticatedUser toPrincipal() {
        return new AuthenticatedUser(id, tenantId, username, fullName, roles, mustChangePassword);
    }
}
