package com.skulify.pocketmoney.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<PmLedgerEntry, UUID> {

    List<PmLedgerEntry> findByReferenceIdOrderByCreatedAtAsc(UUID referenceId);

    List<PmLedgerEntry> findByIdempotencyKeyOrderByCreatedAtAsc(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Wallet balance is defined as credits minus debits, derived from the ledger. */
    @Query(value = """
            select coalesce(sum(case when direction = 'CREDIT' then amount else -amount end), 0)
            from pm_ledger_entries
            where wallet_id = :walletId
            """, nativeQuery = true)
    long sumSignedByWallet(@Param("walletId") UUID walletId);
}
