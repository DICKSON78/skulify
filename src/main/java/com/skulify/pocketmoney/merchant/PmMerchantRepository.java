package com.skulify.pocketmoney.merchant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmMerchantRepository extends JpaRepository<PmMerchant, UUID> {

    Optional<PmMerchant> findByTenantIdAndCode(UUID tenantId, String code);

    List<PmMerchant> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
