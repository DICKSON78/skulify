package com.skulify.pocketmoney.wallet;

import com.skulify.pocketmoney.PocketMoneyProperties;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.RequestHashes;
import com.skulify.pocketmoney.ledger.AccountCode;
import com.skulify.pocketmoney.ledger.Direction;
import com.skulify.pocketmoney.ledger.IdempotencyKeyService;
import com.skulify.pocketmoney.ledger.LedgerEntryType;
import com.skulify.pocketmoney.ledger.LedgerPosting;
import com.skulify.pocketmoney.ledger.LedgerPostingRequest;
import com.skulify.pocketmoney.ledger.LedgerResult;
import com.skulify.pocketmoney.ledger.LedgerService;
import com.skulify.pocketmoney.ledger.PmIdempotencyKey;
import jakarta.persistence.EntityExistsException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Wallet-level commands built on the append-only ledger. */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final WalletOpenTransaction walletOpenTransaction;
    private final IdempotencyKeyService idempotencyKeys;
    private final PocketMoneyProperties properties;

    public WalletService(
            WalletRepository walletRepository,
            LedgerService ledgerService,
            WalletOpenTransaction walletOpenTransaction,
            IdempotencyKeyService idempotencyKeys,
            PocketMoneyProperties properties) {
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.walletOpenTransaction = walletOpenTransaction;
        this.idempotencyKeys = idempotencyKeys;
        this.properties = properties;
    }

    public PmWallet openWallet(OpenWalletCommand command) {
        if (command == null || command.id() == null || command.tenantId() == null || command.studentId() == null) {
            throw invalid("wallet id, tenant and student are required");
        }
        requireKey(command.idempotencyKey());
        if (command.dailyLimit() < 0 || command.weeklyLimit() < 0
                || command.monthlyLimit() < 0 || command.perTxnLimit() < 0) {
            throw invalid("wallet limits cannot be negative");
        }

        String requestHash = RequestHashes.forWalletOpen(command);
        var replay = idempotencyKeys.find(command.idempotencyKey());
        if (replay.isPresent()) {
            idempotencyKeys.verify(replay.get(), "WALLET_OPEN", requestHash);
            return walletFor(replay.get());
        }

        try {
            return walletOpenTransaction.open(command, requestHash);
        } catch (DataIntegrityViolationException
                | EntityExistsException
                | ConstraintViolationException duplicate) {
            var committed = idempotencyKeys.find(command.idempotencyKey());
            if (committed.isPresent()) {
                idempotencyKeys.verify(committed.get(), "WALLET_OPEN", requestHash);
                return walletFor(committed.get());
            }
            // A different key raced to open the same student wallet; reuse it.
            PmWallet wallet = walletRepository.findByStudentId(command.studentId())
                    .orElseThrow(() -> duplicateRequest("wallet was claimed concurrently"));
            try {
                return walletOpenTransaction.claimExisting(command, requestHash, wallet.getId());
            } catch (DataIntegrityViolationException
                    | EntityExistsException
                    | ConstraintViolationException secondDuplicate) {
                PmIdempotencyKey claimed = idempotencyKeys.find(command.idempotencyKey())
                        .orElseThrow(() -> duplicateRequest("idempotency key was claimed concurrently"));
                idempotencyKeys.verify(claimed, "WALLET_OPEN", requestHash);
                return walletFor(claimed);
            }
        }
    }

    public TopUpResult topUp(TopUpCommand command) {
        if (command == null || command.walletId() == null || command.parentUserId() == null) {
            throw invalid("wallet and parent are required");
        }
        if (command.grossAmount() <= 0) {
            throw invalid("top-up amount must be positive");
        }
        requireKey(command.idempotencyKey());

        PmWallet wallet = walletById(command.walletId());
        Map<String, String> metadata = Map.of(
                "parentUserId", String.valueOf(command.parentUserId()),
                "businessFingerprint",
                        "walletId=" + wallet.getId() + "|grossAmount=" + command.grossAmount());
        UUID reference = deterministicReference("TOPUP", command.idempotencyKey());
        LedgerPostingRequest replayProbe = new LedgerPostingRequest(
                command.idempotencyKey(),
                "TOPUP",
                reference,
                wallet.getTenantId(),
                command.createdBy(),
                List.of(),
                metadata);
        var replay = ledgerService.replayIfPresent(replayProbe);
        if (replay.isPresent()) {
            return topUpResult(replay.get(), wallet, command.grossAmount(), 0L, command.grossAmount());
        }

        long feeAmount = feeFor(command.grossAmount());
        long netAmount = command.grossAmount() - feeAmount;
        if (netAmount <= 0) {
            throw invalid("top-up fee leaves no credit for the student wallet");
        }

        List<LedgerPosting> postings = new ArrayList<>();
        postings.add(LedgerPosting.clearingPosting(
                AccountCode.PARENT_FUNDS_IN,
                Direction.DEBIT,
                command.grossAmount(),
                LedgerEntryType.TOP_UP,
                AccountCode.STUDENT_WALLET));
        postings.add(LedgerPosting.walletPosting(
                wallet.getId(),
                Direction.CREDIT,
                netAmount,
                LedgerEntryType.TOP_UP,
                AccountCode.PARENT_FUNDS_IN));
        if (feeAmount > 0) {
            postings.add(LedgerPosting.clearingPosting(
                    AccountCode.PLATFORM_REVENUE,
                    Direction.CREDIT,
                    feeAmount,
                    LedgerEntryType.TOP_UP_FEE,
                    AccountCode.PARENT_FUNDS_IN));
        }

        LedgerResult result = ledgerService.post(new LedgerPostingRequest(
                command.idempotencyKey(),
                "TOPUP",
                reference,
                wallet.getTenantId(),
                command.createdBy(),
                postings,
                metadata));

        return topUpResult(result, wallet, command.grossAmount(), feeAmount, netAmount);
    }

    public PurchaseResult purchase(PurchaseCommand command) {
        if (command == null || command.walletId() == null) {
            throw invalid("wallet is required");
        }
        if (command.amount() <= 0) {
            throw invalid("purchase amount must be positive");
        }
        requireKey(command.idempotencyKey());

        PmWallet wallet = walletById(command.walletId());
        Map<String, String> metadata = purchaseMetadata(wallet, command);
        UUID reference = deterministicReference("PURCHASE", command.idempotencyKey());
        LedgerPostingRequest replayProbe = new LedgerPostingRequest(
                command.idempotencyKey(),
                "PURCHASE",
                reference,
                wallet.getTenantId(),
                command.createdBy(),
                List.of(),
                metadata);
        var replay = ledgerService.replayIfPresent(replayProbe);
        if (replay.isPresent()) {
            return purchaseResult(replay.get(), wallet, command.amount());
        }

        if (wallet.getPerTxnLimit() > 0 && command.amount() > wallet.getPerTxnLimit()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.LIMIT_EXCEEDED, "Purchase exceeds the per-transaction limit");
        }

        LedgerResult result = ledgerService.post(new LedgerPostingRequest(
                command.idempotencyKey(),
                "PURCHASE",
                reference,
                wallet.getTenantId(),
                command.createdBy(),
                List.of(
                        LedgerPosting.walletPosting(
                                wallet.getId(),
                                Direction.DEBIT,
                                command.amount(),
                                LedgerEntryType.PURCHASE,
                                AccountCode.SHOP_SETTLEMENT),
                        LedgerPosting.clearingPosting(
                                AccountCode.SHOP_SETTLEMENT,
                                Direction.CREDIT,
                                command.amount(),
                                LedgerEntryType.PURCHASE,
                                AccountCode.STUDENT_WALLET)),
                metadata));

        return purchaseResult(result, wallet, command.amount());
    }

    private TopUpResult topUpResult(
            LedgerResult result,
            PmWallet wallet,
            long fallbackGross,
            long fallbackFee,
            long fallbackNet) {
        long balanceAfter = result.balanceAfterByWallet().getOrDefault(wallet.getId(), wallet.getBalance());
        long postedGross = postedAmount(
                result, LedgerEntryType.TOP_UP, AccountCode.PARENT_FUNDS_IN, fallbackGross);
        long postedFee = result.replayed()
                ? result.amountsByTypeAndAccount().getOrDefault(
                        LedgerResult.amountKey(LedgerEntryType.TOP_UP_FEE, AccountCode.PLATFORM_REVENUE), 0L)
                : fallbackFee;
        long postedNet = postedAmount(
                result, LedgerEntryType.TOP_UP, AccountCode.STUDENT_WALLET, fallbackNet);
        return new TopUpResult(
                result.referenceId(), postedGross, postedFee, postedNet, balanceAfter, result.replayed());
    }

    private PurchaseResult purchaseResult(LedgerResult result, PmWallet wallet, long fallbackAmount) {
        long balanceAfter = result.balanceAfterByWallet().getOrDefault(wallet.getId(), wallet.getBalance());
        long postedAmount = postedAmount(
                result, LedgerEntryType.PURCHASE, AccountCode.STUDENT_WALLET, fallbackAmount);
        return new PurchaseResult(result.referenceId(), postedAmount, balanceAfter, result.replayed());
    }

    private Map<String, String> purchaseMetadata(PmWallet wallet, PurchaseCommand command) {
        Map<String, String> metadata = new HashMap<>();
        putIfPresent(metadata, "merchantId", command.merchantId());
        putIfPresent(metadata, "terminalId", command.terminalId());
        putIfPresent(metadata, "cardId", command.cardId());
        putIfPresent(metadata, "cardUidHash", command.cardUidHash());
        putIfPresent(metadata, "terminalCode", command.terminalCode());
        putIfPresent(metadata, "nonce", command.nonce());
        putIfPresent(metadata, "itemsFingerprint", command.itemsFingerprint());
        putIfPresent(metadata, "requestTimestamp", command.requestTimestamp());
        metadata.put("offline", String.valueOf(command.offline()));
        metadata.put("businessFingerprint", "walletId=" + wallet.getId()
                + "|amount=" + command.amount()
                + "|cardUidHash=" + nullToEmpty(command.cardUidHash())
                + "|terminalId=" + nullToEmpty(command.terminalId())
                + "|nonce=" + nullToEmpty(command.nonce())
                + "|offline=" + command.offline());
        return Map.copyOf(metadata);
    }

    private void putIfPresent(Map<String, String> metadata, String name, Object value) {
        if (value != null) {
            metadata.put(name, value.toString());
        }
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private long postedAmount(
            LedgerResult result, LedgerEntryType type, String accountCode, long fallback) {
        return result.amountsByTypeAndAccount()
                .getOrDefault(LedgerResult.amountKey(type, accountCode), fallback);
    }

    private long feeFor(long grossAmount) {
        BigDecimal percent = properties.getTopupFeePercent();
        if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("top-up fee percent must be between 0 and 1");
        }
        return BigDecimal.valueOf(grossAmount)
                .multiply(percent)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private UUID deterministicReference(String scope, String idempotencyKey) {
        return UUID.nameUUIDFromBytes((scope + "|" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    private PmWallet walletById(UUID id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + id));
    }

    private PmWallet walletFor(PmIdempotencyKey key) {
        return walletById(UUID.fromString(key.getResultRef()));
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "An idempotency key is required");
        }
    }

    private PocketMoneyException duplicateRequest(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.DUPLICATE_REQUEST, detail);
    }

    private PocketMoneyException invalid(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.INVALID_AMOUNT, detail);
    }
}
