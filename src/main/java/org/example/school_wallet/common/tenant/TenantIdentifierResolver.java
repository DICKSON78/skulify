package org.example.school_wallet.common.tenant;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to.
 *
 * <p>The platform tenant ({@link TenantContext#SYSTEM_TENANT_ID}) is treated as
 * the "root" tenant, so platform users can read and manage every school's data
 * without switching sessions.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.get();
        return tenantId != null ? tenantId : TenantContext.SYSTEM_TENANT_ID;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return TenantContext.SYSTEM_TENANT_ID.equals(tenantId);
    }
}
