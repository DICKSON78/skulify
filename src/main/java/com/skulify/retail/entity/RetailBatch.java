package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "retail_batches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "product_id", "batch_number"}))
@Getter
@Setter
@NoArgsConstructor
public class RetailBatch extends TenantOwnedEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_remaining", nullable = false)
    private int quantityRemaining;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(length = 120)
    private String reference;
}
