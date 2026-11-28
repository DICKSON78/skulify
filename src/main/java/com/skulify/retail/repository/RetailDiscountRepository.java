package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailDiscount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailDiscountRepository extends JpaRepository<RetailDiscount, UUID> {
    Optional<RetailDiscount> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    List<RetailDiscount> findByTenantIdOrderByCodeAsc(UUID tenantId);
}
