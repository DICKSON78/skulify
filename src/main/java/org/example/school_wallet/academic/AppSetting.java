package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "app_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "setting_key"}))
@Getter
@Setter
@NoArgsConstructor
public class AppSetting extends TenantOwnedEntity {

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", length = 1000)
    private String settingValue;

    @Column(length = 60)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
