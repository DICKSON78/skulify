package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailPurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailPurchaseOrderRepository extends JpaRepository<RetailPurchaseOrder, UUID> {
    Optional<RetailPurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);
    List<RetailPurchaseOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RetailPurchaseOrder o where o.id = :id and o.tenantId = :tenantId")
    Optional<RetailPurchaseOrder> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
