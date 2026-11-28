package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailSupplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailSupplierRepository extends JpaRepository<RetailSupplier, UUID> {
    Optional<RetailSupplier> findByIdAndTenantId(UUID id, UUID tenantId);
    List<RetailSupplier> findByTenantIdOrderByNameAsc(UUID tenantId);
}
