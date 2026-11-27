package com.skulify.pocketmoney.api;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.CardResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.IssueCardRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.MerchantRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.MerchantResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TerminalRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TerminalResponse;
import com.skulify.pocketmoney.card.PmCard;
import com.skulify.pocketmoney.card.PmCardRepository;
import com.skulify.pocketmoney.card.PmCardStatus;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import com.skulify.pocketmoney.common.RequestHashes;
import com.skulify.pocketmoney.ledger.IdempotencyKeyRepository;
import com.skulify.pocketmoney.ledger.IdempotencyKeyService;
import com.skulify.pocketmoney.merchant.PmMerchant;
import com.skulify.pocketmoney.merchant.PmMerchantRepository;
import com.skulify.pocketmoney.terminal.PmTerminal;
import com.skulify.pocketmoney.terminal.PmTerminalRepository;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.WalletRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues the credentials used by sensors and keeps tenant scope on every record. */
@Service
@Transactional
public class PocketMoneyProvisioningService {

    private static final String CARD_SCOPE = "CARD_ISSUE";
    private static final String MERCHANT_SCOPE = "MERCHANT_REGISTER";
    private static final String TERMINAL_SCOPE = "TERMINAL_REGISTER";

    private final WalletRepository wallets;
    private final StudentRepository students;
    private final PmCardRepository cards;
    private final PmMerchantRepository merchants;
    private final PmTerminalRepository terminals;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final IdempotencyKeyService idempotencyKeyService;
    private final ResourceAccessPolicy accessPolicy;

    public PocketMoneyProvisioningService(
            WalletRepository wallets,
            StudentRepository students,
            PmCardRepository cards,
            PmMerchantRepository merchants,
            PmTerminalRepository terminals,
            IdempotencyKeyRepository idempotencyKeys,
            IdempotencyKeyService idempotencyKeyService,
            ResourceAccessPolicy accessPolicy) {
        this.wallets = wallets;
        this.students = students;
        this.cards = cards;
        this.merchants = merchants;
        this.terminals = terminals;
        this.idempotencyKeys = idempotencyKeys;
        this.idempotencyKeyService = idempotencyKeyService;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> cards() {
        accessPolicy.require("pocket-cards", ResourceAccessPolicy.Action.READ);
        AuthenticatedUser user = currentUser();
        List<PmCard> rows = user.isSuperAdmin()
                ? cards.findAll()
                : cards.findByTenantIdOrderByCreatedAtDesc(tenant(user));
        return rows.stream()
                .filter(card -> !accessPolicy.isScoped(user) || owns(user, card.getWalletId()))
                .map(this::toCardResponse)
                .toList();
    }

    public CardResponse issueCard(IssueCardRequest request, String idempotencyKey) {
        accessPolicy.require("pocket-cards", ResourceAccessPolicy.Action.CREATE);
        requireKey(idempotencyKey);
        if (request == null || request.studentId() == null) {
            throw Errors.badRequest("STUDENT_REQUIRED", "A student is required");
        }
        String uid = request.cardUid() == null ? "" : request.cardUid().trim();
        if (uid.isBlank()) {
            throw Errors.badRequest("CARD_UID_REQUIRED", "A card UID is required");
        }
        String uidHash = PocketMoneyHashes.identifier(uid);
        AuthenticatedUser user = currentUser();
        Student student = accessibleStudent(request.studentId(), user);
        PmWallet wallet = wallets.findByStudentId(student.getId())
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "No Pocket Money wallet for this student"));
        requireTenant(wallet.getTenantId(), user);
        String hash = RequestHashes.forCardIssue(
                studentTenant(student, user), student.getId(), wallet.getId(), uidHash, user.id());

        var existing = idempotencyKeyService.find(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyKeyService.verify(existing.get(), CARD_SCOPE, hash);
            return cardById(existing.get().getResultRef());
        }

        if (cards.findByUidHash(uidHash).isPresent()) {
            throw new PocketMoneyException(PocketMoneyErrorCode.DUPLICATE_REQUEST, "Card UID is already issued");
        }
        if (!cards.findByWalletIdAndStatus(wallet.getId(), PmCardStatus.ACTIVE).isEmpty()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.CARD_ALREADY_ACTIVE, "This student already has an active card");
        }

        UUID cardId = PocketMoneyHashes.deterministicId("CARD", idempotencyKey);
        Instant now = Instant.now();
        PmCard card = new PmCard();
        card.setId(cardId);
        card.setTenantId(studentTenant(student, user));
        card.setWalletId(wallet.getId());
        card.setUidHash(uidHash);
        card.setUidLabel(uid.length() > 60 ? uid.substring(0, 60) : uid);
        card.setStatus(PmCardStatus.ACTIVE);
        card.setIssuedAt(now);
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        try {
            idempotencyKeys.save(idempotencyKeyService.newKey(
                    idempotencyKey, CARD_SCOPE, hash, cardId.toString()));
            return toCardResponse(cards.save(card));
        } catch (DataIntegrityViolationException duplicate) {
            var committed = idempotencyKeyService.find(idempotencyKey)
                    .orElseThrow(() -> new PocketMoneyException(
                            PocketMoneyErrorCode.DUPLICATE_REQUEST, "Card issue was claimed concurrently"));
            idempotencyKeyService.verify(committed, CARD_SCOPE, hash);
            return cardById(committed.getResultRef());
        }
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> merchants() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        AuthenticatedUser user = currentUser();
        List<PmMerchant> rows = user.isSuperAdmin()
                ? merchants.findAll()
                : merchants.findByTenantIdOrderByCreatedAtDesc(tenant(user));
        return rows.stream().map(this::toMerchantResponse).toList();
    }

    public MerchantResponse registerMerchant(MerchantRequest request, String idempotencyKey) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.CREATE);
        requireKey(idempotencyKey);
        MerchantRequest valid = validate(request);
        AuthenticatedUser user = currentUser();
        UUID tenantId = tenant(user);
        String hash = RequestHashes.forMerchant(
                tenantId, valid.code(), valid.name(), valid.category(), user.id());

        var existing = idempotencyKeyService.find(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyKeyService.verify(existing.get(), MERCHANT_SCOPE, hash);
            return merchantById(existing.get().getResultRef());
        }
        if (merchants.findByTenantIdAndCode(tenantId, valid.code()).isPresent()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.DUPLICATE_REQUEST, "Merchant code already exists");
        }

        UUID merchantId = PocketMoneyHashes.deterministicId("MERCHANT", idempotencyKey);
        Instant now = Instant.now();
        PmMerchant row = new PmMerchant();
        row.setId(merchantId);
        row.setTenantId(tenantId);
        row.setCode(valid.code());
        row.setName(valid.name());
        row.setCategory(valid.category());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            idempotencyKeys.save(idempotencyKeyService.newKey(
                    idempotencyKey, MERCHANT_SCOPE, hash, merchantId.toString()));
            return toMerchantResponse(merchants.save(row));
        } catch (DataIntegrityViolationException duplicate) {
            var committed = idempotencyKeyService.find(idempotencyKey)
                    .orElseThrow(() -> new PocketMoneyException(
                            PocketMoneyErrorCode.DUPLICATE_REQUEST, "Merchant was claimed concurrently"));
            idempotencyKeyService.verify(committed, MERCHANT_SCOPE, hash);
            return merchantById(committed.getResultRef());
        }
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> terminals() {
        requireTerminalAccess(ResourceAccessPolicy.Action.READ);
        AuthenticatedUser user = currentUser();
        List<PmTerminal> rows = user.isSuperAdmin()
                ? terminals.findAll()
                : terminals.findByTenantIdOrderByCreatedAtDesc(tenant(user));
        return rows.stream().map(this::toTerminalResponse).toList();
    }

    public TerminalResponse registerTerminal(TerminalRequest request, String idempotencyKey) {
        requireTerminalAccess(ResourceAccessPolicy.Action.CREATE);
        requireKey(idempotencyKey);
        if (request == null || request.code() == null || request.code().isBlank()
                || request.merchantCode() == null || request.merchantCode().isBlank()
                || request.deviceKey() == null || request.deviceKey().isBlank()) {
            throw Errors.badRequest("TERMINAL_DETAILS_REQUIRED", "Merchant, code and device key are required");
        }
        String code = request.code().trim().toUpperCase();
        String merchantCode = request.merchantCode().trim().toUpperCase();
        String deviceKeyHash = PocketMoneyHashes.identifier(request.deviceKey());
        AuthenticatedUser user = currentUser();
        UUID tenantId = tenant(user);
        PmMerchant merchant = merchants.findByTenantIdAndCode(tenantId, merchantCode)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.MERCHANT_NOT_FOUND, "Merchant not found: " + merchantCode));
        String hash = RequestHashes.forTerminal(
                tenantId, merchant.getId(), code, deviceKeyHash, user.id());

        var existing = idempotencyKeyService.find(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyKeyService.verify(existing.get(), TERMINAL_SCOPE, hash);
            return terminalById(existing.get().getResultRef());
        }
        if (terminals.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.DUPLICATE_REQUEST, "Terminal code already exists");
        }

        UUID terminalId = PocketMoneyHashes.deterministicId("TERMINAL", idempotencyKey);
        Instant now = Instant.now();
        PmTerminal row = new PmTerminal();
        row.setId(terminalId);
        row.setTenantId(tenantId);
        row.setMerchantId(merchant.getId());
        row.setCode(code);
        row.setDeviceKeyHash(deviceKeyHash);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            idempotencyKeys.save(idempotencyKeyService.newKey(
                    idempotencyKey, TERMINAL_SCOPE, hash, terminalId.toString()));
            return toTerminalResponse(terminals.save(row));
        } catch (DataIntegrityViolationException duplicate) {
            var committed = idempotencyKeyService.find(idempotencyKey)
                    .orElseThrow(() -> new PocketMoneyException(
                            PocketMoneyErrorCode.DUPLICATE_REQUEST, "Terminal was claimed concurrently"));
            idempotencyKeyService.verify(committed, TERMINAL_SCOPE, hash);
            return terminalById(committed.getResultRef());
        }
    }

    private MerchantRequest validate(MerchantRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw Errors.badRequest("MERCHANT_DETAILS_REQUIRED", "Merchant code and name are required");
        }
        String category = request.category() == null || request.category().isBlank()
                ? "GENERAL"
                : request.category().trim().toUpperCase();
        return new MerchantRequest(
                request.code().trim().toUpperCase(), request.name().trim(), category);
    }

    private boolean owns(AuthenticatedUser user, UUID walletId) {
        return wallets.findById(walletId)
                .map(wallet -> students.findById(wallet.getStudentId())
                        .map(student -> user.id().equals(student.getGuardianUserId())
                                || user.id().equals(student.getUserId()))
                        .orElse(false))
                .orElse(false);
    }

    private Student accessibleStudent(UUID studentId, AuthenticatedUser user) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
        requireTenant(studentTenant(student, user), user);
        if (accessPolicy.isScoped(user)
                && !user.id().equals(student.getGuardianUserId())
                && !user.id().equals(student.getUserId())) {
            throw Errors.forbidden("FORBIDDEN", "You can only manage your own child's card");
        }
        return student;
    }

    private void requireTenant(UUID tenantId, AuthenticatedUser user) {
        if (!user.isSuperAdmin() && !Objects.equals(tenantId, tenant(user))) {
            throw Errors.forbidden("FORBIDDEN", "Record belongs to another school");
        }
    }

    private UUID studentTenant(Student student, AuthenticatedUser user) {
        return student.getTenantId() == null ? tenant(user) : student.getTenantId();
    }

    private UUID tenant(AuthenticatedUser user) {
        if (user.tenantId() == null) {
            throw Errors.forbidden("FORBIDDEN", "A school context is required");
        }
        return user.tenantId();
    }

    private void requireTerminalAccess(ResourceAccessPolicy.Action action) {
        if (!accessPolicy.can("store-terminals", action) && !accessPolicy.can("store-pos", action)) {
            throw Errors.forbidden("FORBIDDEN", "Your role cannot manage sensors");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "An Idempotency-Key header is required");
        }
    }

    private CardResponse cardById(String resultRef) {
        return cards.findById(UUID.fromString(resultRef))
                .map(this::toCardResponse)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.CARD_NOT_FOUND, "Card not found"));
    }

    private MerchantResponse merchantById(String resultRef) {
        return merchants.findById(UUID.fromString(resultRef))
                .map(this::toMerchantResponse)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.MERCHANT_NOT_FOUND, "Merchant not found"));
    }

    private TerminalResponse terminalById(String resultRef) {
        return terminals.findById(UUID.fromString(resultRef))
                .map(this::toTerminalResponse)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.TERMINAL_NOT_FOUND, "Terminal not found"));
    }

    private CardResponse toCardResponse(PmCard card) {
        return new CardResponse(
                card.getId(), walletStudent(card.getWalletId()), card.getWalletId(),
                card.getUidLabel(), card.getStatus().name(), card.getIssuedAt());
    }

    private UUID walletStudent(UUID walletId) {
        return wallets.findById(walletId).map(PmWallet::getStudentId).orElse(null);
    }

    private MerchantResponse toMerchantResponse(PmMerchant merchant) {
        return new MerchantResponse(
                merchant.getId(), merchant.getCode(), merchant.getName(), merchant.getCategory());
    }

    private TerminalResponse toTerminalResponse(PmTerminal terminal) {
        PmMerchant merchant = merchants.findById(terminal.getMerchantId()).orElse(null);
        return new TerminalResponse(
                terminal.getId(), terminal.getCode(),
                merchant == null ? "" : merchant.getCode(),
                terminal.getStatus().name(), terminal.getLastSeenAt());
    }

    private AuthenticatedUser currentUser() {
        return CurrentUser.get()
                .orElseThrow(() -> Errors.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }
}
