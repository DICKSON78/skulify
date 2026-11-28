package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetailSaleItemBatchId implements Serializable {

    @Column(name = "sale_item_id", nullable = false)
    private UUID saleItemId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetailSaleItemBatchId that)) return false;
        return Objects.equals(saleItemId, that.saleItemId) && Objects.equals(batchId, that.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(saleItemId, batchId);
    }
}
