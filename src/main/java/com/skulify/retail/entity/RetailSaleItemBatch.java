package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_sale_item_batches")
@Getter
@Setter
@NoArgsConstructor
public class RetailSaleItemBatch {

    @EmbeddedId
    private RetailSaleItemBatchId id;

    @Column(nullable = false)
    private int quantity;
}
