package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailBatchRepository extends JpaRepository<RetailBatch, UUID> {
    List<RetailBatch> findByTenantIdAndProductIdOrderByReceivedAtDesc(UUID tenantId, UUID productId);

    Optional<RetailBatch> findByTenantIdAndProductIdAndBatchNumber(UUID tenantId, UUID productId, String batchNumber);

    @Query("""
            select b from RetailBatch b
            where b.tenantId = :tenantId
              and b.productId = :productId
              and b.quantityRemaining > 0
              and (b.expiryDate is null or b.expiryDate >= :today)
            order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate asc, b.receivedAt asc, b.id asc
            """)
    List<RetailBatch> findFefoBatches(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId,
            @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from RetailBatch b
            where b.tenantId = :tenantId
              and b.productId = :productId
              and b.quantityRemaining > 0
              and (b.expiryDate is null or b.expiryDate >= :today)
            order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate asc, b.receivedAt asc, b.id asc
            """)
    List<RetailBatch> lockFefoBatches(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId,
            @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RetailBatch b where b.tenantId = :tenantId and b.productId = :productId and b.quantityRemaining > 0 order by b.receivedAt desc, b.id desc")
    List<RetailBatch> lockAvailableBatches(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RetailBatch b where b.tenantId = :tenantId and b.id = :id")
    Optional<RetailBatch> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
