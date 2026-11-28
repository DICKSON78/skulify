package com.skulify.pocketmoney.api;

import com.skulify.pocketmoney.PocketMoneyProperties;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseResponse;
import com.skulify.pocketmoney.card.PmCard;
import com.skulify.pocketmoney.card.PmCardRepository;
import com.skulify.pocketmoney.card.PmCardStatus;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import com.skulify.pocketmoney.merchant.PmMerchant;
import com.skulify.pocketmoney.merchant.PmMerchantRepository;
import com.skulify.pocketmoney.purchase.PmPurchase;
import com.skulify.pocketmoney.purchase.PmPurchaseRepository;
import com.skulify.pocketmoney.purchase.PmPurchaseStatus;
import com.skulify.pocketmoney.terminal.PmTerminal;
import com.skulify.pocketmoney.terminal.PmTerminalRepository;
import com.skulify.pocketmoney.terminal.PmTerminalStatus;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.PurchaseCommand;
import com.skulify.pocketmoney.wallet.PurchaseResult;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.SchoolContext;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sensor payments. The card UID identifies the credential; the balance stays on the server. */
@Service
@Transactional
public class PocketMoneyPurchaseService {

    private final WalletRepository wallets;
    private final StudentRepository students;
    private final PmCardRepository cards;
    private final PmMerchantRepository merchants;
    private final PmTerminalRepository terminals;
    private final PmPurchaseRepository purchases;
    private final WalletService walletService;
    private final PocketMoneyProperties properties;
    private final ResourceAccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;

    public PocketMoneyPurchaseService(
            WalletRepository wallets,
            StudentRepository students,
            PmCardRepository cards,
            PmMerchantRepository merchants,
            PmTerminalRepository terminals,
            PmPurchaseRepository purchases,
            WalletService walletService,
            PocketMoneyProperties properties,
            ResourceAccessPolicy accessPolicy,
            ObjectMapper objectMapper) {
        this.wallets = wallets;
        this.students = students;
        this.cards = cards;
        this.merchants = merchants;
        this.terminals = terminals;
        this.purchases = purchases;
        this.walletService = walletService;
        this.properties = properties;
        this.accessPolicy = accessPolicy;
        this.objectMapper = objectMapper;
    }

    public PurchaseResponse purchase(PurchaseRequest request, String idempotencyKey) {
        requireWriteAccess();
        requireKey(idempotencyKey);
        PurchaseRequest valid = validate(request);
        AuthenticatedUser user = currentUser();
        UUID tenantId = tenant(user);

        PmCard card = cards.findByUidHash(PocketMoneyHashes.identifier(valid.cardUid()))
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.CARD_NOT_FOUND, "Card UID is not registered"));
        requireTenant(card.getTenantId(), tenantId);
        if (card.getStatus() != PmCardStatus.ACTIVE) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.CARD_NOT_ACTIVE, "Card is not active");
        }

        PmTerminal terminal = terminals.findByTenantIdAndCode(tenantId, valid.terminalCode().toUpperCase())
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.TERMINAL_NOT_FOUND, "Sensor terminal is not registered"));
        if (terminal.getStatus() != PmTerminalStatus.ACTIVE) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.TERMINAL_NOT_ACTIVE, "Sensor terminal is not active");
        }
        if (!PocketMoneyHashes.identifier(valid.deviceKey()).equals(terminal.getDeviceKeyHash())) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.DEVICE_KEY_INVALID, "Sensor device key is invalid");
        }
        PmMerchant merchant = merchants.findById(terminal.getMerchantId())
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.MERCHANT_NOT_FOUND, "Merchant is not registered"));

        PmWallet wallet = wallets.findById(card.getWalletId())
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "Wallet not found"));
        requireTenant(wallet.getTenantId(), tenantId);
        if (valid.studentId() != null && !valid.studentId().equals(wallet.getStudentId())) {
            throw Errors.badRequest(
                    "CARD_STUDENT_MISMATCH", "Card does not belong to the selected student");
        }
        if (valid.offline() && valid.amount() > properties.getOfflineCap()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.LIMIT_EXCEEDED, "Offline purchase exceeds the offline cap");
        }

        Instant requestTime = valid.requestTimestamp() == null ? Instant.now() : valid.requestTimestamp();
        String items = valid.items() == null ? "" : valid.items().trim();
        String itemsFingerprint = PocketMoneyHashes.deterministicId("ITEMS", items).toString();
        PurchaseResult result = walletService.purchase(new PurchaseCommand(
                wallet.getId(),
                valid.amount(),
                merchant.getId(),
                terminal.getId(),
                idempotencyKey,
                user.id(),
                card.getId(),
                card.getUidHash(),
                terminal.getCode(),
                valid.nonce(),
                valid.offline(),
                requestTime,
                itemsFingerprint));

        var existing = purchases.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }
        if (result.replayed()) {
            // A concurrent replay cannot see the first transaction's purchase row,
            // but the ledger already returned the authoritative stored result.
            return replayResponse(
                    idempotencyKey, wallet, result, PmPurchaseStatus.APPROVED, items);
        }

        UUID purchaseId = PocketMoneyHashes.deterministicId("PURCHASE", idempotencyKey);
        PmPurchase row = new PmPurchase();
        row.setId(purchaseId);
        row.setTenantId(tenantId);
        row.setWalletId(wallet.getId());
        row.setMerchantId(merchant.getId());
        row.setTerminalId(terminal.getId());
        row.setAmount(valid.amount());
        row.setCurrency(wallet.getCurrency());
        row.setBalanceAfter(result.balanceAfter());
        row.setStatus(PmPurchaseStatus.APPROVED);
        row.setItems(items.isBlank() ? null : jsonItems(items));
        row.setOffline(valid.offline());
        row.setNonce(valid.nonce());
        row.setRequestTimestamp(requestTime);
        row.setIdempotencyKey(idempotencyKey);
        row.setCreatedAt(Instant.now());

        terminal.setLastSeenAt(requestTime);
        terminal.setUpdatedAt(Instant.now());
        return toResponse(purchases.save(row), result.replayed());
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> purchases(UUID studentId) {
        accessPolicy.require("pocket-transactions", ResourceAccessPolicy.Action.READ);
        AuthenticatedUser user = currentUser();
        List<PmWallet> scope;
        if (studentId != null) {
            Student student = students.findById(studentId)
                    .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
            requireTenant(studentTenant(student, user), user);
            requireScopedStudent(student, user);
            scope = List.of(wallets.findByStudentId(studentId)
                    .orElseThrow(() -> new PocketMoneyException(
                            PocketMoneyErrorCode.WALLET_NOT_FOUND, "No Pocket Money wallet for this student")));
        } else if (user.isSuperAdmin()) {
            scope = wallets.findAll();
        } else {
            scope = wallets.findByTenantId(tenant(user));
            if (accessPolicy.isScoped(user)) {
                scope = scope.stream().filter(wallet -> isOwnWallet(wallet, user)).toList();
            }
        }
        return scope.stream()
                .flatMap(wallet -> purchases.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream())
                .map(row -> toResponse(row, false))
                .toList();
    }

    private PurchaseRequest validate(PurchaseRequest request) {
        if (request == null) {
            throw Errors.badRequest("PURCHASE_REQUIRED", "A purchase request is required");
        }
        if (request.amount() <= 0) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.INVALID_AMOUNT, "Purchase amount must be positive");
        }
        if (request.cardUid() == null || request.cardUid().isBlank()) {
            throw Errors.badRequest("CARD_UID_REQUIRED", "A card UID is required");
        }
        if (request.terminalCode() == null || request.terminalCode().isBlank()) {
            throw Errors.badRequest("TERMINAL_REQUIRED", "A terminal code is required");
        }
        if (request.deviceKey() == null || request.deviceKey().isBlank()) {
            throw Errors.badRequest("DEVICE_KEY_REQUIRED", "A sensor device key is required");
        }
        if (request.nonce() == null || request.nonce().isBlank()) {
            throw Errors.badRequest("NONCE_REQUIRED", "A terminal nonce is required");
        }
        return request;
    }

    private String jsonItems(String items) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("description", items));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store purchase items", exception);
        }
    }

    private void requireWriteAccess() {
        if (!accessPolicy.can("store-pos", ResourceAccessPolicy.Action.CREATE)
                && !accessPolicy.can("pocket-transactions", ResourceAccessPolicy.Action.CREATE)) {
            throw Errors.forbidden("FORBIDDEN", "Your role cannot record sensor payments");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "An Idempotency-Key header is required");
        }
    }

    private void requireTenant(UUID tenantId, UUID expected) {
        if (!Objects.equals(tenantId, expected)) {
            throw Errors.forbidden("FORBIDDEN", "Card belongs to another school");
        }
    }

    private void requireTenant(UUID tenantId, AuthenticatedUser user) {
        if (!user.isSuperAdmin() && !Objects.equals(tenantId, tenant(user))) {
            throw Errors.forbidden("FORBIDDEN", "Record belongs to another school");
        }
    }

    private void requireScopedStudent(Student student, AuthenticatedUser user) {
        if (accessPolicy.isScoped(user)
                && !user.id().equals(student.getGuardianUserId())
                && !user.id().equals(student.getUserId())) {
            throw Errors.forbidden("FORBIDDEN", "You can only read your own child's purchases");
        }
    }

    private boolean isOwnWallet(PmWallet wallet, AuthenticatedUser user) {
        return students.findById(wallet.getStudentId())
                .map(student -> user.id().equals(student.getGuardianUserId())
                        || user.id().equals(student.getUserId()))
                .orElse(false);
    }

    private UUID tenant(AuthenticatedUser user) {
        return SchoolContext.require(user.tenantId(), user.isSuperAdmin());
    }

    private UUID studentTenant(Student student, AuthenticatedUser user) {
        return student.getTenantId() == null ? tenant(user) : student.getTenantId();
    }

    private AuthenticatedUser currentUser() {
        return CurrentUser.get()
                .orElseThrow(() -> Errors.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }

    private PurchaseResponse replayResponse(
            String idempotencyKey,
            PmWallet wallet,
            PurchaseResult result,
            PmPurchaseStatus status,
            String items) {
        return new PurchaseResponse(
                PocketMoneyHashes.deterministicId("PURCHASE", idempotencyKey),
                wallet.getId(),
                result.referenceId(),
                result.amount(),
                wallet.getCurrency(),
                status.name(),
                result.balanceAfter(),
                true,
                itemDescription(items),
                Instant.now());
    }

    private PurchaseResponse toResponse(PmPurchase row, boolean replayed) {
        return new PurchaseResponse(
                row.getId(),
                row.getWalletId(),
                PocketMoneyHashes.deterministicId("PURCHASE", row.getIdempotencyKey()),
                row.getAmount(),
                row.getCurrency(),
                row.getStatus().name(),
                row.getBalanceAfter() == null ? 0L : row.getBalanceAfter(),
                replayed,
                itemDescription(row.getItems()),
                row.getCreatedAt());
    }

    private String itemDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readTree(raw).path("description").asText(raw);
        } catch (JsonProcessingException exception) {
            return raw;
        }
    }
}
