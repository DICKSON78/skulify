package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "retail_sale_items")
@Getter
@Setter
@NoArgsConstructor
public class RetailSaleItem extends TenantOwnedEntity {

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "line_total", nullable = false)
    private long lineTotal;

    @Column(name = "unit_cost", nullable = false)
    private long unitCost;

    @Column(name = "price_source", nullable = false, length = 30)
    private String priceSource = "CATALOG";
}
