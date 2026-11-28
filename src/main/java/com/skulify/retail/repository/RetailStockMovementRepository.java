package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailStockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailStockMovementRepository extends JpaRepository<RetailStockMovement, UUID> {
    List<RetailStockMovement> findByTenantIdAndProductIdOrderByCreatedAtDesc(UUID tenantId, UUID productId);

    List<RetailStockMovement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("select coalesce(sum(m.quantity), 0) from RetailStockMovement m where m.tenantId = :tenantId and m.productId = :productId")
    long sumQuantityByTenantIdAndProductId(@Param("tenantId") UUID tenantId, @Param("productId") UUID productId);

    @Query("""
            select m.productId, coalesce(sum(m.quantity), 0)
            from RetailStockMovement m
            where m.tenantId = :tenantId
            group by m.productId
            """)
    List<Object[]> sumQuantitiesByTenantId(@Param("tenantId") UUID tenantId);
}
