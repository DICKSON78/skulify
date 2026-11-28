package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailSale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailSaleRepository extends JpaRepository<RetailSale, UUID> {
    Optional<RetailSale> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<RetailSale> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<RetailSale> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<RetailSale> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RetailSale s where s.tenantId = :tenantId and s.id = :id")
    Optional<RetailSale> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
