package com.skulify.pocketmoney.wallet;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<PmWallet, UUID> {

    Optional<PmWallet> findByStudentId(UUID studentId);

    List<PmWallet> findByTenantId(UUID tenantId);

    /** Pessimistic row lock: two simultaneous taps cannot both spend the same balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PmWallet w where w.id = :id")
    Optional<PmWallet> findByIdForUpdate(@Param("id") UUID id);
}
