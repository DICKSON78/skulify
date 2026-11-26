package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "store_items", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "sku"}))
@Getter
@Setter
@NoArgsConstructor
public class StoreItem extends TenantOwnedEntity {

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(length = 20)
    private String unit = "PIECE";

    @Column(length = 120)
    private String location;

    @Column(nullable = false)
    private boolean active = true;
}
