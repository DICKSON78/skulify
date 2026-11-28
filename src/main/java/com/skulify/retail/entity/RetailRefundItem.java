package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;
import org.hibernate.annotations.Immutable;

/** Append-only record of the sale batches consumed by one refund. */
@Entity
@Immutable
@Table(name = "retail_refund_items")
@Getter
@Setter
@NoArgsConstructor
public class RetailRefundItem extends TenantOwnedEntity {

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "sale_item_id", nullable = false)
    private UUID saleItemId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private int quantity;
}
