package org.example.school_wallet.tenant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Schools a platform administrator may switch to for tenant-scoped work. */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolContextController {

    private final TenantRepository tenants;

    public SchoolContextController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        AuthenticatedUser user = CurrentUser.require();
        if (!user.isSuperAdmin()) {
            throw Errors.forbidden("FORBIDDEN", "Your role cannot switch schools");
        }
        return tenants.findByIdNot(TenantContext.SYSTEM_TENANT_ID).stream()
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .sorted(Comparator.comparing(Tenant::getName))
                .map(tenant -> Map.<String, Object>of(
                        "id", tenant.getId().toString(),
                        "code", tenant.getCode(),
                        "name", tenant.getName(),
                        "status", tenant.getStatus().name()))
                .toList();
    }
}
