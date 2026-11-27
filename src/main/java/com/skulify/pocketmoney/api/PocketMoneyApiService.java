package com.skulify.pocketmoney.api;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.LedgerEntryResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.OpenWalletRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.ReconciliationResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.WalletLimitsRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.WalletResponse;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.RequestHashes;
import com.skulify.pocketmoney.ledger.IdempotencyKeyRepository;
import com.skulify.pocketmoney.ledger.IdempotencyKeyService;
import com.skulify.pocketmoney.ledger.LedgerEntryRepository;
import com.skulify.pocketmoney.ledger.PmLedgerEntry;
import com.skulify.pocketmoney.ledger.ReconciliationResult;
import com.skulify.pocketmoney.ledger.ReconciliationService;
import com.skulify.pocketmoney.topup.PmTopUp;
import com.skulify.pocketmoney.topup.PmTopUpRepository;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.TopUpResult;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger-backed Pocket Money commands. Callers never supply a tenant or a
 * wallet balance; both are resolved from the authenticated user and the child
 * they are allowed to access.
 */
@Service
@Transactional
public class PocketMoneyApiService {

    private static final String TOPUP_REQUEST_SCOPE = "TOPUP_REQUEST";
    private static final String TOPUP_DECISION_SCOPE = "TOPUP_DECISION";
    private static final String WALLET_LIMITS_SCOPE = "WALLET_LIMITS";
    private static final long DEFAULT_DAILY_LIMIT = 100_000L;
    private static final long DEFAULT_WEEKLY_LIMIT = 300_000L;
    private static final long DEFAULT_MONTHLY_LIMIT = 1_000_000L;
    private static final long DEFAULT_PER_TXN_LIMIT = 50_000L;

    private final WalletRepository wallets;
    private final StudentRepository students;
    private final WalletService walletService;
    private final LedgerEntryRepository ledgerEntries;
    private final ReconciliationService reconciliation;
    private final PmTopUpRepository topUps;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final IdempotencyKeyService idempotencyKeyService;
    private final NotificationService notifications;
    private final ResourceAccessPolicy accessPolicy;

    private record SavedTopUp(PmTopUp row, boolean created) {
    }

    public PocketMoneyApiService(
            WalletRepository wallets,
            StudentRepository students,
            WalletService walletService,
            LedgerEntryRepository ledgerEntries,
            ReconciliationService reconciliation,
            PmTopUpRepository topUps,
            IdempotencyKeyRepository idempotencyKeys,
            IdempotencyKeyService idempotencyKeyService,
            NotificationService notifications,
            ResourceAccessPolicy accessPolicy) {
        this.wallets = wallets;
        this.students = students;
        this.walletService = walletService;
        this.ledgerEntries = ledgerEntries;
        this.reconciliation = reconciliation;
        this.topUps = topUps;
        this.idempotencyKeys = idempotencyKeys;
        this.idempotencyKeyService = idempotencyKeyService;
        this.notifications = notifications;
        this.accessPolicy = accessPolicy;
    }

    public WalletResponse openWallet(OpenWalletRequest request, String idempotencyKey) {
        accessPolicy.require("pocket-cards", Action.CREATE);
        requireKey(idempotencyKey);
        if (request == null || request.studentId() == null) {
            throw Errors.badRequest("STUDENT_REQUIRED", "A student is required");
        }
        Student student = accessibleStudent(request.studentId());
        long daily = request.dailyLimit() == null ? DEFAULT_DAILY_LIMIT : request.dailyLimit();
        long weekly = request.weeklyLimit() == null ? DEFAULT_WEEKLY_LIMIT : request.weeklyLimit();
        long monthly = request.monthlyLimit() == null ? DEFAULT_MONTHLY_LIMIT : request.monthlyLimit();
        long perTransaction =
                request.perTransactionLimit() == null ? DEFAULT_PER_TXN_LIMIT : request.perTransactionLimit();
        if (daily < 0 || weekly < 0 || monthly < 0 || perTransaction < 0) {
            throw Errors.badRequest("INVALID_LIMITS", "Wallet limits cannot be negative");
        }

        UUID walletId = deterministicId("WALLET", student.getId().toString());
        PmWallet wallet = walletService.openWallet(new OpenWalletCommand(
                walletId,
                studentTenant(student),
                student.getId(),
                idempotencyKey,
                daily,
                weekly,
                monthly,
                perTransaction,
                currentUser().id()));
        return toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse walletForStudent(UUID studentId) {
        accessPolicy.require("pocket-cards", Action.READ);
        Student student = accessibleStudent(studentId);
        return toWalletResponse(walletFor(student));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> ledger(UUID walletId) {
        accessPolicy.require("pocket-transactions", Action.READ);
        PmWallet wallet = accessibleWallet(walletId);
        return ledgerEntries.findByWalletIdOrderByCreatedAtAsc(wallet.getId()).stream()
                .map(this::toLedgerEntryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopUpResponse> topUps(UUID studentId, UUID walletId) {
        accessPolicy.require("pocket-topups", Action.READ);
        if (studentId == null && walletId == null) {
            throw Errors.badRequest("CONTEXT_REQUIRED", "Choose a student or wallet");
        }
        PmWallet wallet = walletId == null
                ? walletFor(accessibleStudent(studentId))
                : accessibleWallet(walletId);
        return topUps.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(row -> toTopUpResponse(row, null, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse reconcile(UUID walletId) {
        accessPolicy.require("pocket-cards", Action.READ);
        PmWallet wallet = accessibleWallet(walletId);
        ReconciliationResult result = reconciliation.reconcile(wallet.getId());
        return new ReconciliationResponse(
                result.walletId(),
                result.cachedBalance(),
                result.ledgerBalance(),
                result.difference(),
                result.healthy());
    }

    public WalletResponse updateLimits(
            UUID walletId, WalletLimitsRequest request, String idempotencyKey) {
        if (!accessPolicy.can("pocket-cards", Action.UPDATE)
                && !accessPolicy.can("spending-limits", Action.UPDATE)) {
            throw forbidden("Your role cannot change spending limits");
        }
        requireKey(idempotencyKey);
        if (walletId == null || request == null) {
            throw Errors.badRequest("CONTEXT_REQUIRED", "A wallet and limits are required");
        }
        PmWallet wallet = accessibleWallet(walletId);
        long daily = request.dailyLimit() == null ? wallet.getDailyLimit() : request.dailyLimit();
        long weekly = request.weeklyLimit() == null ? wallet.getWeeklyLimit() : request.weeklyLimit();
        long monthly = request.monthlyLimit() == null ? wallet.getMonthlyLimit() : request.monthlyLimit();
        long perTransaction = request.perTransactionLimit() == null
                ? wallet.getPerTxnLimit()
                : request.perTransactionLimit();
        if (daily < 0 || weekly < 0 || monthly < 0 || perTransaction < 0) {
            throw Errors.badRequest("INVALID_LIMITS", "Wallet limits cannot be negative");
        }
        String hash = RequestHashes.forWalletLimits(
                wallet.getId(), daily, weekly, monthly, perTransaction, currentUser().id());
        var existing = idempotencyKeyService.find(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyKeyService.verify(existing.get(), WALLET_LIMITS_SCOPE, hash);
            return toWalletResponse(wallet);
        }
        idempotencyKeys.save(idempotencyKeyService.newKey(
                idempotencyKey, WALLET_LIMITS_SCOPE, hash, wallet.getId().toString()));
        wallet.setDailyLimit(daily);
        wallet.setWeeklyLimit(weekly);
        wallet.setMonthlyLimit(monthly);
        wallet.setPerTxnLimit(perTransaction);
        wallet.setUpdatedAt(Instant.now());
        return toWalletResponse(wallets.save(wallet));
    }

    /** Immediate top-up: money moves in the same transaction as the public record. */
    public TopUpResponse topUp(TopUpRequest request, String idempotencyKey) {
        accessPolicy.require("pocket-topups", Action.CREATE);
        requireKey(idempotencyKey);
        TopUpRequest valid = validate(request);
        AuthenticatedUser user = currentUser();
        Student student = resolveStudent(valid.studentId(), valid.walletId());
        PmWallet wallet = walletFor(student);
        UUID parentUserId = parentFor(student, user);

        TopUpResult result = walletService.topUp(new TopUpCommand(
                wallet.getId(),
                valid.amount(),
                parentUserId,
                idempotencyKey,
                user.id()));
        SavedTopUp saved = saveCompletedTopUp(
                idempotencyKey, wallet, student, parentUserId, valid, result, user.id());
        if (saved.created()) {
            notifyGuardian(
                    student,
                    saved.row().getId(),
                    "Pocket Money top-up received",
                    "TZS " + valid.amount() + " was added to " + student.getFirstName()
                            + "'s Pocket Money wallet.");
        }
        return toTopUpResponse(saved.row(), result.balanceAfter(), result.replayed());
    }

    /** Simulated USSD push: no money moves until the guardian approves. */
    public TopUpResponse requestTopUp(TopUpRequest request, String idempotencyKey) {
        accessPolicy.require("pocket-topups", Action.CREATE);
        requireKey(idempotencyKey);
        TopUpRequest valid = validate(request);
        AuthenticatedUser user = currentUser();
        Student student = resolveStudent(valid.studentId(), valid.walletId());
        PmWallet wallet = walletFor(student);
        UUID parentUserId = parentFor(student, user);
        UUID topUpId = deterministicId("TOPUP_REQUEST", idempotencyKey);
        String hash = RequestHashes.forTopUpRequest(
                studentTenant(student),
                student.getId(),
                parentUserId,
                valid.amount(),
                provider(valid.provider()),
                valid.providerReference());

        var existingKey = idempotencyKeyService.find(idempotencyKey);
        if (existingKey.isPresent()) {
            idempotencyKeyService.verify(existingKey.get(), TOPUP_REQUEST_SCOPE, hash);
            PmTopUp existing = topUps.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> Errors.conflict(
                            "DUPLICATE_REQUEST", "The top-up request could not be replayed"));
            return toTopUpResponse(existing, null, true);
        }

        idempotencyKeys.save(idempotencyKeyService.newKey(
                idempotencyKey, TOPUP_REQUEST_SCOPE, hash, topUpId.toString()));
        PmTopUp row = newTopUp(
                topUpId,
                studentTenant(student),
                parentUserId,
                wallet,
                valid,
                idempotencyKey,
                user.id(),
                PmTopUp.Status.PENDING,
                0L,
                0L);
        PmTopUp saved = topUps.save(row);
        notifyGuardian(
                student,
                saved.getId(),
                "Approve Pocket Money top-up",
                "Approve TZS " + valid.amount() + " for " + student.getFirstName()
                        + " in the Skulify parent app.");
        return toTopUpResponse(saved, null, false);
    }

    public TopUpResponse decideTopUp(UUID topUpId, boolean approved, String idempotencyKey) {
        accessPolicy.require("pocket-topups", Action.UPDATE);
        requireKey(idempotencyKey);
        if (topUpId == null) {
            throw Errors.badRequest("TOPUP_REQUIRED", "A top-up is required");
        }
        AuthenticatedUser user = currentUser();
        PmTopUp topUp = topUps.findByIdForUpdate(topUpId)
                .orElseThrow(() -> Errors.notFound("TOP_UP_NOT_FOUND", "Top-up not found"));
        Student student = accessibleStudent(resolveWallet(topUp.getWalletId()).getStudentId());
        String hash = RequestHashes.forTopUpDecision(topUp.getId(), approved);
        var decisionKey = idempotencyKeyService.find(idempotencyKey);
        if (decisionKey.isPresent()) {
            idempotencyKeyService.verify(decisionKey.get(), TOPUP_DECISION_SCOPE, hash);
        }

        if (topUp.getStatus() != PmTopUp.Status.PENDING) {
            boolean sameDecision = approved
                    ? topUp.getStatus() == PmTopUp.Status.SUCCESS
                    : topUp.getStatus() == PmTopUp.Status.FAILED;
            if (sameDecision) {
                return toTopUpResponse(topUp, null, true);
            }
            throw Errors.conflict("TOP_UP_DECIDED", "This top-up has already been decided");
        }

        if (decisionKey.isEmpty()) {
            idempotencyKeys.save(idempotencyKeyService.newKey(
                    idempotencyKey, TOPUP_DECISION_SCOPE, hash, topUp.getId().toString()));
        }

        if (!approved) {
            topUp.setStatus(PmTopUp.Status.FAILED);
            topUp.setUpdatedAt(Instant.now());
            PmTopUp saved = topUps.save(topUp);
            notifyGuardian(
                    student,
                    saved.getId(),
                    "Pocket Money top-up declined",
                    "The TZS " + saved.getGrossAmount() + " top-up was declined.");
            return toTopUpResponse(saved, null, false);
        }

        PmWallet wallet = resolveWallet(topUp.getWalletId());
        TopUpResult result = walletService.topUp(new TopUpCommand(
                wallet.getId(),
                topUp.getGrossAmount(),
                topUp.getParentUserId(),
                ledgerKey(idempotencyKey),
                user.id()));
        topUp.setStatus(PmTopUp.Status.SUCCESS);
        topUp.setFeeAmount(result.feeAmount());
        topUp.setNetAmount(result.netAmount());
        topUp.setUpdatedAt(Instant.now());
        PmTopUp saved = topUps.save(topUp);
        notifyGuardian(
                student,
                saved.getId(),
                "Pocket Money top-up approved",
                "TZS " + result.netAmount() + " was added to " + student.getFirstName()
                        + "'s Pocket Money wallet.");
        return toTopUpResponse(saved, result.balanceAfter(), result.replayed());
    }

    private TopUpRequest validate(TopUpRequest request) {
        if (request == null) {
            throw Errors.badRequest("TOP_UP_REQUIRED", "A top-up request is required");
        }
        if (request.amount() <= 0) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.INVALID_AMOUNT, "Top-up amount must be positive");
        }
        if (request.studentId() == null && request.walletId() == null) {
            throw Errors.badRequest("CONTEXT_REQUIRED", "Choose a student or wallet");
        }
        String providerReference = request.providerReference() == null
                ? null
                : request.providerReference().trim();
        return new TopUpRequest(
                request.studentId(),
                request.walletId(),
                request.amount(),
                provider(request.provider()),
                providerReference == null || providerReference.isBlank() ? null : providerReference);
    }

    private String provider(String value) {
        return value == null || value.isBlank() ? "USSD" : value.trim().toUpperCase();
    }

    private SavedTopUp saveCompletedTopUp(
            String idempotencyKey,
            PmWallet wallet,
            Student student,
            UUID parentUserId,
            TopUpRequest request,
            TopUpResult result,
            UUID createdBy) {
        var existing = topUps.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new SavedTopUp(existing.get(), false);
        }
        PmTopUp row = newTopUp(
                deterministicId("TOPUP", idempotencyKey),
                wallet.getTenantId(),
                parentUserId,
                wallet,
                request,
                idempotencyKey,
                createdBy,
                PmTopUp.Status.SUCCESS,
                result.feeAmount(),
                result.netAmount());
        return new SavedTopUp(topUps.save(row), true);
    }

    private PmTopUp newTopUp(
            UUID id,
            UUID tenantId,
            UUID parentUserId,
            PmWallet wallet,
            TopUpRequest request,
            String idempotencyKey,
            UUID createdBy,
            PmTopUp.Status status,
            long feeAmount,
            long netAmount) {
        Instant now = Instant.now();
        PmTopUp row = new PmTopUp();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setParentUserId(parentUserId);
        row.setWalletId(wallet.getId());
        row.setGrossAmount(request.amount());
        row.setFeeAmount(feeAmount);
        row.setNetAmount(netAmount == 0 ? request.amount() : netAmount);
        row.setCurrency(wallet.getCurrency());
        row.setProvider(provider(request.provider()));
        row.setProviderReference(request.providerReference());
        row.setStatus(status);
        row.setIdempotencyKey(idempotencyKey);
        row.setCreatedBy(createdBy);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private Student resolveStudent(UUID studentId, UUID walletId) {
        if (studentId == null && walletId == null) {
            throw Errors.badRequest("CONTEXT_REQUIRED", "Choose a student or wallet");
        }
        Student byId = studentId == null ? null : accessibleStudent(studentId);
        if (walletId == null) {
            return byId;
        }
        Student fromWallet = accessibleStudent(resolveWallet(walletId).getStudentId());
        if (byId != null && !byId.getId().equals(fromWallet.getId())) {
            throw Errors.badRequest("CONTEXT_MISMATCH", "Student and wallet do not match");
        }
        return fromWallet;
    }

    private PmWallet walletFor(Student student) {
        PmWallet wallet = wallets.findByStudentId(student.getId())
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "No Pocket Money wallet for this student"));
        if (!Objects.equals(wallet.getTenantId(), studentTenant(student))) {
            throw forbidden("Wallet belongs to another school");
        }
        return wallet;
    }

    private PmWallet resolveWallet(UUID walletId) {
        if (walletId == null) {
            throw Errors.badRequest("WALLET_REQUIRED", "A wallet is required");
        }
        PmWallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "Wallet not found"));
        requireTenant(wallet.getTenantId());
        accessibleStudent(wallet.getStudentId());
        return wallet;
    }

    private PmWallet accessibleWallet(UUID walletId) {
        return resolveWallet(walletId);
    }

    private Student accessibleStudent(UUID studentId) {
        if (studentId == null) {
            throw Errors.badRequest("STUDENT_REQUIRED", "A student is required");
        }
        Student student = students.findById(studentId)
                .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
        requireTenant(studentTenant(student));
        AuthenticatedUser user = currentUser();
        if (accessPolicy.isScoped(user)) {
            boolean linked = user.id().equals(student.getGuardianUserId())
                    || user.id().equals(student.getUserId());
            if (!linked) {
                throw forbidden("You can only access your own child's Pocket Money");
            }
        }
        return student;
    }

    private UUID parentFor(Student student, AuthenticatedUser user) {
        if (student.getGuardianUserId() != null) {
            return student.getGuardianUserId();
        }
        if (student.getUserId() != null) {
            return student.getUserId();
        }
        return user.id();
    }

    private void requireTenant(UUID tenantId) {
        AuthenticatedUser user = currentUser();
        if (!user.isSuperAdmin() && !Objects.equals(tenantId, user.tenantId())) {
            throw forbidden("Record belongs to another school");
        }
    }

    private UUID studentTenant(Student student) {
        return student.getTenantId() == null ? currentUser().tenantId() : student.getTenantId();
    }

    private AuthenticatedUser currentUser() {
        return CurrentUser.get()
                .orElseThrow(() -> Errors.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.IDEMPOTENCY_REQUIRED, "An Idempotency-Key header is required");
        }
    }

    private String ledgerKey(String idempotencyKey) {
        return deterministicId("TOPUP_LEDGER", idempotencyKey).toString();
    }

    private UUID deterministicId(String scope, String value) {
        return UUID.nameUUIDFromBytes((scope + "|" + value).getBytes(StandardCharsets.UTF_8));
    }

    private void notifyGuardian(Student student, UUID topUpId, String subject, String body) {
        notifications.send(
                NotificationOutbox.Channel.SMS,
                student.getGuardianPhone(),
                subject,
                body,
                "pocket-topups",
                topUpId);
    }

    private WalletResponse toWalletResponse(PmWallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getStudentId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus() == null ? "" : wallet.getStatus().name(),
                wallet.getDailyLimit(),
                wallet.getWeeklyLimit(),
                wallet.getMonthlyLimit(),
                wallet.getPerTxnLimit());
    }

    private TopUpResponse toTopUpResponse(PmTopUp row, Long balanceAfter, boolean replayed) {
        return new TopUpResponse(
                row.getId(),
                row.getWalletId(),
                row.getGrossAmount(),
                row.getFeeAmount(),
                row.getNetAmount(),
                row.getCurrency(),
                row.getProvider(),
                row.getProviderReference(),
                row.getStatus() == null ? "" : row.getStatus().name(),
                balanceAfter,
                replayed,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private LedgerEntryResponse toLedgerEntryResponse(PmLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getWalletId(),
                entry.getReferenceId(),
                entry.getType() == null ? "" : entry.getType().name(),
                entry.getDirection() == null ? "" : entry.getDirection().name(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getAccountCode(),
                entry.getCounterAccountCode(),
                entry.getIdempotencyKey(),
                entry.getCreatedAt());
    }

    private org.example.school_wallet.common.error.ApiException forbidden(String message) {
        return Errors.forbidden("FORBIDDEN", message);
    }
}
