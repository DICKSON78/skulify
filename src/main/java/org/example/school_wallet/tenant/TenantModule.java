package org.example.school_wallet.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(
        name = "tenant_modules",
        uniqueConstraints = @UniqueConstraint(
                name = "tenant_modules_key",
                columnNames = {"tenant_id", "module_code"}))
@Getter
@Setter
@NoArgsConstructor
public class TenantModule extends TenantOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "module_code", nullable = false, length = 40)
    private ModuleCode moduleCode;

    @Column(nullable = false)
    private boolean enabled;

    public static TenantModule of(ModuleCode code, boolean enabled) {
        TenantModule module = new TenantModule();
        module.setModuleCode(code);
        module.setEnabled(enabled);
        return module;
    }
}
