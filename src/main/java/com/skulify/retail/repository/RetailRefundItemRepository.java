package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailRefundItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailRefundItemRepository extends JpaRepository<RetailRefundItem, UUID> {

    List<RetailRefundItem> findBySaleItemId(UUID saleItemId);

    List<RetailRefundItem> findBySaleId(UUID saleId);

    @Query("""
            select coalesce(sum(i.quantity), 0) from RetailRefundItem i
            where i.tenantId = :tenantId
              and i.saleItemId = :saleItemId
              and i.batchId = :batchId
            """)
    int sumReturnedByBatch(
            @Param("tenantId") UUID tenantId,
            @Param("saleItemId") UUID saleItemId,
            @Param("batchId") UUID batchId);
}
