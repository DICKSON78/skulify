package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailSaleItemBatch;
import com.skulify.retail.entity.RetailSaleItemBatchId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailSaleItemBatchRepository extends JpaRepository<RetailSaleItemBatch, RetailSaleItemBatchId> {
    List<RetailSaleItemBatch> findByIdSaleItemId(UUID saleItemId);
}
