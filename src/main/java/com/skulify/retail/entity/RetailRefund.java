package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Immutable
@Table(name = "retail_refunds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class RetailRefund extends TenantOwnedEntity {

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(nullable = false)
    private long amount;

    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetailRefundStatus status = RetailRefundStatus.COMPLETED;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
}
