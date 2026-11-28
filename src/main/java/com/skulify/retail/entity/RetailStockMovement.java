package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Immutable
@Table(name = "retail_stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class RetailStockMovement extends TenantOwnedEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetailStockMovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(length = 120)
    private String reference;

    @Column(length = 255)
    private String reason;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "performed_by")
    private UUID performedBy;
}
