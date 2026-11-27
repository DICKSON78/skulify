package com.skulify.pocketmoney.ledger;

import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.RequestHashes;
import jakarta.persistence.EntityExistsException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The only entry point for money movements.
 *
 * <p>Ledger rows are inserted once and never changed. The cached wallet balance
 * is updated in the same transaction as the rows, while a pessimistic wallet
 * row lock makes simultaneous taps serialise deterministically.
 */
@Service
public class LedgerService {

    static final String CURRENCY = "TZS";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyService idempotencyKeys;
    private final LedgerPostingTransaction postingTransaction;

    public LedgerService(
            LedgerEntryRepository ledgerEntryRepository,
            IdempotencyKeyService idempotencyKeys,
            LedgerPostingTransaction postingTransaction) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeys = idempotencyKeys;
        this.postingTransaction = postingTransaction;
    }

    /** Lets callers return a stored result before re-checking mutable limits or fees. */
    public Optional<LedgerResult> replayIfPresent(LedgerPostingRequest request) {
        String requestHash = RequestHashes.forLedger(request);
        var key = idempotencyKeys.find(request.idempotencyKey());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        idempotencyKeys.verify(key.get(), request.scope(), requestHash);
        return Optional.of(replayResult(key.get()));
    }

    public LedgerResult post(LedgerPostingRequest request) {
        validateRequest(request);
        String requestHash = RequestHashes.forLedger(request);

        var replay = idempotencyKeys.find(request.idempotencyKey());
        if (replay.isPresent()) {
            idempotencyKeys.verify(replay.get(), request.scope(), requestHash);
            return replayResult(replay.get());
        }

        try {
            return postingTransaction.post(request, requestHash);
        } catch (DataIntegrityViolationException
                | EntityExistsException
                | ConstraintViolationException duplicate) {
            // The key was claimed by a concurrent request. Its transaction has
            // rolled back this one's posting attempt, so replaying the committed
            // result is safe.
            var committed = idempotencyKeys.find(request.idempotencyKey())
                    .orElseThrow(() -> duplicateRequest("idempotency key was claimed concurrently"));
            idempotencyKeys.verify(committed, request.scope(), requestHash);
            return replayResult(committed);
        }
    }

    private void validateRequest(LedgerPostingRequest request) {
        if (request == null) {
            throw invalid("posting request is required");
        }
        if (isBlank(request.idempotencyKey())) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "An idempotency key is required");
        }
        if (isBlank(request.scope())) {
            throw invalid("posting scope is required");
        }
        if (request.referenceId() == null || request.tenantId() == null) {
            throw invalid("reference and tenant are required");
        }
        if (request.postings() == null || request.postings().isEmpty()) {
            throw invalid("at least one posting is required");
        }

        long debits = 0L;
        long credits = 0L;
        for (LedgerPosting posting : request.postings()) {
            if (posting == null || posting.amount() <= 0) {
                throw invalid("every posting amount must be a positive whole shilling value");
            }
            if (posting.direction() == null || posting.type() == null) {
                throw invalid("posting direction and type are required");
            }
            if (isBlank(posting.accountCode()) || isBlank(posting.counterAccountCode())) {
                throw invalid("posting must name both accounts");
            }
            boolean walletPosting = posting.walletId() != null;
            if (walletPosting != AccountCode.STUDENT_WALLET.equals(posting.accountCode())) {
                throw invalid("wallet postings use STUDENT_WALLET and clearing postings have no wallet id");
            }
            if (!AccountCode.isKnown(posting.accountCode())
                    || !AccountCode.isKnown(posting.counterAccountCode())) {
                throw invalid("unknown ledger account code");
            }
            debits = addExact(debits, posting.direction() == Direction.DEBIT ? posting.amount() : 0L);
            credits = addExact(credits, posting.direction() == Direction.CREDIT ? posting.amount() : 0L);
        }
        if (debits != credits) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.UNBALANCED_POSTING,
                    "Debits and credits must balance: " + debits + " vs " + credits);
        }

        Set<String> namedAccounts = request.postings().stream()
                .map(LedgerPosting::accountCode)
                .collect(Collectors.toSet());
        if (request.postings().stream()
                .anyMatch(posting -> !namedAccounts.contains(posting.counterAccountCode()))) {
            throw invalid("every counter account must appear as an account in the posting set");
        }
    }

    private LedgerResult replayResult(PmIdempotencyKey key) {
        UUID referenceId = UUID.fromString(key.getResultRef());
        Map<UUID, Long> balances = new LinkedHashMap<>();
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (PmLedgerEntry entry : ledgerEntryRepository.findByReferenceIdOrderByCreatedAtAsc(referenceId)) {
            if (entry.getWalletId() != null && entry.getBalanceAfter() != null) {
                balances.put(entry.getWalletId(), entry.getBalanceAfter());
            }
            amounts.merge(
                    LedgerResult.amountKey(entry.getType(), entry.getAccountCode()),
                    entry.getAmount(),
                    Long::sum);
        }
        return new LedgerResult(referenceId, true, Map.copyOf(balances), Map.copyOf(amounts));
    }

    private PocketMoneyException duplicateRequest(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.DUPLICATE_REQUEST, detail);
    }

    private PocketMoneyException invalid(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.INVALID_AMOUNT, detail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw invalid("money amount overflow");
        }
    }
}
