package com.skulify.retail.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.school_wallet.operations.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailStoreItemRepository extends JpaRepository<StoreItem, UUID> {
    List<StoreItem> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StoreItem s where s.tenantId = :tenantId and s.id = :id")
    Optional<StoreItem> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
