package com.skulify.pocketmoney.purchase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmPurchaseRepository extends JpaRepository<PmPurchase, UUID> {

    Optional<PmPurchase> findByIdempotencyKey(String idempotencyKey);

    List<PmPurchase> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
