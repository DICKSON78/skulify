package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailRefund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailRefundRepository extends JpaRepository<RetailRefund, UUID> {
    Optional<RetailRefund> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<RetailRefund> findByTenantIdAndSaleIdOrderByCreatedAtAsc(UUID tenantId, UUID saleId);

    @Query("select coalesce(sum(r.amount), 0) from RetailRefund r where r.tenantId = :tenantId and r.saleId = :saleId and r.status = 'COMPLETED'")
    long sumCompletedAmountBySale(@Param("tenantId") UUID tenantId, @Param("saleId") UUID saleId);
}
