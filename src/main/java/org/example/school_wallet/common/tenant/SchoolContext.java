package org.example.school_wallet.common.tenant;

import java.util.UUID;
import org.example.school_wallet.common.error.Errors;

/**
 * Resolves the school a request is operating on.
 *
 * <p>Platform administrators may select a school with the {@code X-Tenant-Id}
 * header; {@link TenantFilter} puts that value in {@link TenantContext}. School
 * users always stay on their own tenant, and the reserved platform tenant is
 * never a valid school for business data.
 */
public final class SchoolContext {

    private SchoolContext() {
    }

    public static UUID require(UUID userTenantId, boolean superAdmin) {
        UUID selected = TenantContext.get();
        if (superAdmin && selected != null && !TenantContext.SYSTEM_TENANT_ID.equals(selected)) {
            return selected;
        }
        if (userTenantId == null || TenantContext.SYSTEM_TENANT_ID.equals(userTenantId)) {
            throw Errors.forbidden("SCHOOL_CONTEXT_REQUIRED", "Select a school to continue");
        }
        return userTenantId;
    }
}
