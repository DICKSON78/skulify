package com.skulify.pocketmoney.topup;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PmTopUpRepository extends JpaRepository<PmTopUp, UUID> {

    Optional<PmTopUp> findByIdempotencyKey(String idempotencyKey);

    List<PmTopUp> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from PmTopUp row where row.id = :id")
    Optional<PmTopUp> findByIdForUpdate(@Param("id") UUID id);
}
