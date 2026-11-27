package com.skulify.pocketmoney.card;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmCardRepository extends JpaRepository<PmCard, UUID> {

    Optional<PmCard> findByUidHash(String uidHash);

    List<PmCard> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<PmCard> findByWalletIdAndStatus(UUID walletId, PmCardStatus status);
}
