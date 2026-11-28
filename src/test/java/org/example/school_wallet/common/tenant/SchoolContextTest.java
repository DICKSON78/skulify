package org.example.school_wallet.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.example.school_wallet.common.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SchoolContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void superAdminUsesSelectedSchool() {
        UUID schoolId = UUID.randomUUID();
        TenantContext.set(schoolId);

        assertEquals(schoolId, SchoolContext.require(TenantContext.SYSTEM_TENANT_ID, true));
    }

    @Test
    void superAdminWithoutSelectedSchoolIsRejected() {
        TenantContext.set(TenantContext.SYSTEM_TENANT_ID);

        ApiException error = assertThrows(
                ApiException.class,
                () -> SchoolContext.require(TenantContext.SYSTEM_TENANT_ID, true));
        assertEquals("SCHOOL_CONTEXT_REQUIRED", error.getCode());
    }

    @Test
    void schoolUserStaysOnOwnTenantEvenWhenHeaderIsPresent() {
        UUID ownTenant = UUID.randomUUID();
        TenantContext.set(UUID.randomUUID());

        assertEquals(ownTenant, SchoolContext.require(ownTenant, false));
    }
}
