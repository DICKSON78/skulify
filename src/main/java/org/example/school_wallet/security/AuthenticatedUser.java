package org.example.school_wallet.security;

import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.user.Role;

/**
 * Authenticated principal stored in the Spring Security context.
 */
public record AuthenticatedUser(
        UUID id,
        UUID tenantId,
        String username,
        String fullName,
        Set<Role> roles,
        boolean mustChangePassword) {

    public boolean isSuperAdmin() {
        return roles.contains(Role.SUPER_ADMIN);
    }
}
