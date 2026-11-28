package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Natural key for a retail write idempotency record. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RetailWriteIdempotencyId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 40)
    private String scope;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
}
