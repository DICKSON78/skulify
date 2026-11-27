package com.skulify.pocketmoney.ledger;

import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The single database transaction that writes ledger rows and the cached wallet balance. */
@Component
public class LedgerPostingTransaction {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyService idempotencyKeys;
    private final EntityManager entityManager;

    public LedgerPostingTransaction(
            WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository,
            IdempotencyKeyService idempotencyKeys,
            EntityManager entityManager) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeys = idempotencyKeys;
        this.entityManager = entityManager;
    }

    @Transactional
    public LedgerResult post(LedgerPostingRequest request, String requestHash) {
        Map<UUID, PmWallet> lockedWallets = lockWallets(request.postings());
        Map<UUID, Long> balanceAfterByWallet = computeBalances(lockedWallets, request.postings());
        enforceDailyPurchaseLimits(lockedWallets, request.postings());

        // The key is committed in this same transaction. A duplicate-key failure
        // rolls back the ledger rows and the cached balance together, so a
        // concurrent replay can safely reuse the original result.
        // persist() always issues an INSERT for this assigned-id entity. A
        // concurrent claim therefore fails on the primary key instead of being
        // silently merged into an existing idempotency record.
        entityManager.persist(idempotencyKeys.newKey(
                request.idempotencyKey(),
                request.scope(),
                requestHash,
                request.referenceId().toString()));
        entityManager.flush();

        Map<String, Long> amountsByTypeAndAccount = new LinkedHashMap<>();
        Instant now = Instant.now();
        List<PmLedgerEntry> entries = new ArrayList<>();
        for (LedgerPosting posting : request.postings()) {
            PmLedgerEntry entry = new PmLedgerEntry();
            entry.setId(UUID.randomUUID());
            entry.setTenantId(request.tenantId());
            entry.setWalletId(posting.walletId());
            entry.setReferenceId(request.referenceId());
            entry.setType(posting.type());
            entry.setDirection(posting.direction());
            entry.setAmount(posting.amount());
            entry.setCurrency(LedgerService.CURRENCY);
            entry.setBalanceAfter(posting.walletId() == null ? null : balanceAfterByWallet.get(posting.walletId()));
            entry.setAccountCode(posting.accountCode());
            entry.setCounterAccountCode(posting.counterAccountCode());
            entry.setIdempotencyKey(request.idempotencyKey());
            entry.setCreatedBy(request.createdBy());
            entry.setCreatedAt(now);
            entries.add(entry);
            amountsByTypeAndAccount.merge(
                    LedgerResult.amountKey(posting.type(), posting.accountCode()),
                    posting.amount(),
                    Long::sum);
        }
        ledgerEntryRepository.saveAll(entries);

        for (PmWallet wallet : lockedWallets.values()) {
            wallet.setBalance(balanceAfterByWallet.get(wallet.getId()));
            wallet.setUpdatedAt(now);
        }
        ledgerEntryRepository.flush();

        return new LedgerResult(
                request.referenceId(),
                false,
                Map.copyOf(balanceAfterByWallet),
                Map.copyOf(amountsByTypeAndAccount));
    }

    private Map<UUID, PmWallet> lockWallets(List<LedgerPosting> postings) {
        Set<UUID> walletIds = postings.stream()
                .map(LedgerPosting::walletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, PmWallet> wallets = new LinkedHashMap<>();
        walletIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> {
                    PmWallet wallet = walletRepository
                            .findByIdForUpdate(id)
                            .orElseThrow(() -> new PocketMoneyException(
                                    PocketMoneyErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + id));
                    // The entity may already be managed from an earlier read in this
                    // transaction. Refresh under the lock so the balance used for the
                    // debit is the committed one, never a stale cached copy.
                    entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
                    if (wallet.getStatus() != WalletStatus.ACTIVE) {
                        throw new PocketMoneyException(
                                PocketMoneyErrorCode.WALLET_FROZEN, "Wallet is frozen: " + id);
                    }
                    if (!LedgerService.CURRENCY.equals(wallet.getCurrency())) {
                        throw new PocketMoneyException(
                                PocketMoneyErrorCode.CURRENCY_MISMATCH, "Wallet currency is not TZS");
                    }
                    wallets.put(id, wallet);
                });
        return wallets;
    }

    private void enforceDailyPurchaseLimits(
            Map<UUID, PmWallet> wallets, List<LedgerPosting> postings) {
        Instant since = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<LedgerEntryType> spendingTypes = List.of(
                LedgerEntryType.PURCHASE, LedgerEntryType.REFUND, LedgerEntryType.REVERSAL);
        for (PmWallet wallet : wallets.values()) {
            if (wallet.getDailyLimit() <= 0) {
                continue;
            }
            long pending = postings.stream()
                    .filter(posting -> wallet.getId().equals(posting.walletId()))
                    .filter(posting -> posting.type() == LedgerEntryType.PURCHASE)
                    .filter(posting -> posting.direction() == Direction.DEBIT)
                    .mapToLong(LedgerPosting::amount)
                    .sum();
            if (pending <= 0) {
                continue;
            }
            long spentToday = ledgerEntryRepository.sumDailySpend(
                    wallet.getId(), since, spendingTypes, Direction.DEBIT);
            if (addExact(spentToday, pending) > wallet.getDailyLimit()) {
                throw new PocketMoneyException(
                        PocketMoneyErrorCode.LIMIT_EXCEEDED,
                        "Purchase exceeds the daily spending limit");
            }
        }
    }

    private Map<UUID, Long> computeBalances(
            Map<UUID, PmWallet> wallets, List<LedgerPosting> postings) {
        Map<UUID, Long> running = new LinkedHashMap<>();
        wallets.forEach((id, wallet) -> running.put(id, wallet.getBalance()));
        for (LedgerPosting posting : postings) {
            if (posting.walletId() == null) {
                continue;
            }
            long current = running.get(posting.walletId());
            long next = posting.direction() == Direction.CREDIT
                    ? addExact(current, posting.amount())
                    : subtractExact(current, posting.amount());
            if (next < 0) {
                throw new PocketMoneyException(
                        PocketMoneyErrorCode.INSUFFICIENT_FUNDS,
                        "Wallet does not have enough balance for this movement");
            }
            running.put(posting.walletId(), next);
        }
        return running;
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw invalid("money amount overflow");
        }
    }

    private long subtractExact(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException exception) {
            throw invalid("money amount overflow");
        }
    }

    private PocketMoneyException invalid(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.INVALID_AMOUNT, detail);
    }
}
