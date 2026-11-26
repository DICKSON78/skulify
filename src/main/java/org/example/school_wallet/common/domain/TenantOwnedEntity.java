package org.example.school_wallet.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base class for data owned by a school (tenant).
 *
 * <p>The {@link TenantId} mapping lets Hibernate do two things automatically:
 * stamp the current tenant on inserts and add a tenant predicate to every
 * load/query/update/delete. Platform (root) users are exempt so they can manage
 * all tenants.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantOwnedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}
