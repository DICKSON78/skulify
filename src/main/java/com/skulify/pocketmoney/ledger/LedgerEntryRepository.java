package com.skulify.pocketmoney.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<PmLedgerEntry, UUID> {

    List<PmLedgerEntry> findByReferenceIdOrderByCreatedAtAsc(UUID referenceId);

    List<PmLedgerEntry> findByWalletIdOrderByCreatedAtAsc(UUID walletId);

    List<PmLedgerEntry> findByIdempotencyKeyOrderByCreatedAtAsc(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Wallet balance is defined as credits minus debits, derived from the ledger. */
    @Query(value = """
            select coalesce(sum(case when direction = 'CREDIT' then amount else -amount end), 0)
            from pm_ledger_entries
            where wallet_id = :walletId
            """, nativeQuery = true)
    long sumSignedByWallet(@Param("walletId") UUID walletId);

    /** Today's net card spending, with refunds reducing the total. */
    @Query("""
            select coalesce(sum(case when e.direction = :debit
                then e.amount else -e.amount end), 0)
            from PmLedgerEntry e
            where e.walletId = :walletId
              and e.createdAt >= :since
              and e.type in :types
            """)
    long sumDailySpend(
            @Param("walletId") UUID walletId,
            @Param("since") Instant since,
            @Param("types") List<LedgerEntryType> types,
            @Param("debit") Direction debit);
}
