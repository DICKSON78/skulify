package com.skulify.pocketmoney.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpResponse;
import com.skulify.pocketmoney.ledger.IdempotencyKeyRepository;
import com.skulify.pocketmoney.ledger.IdempotencyKeyService;
import com.skulify.pocketmoney.ledger.LedgerEntryRepository;
import com.skulify.pocketmoney.ledger.PmIdempotencyKey;
import com.skulify.pocketmoney.ledger.ReconciliationService;
import com.skulify.pocketmoney.topup.PmTopUp;
import com.skulify.pocketmoney.topup.PmTopUpRepository;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.TopUpResult;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.ApiException;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PocketMoneyApiServiceTest {

    @Mock private WalletRepository wallets;
    @Mock private StudentRepository students;
    @Mock private WalletService walletService;
    @Mock private LedgerEntryRepository ledgerEntries;
    @Mock private ReconciliationService reconciliation;
    @Mock private PmTopUpRepository topUps;
    @Mock private IdempotencyKeyRepository idempotencyKeys;
    @Mock private NotificationService notifications;

    private PocketMoneyApiService service;
    private UUID tenantId;
    private UUID guardianId;
    private UUID studentId;
    private UUID walletId;
    private Student student;
    private PmWallet wallet;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        guardianId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        student = new Student();
        student.setId(studentId);
        student.setTenantId(tenantId);
        student.setFirstName("Asha");
        student.setLastName("Mwangi");
        student.setGuardianUserId(guardianId);
        student.setGuardianPhone("+255700000001");

        wallet = new PmWallet();
        wallet.setId(walletId);
        wallet.setTenantId(tenantId);
        wallet.setStudentId(studentId);
        wallet.setCurrency("TZS");

        service = new PocketMoneyApiService(
                wallets,
                students,
                walletService,
                ledgerEntries,
                reconciliation,
                topUps,
                idempotencyKeys,
                new IdempotencyKeyService(idempotencyKeys),
                notifications,
                new ResourceAccessPolicy());
        authenticate(guardianId, Role.PARENT);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestTopUpCreatesPendingRecordAndApprovalPrompt() {
        stubChild();
        when(idempotencyKeys.findById("req-1")).thenReturn(Optional.empty());
        when(topUps.save(any(PmTopUp.class))).thenAnswer(call -> call.getArgument(0));

        TopUpResponse response = service.requestTopUp(
                new TopUpRequest(studentId, null, 10_000L, "USSD", "PTX-123"), "req-1");

        ArgumentCaptor<PmTopUp> topUpCaptor = ArgumentCaptor.forClass(PmTopUp.class);
        verify(topUps).save(topUpCaptor.capture());
        ArgumentCaptor<PmIdempotencyKey> keyCaptor = ArgumentCaptor.forClass(PmIdempotencyKey.class);
        verify(idempotencyKeys).save(keyCaptor.capture());
        verify(notifications).send(any(), eq("+255700000001"), eq("Approve Pocket Money top-up"),
                any(), eq("pocket-topups"), any());

        PmTopUp saved = topUpCaptor.getValue();
        assertEquals(PmTopUp.Status.PENDING, saved.getStatus());
        assertEquals(10_000L, saved.getGrossAmount());
        assertEquals(0L, saved.getFeeAmount());
        assertEquals(10_000L, saved.getNetAmount());
        assertEquals("req-1", saved.getIdempotencyKey());
        assertEquals("TOPUP_REQUEST", keyCaptor.getValue().getScope());
        assertEquals("PENDING", response.status());
        assertTrue(!response.replayed());
    }

    @Test
    void approvalPostsThroughLedgerWithDerivedKeyAndAppliesFee() {
        stubChild();
        PmTopUp pending = pendingTopUp();
        when(topUps.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(idempotencyKeys.findById("approve-1")).thenReturn(Optional.empty());
        when(walletService.topUp(any(TopUpCommand.class))).thenReturn(new TopUpResult(
                UUID.randomUUID(), 10_000L, 200L, 9_800L, 9_800L, false));
        when(topUps.save(any(PmTopUp.class))).thenAnswer(call -> call.getArgument(0));

        TopUpResponse response = service.decideTopUp(pending.getId(), true, "approve-1");

        ArgumentCaptor<TopUpCommand> commandCaptor = ArgumentCaptor.forClass(TopUpCommand.class);
        verify(walletService).topUp(commandCaptor.capture());
        assertTrue(!commandCaptor.getValue().idempotencyKey().equals("approve-1"));
        assertEquals(walletId, commandCaptor.getValue().walletId());
        assertEquals(10_000L, commandCaptor.getValue().grossAmount());
        assertEquals(PmTopUp.Status.SUCCESS, pending.getStatus());
        assertEquals(200L, pending.getFeeAmount());
        assertEquals(9_800L, pending.getNetAmount());
        assertEquals(9_800L, response.balanceAfter());
    }

    @Test
    void declineSkipsLedgerAndMarksTopUpFailed() {
        stubChild();
        PmTopUp pending = pendingTopUp();
        when(topUps.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(idempotencyKeys.findById("decline-1")).thenReturn(Optional.empty());
        when(topUps.save(any(PmTopUp.class))).thenAnswer(call -> call.getArgument(0));

        service.decideTopUp(pending.getId(), false, "decline-1");

        verify(walletService, never()).topUp(any());
        assertEquals(PmTopUp.Status.FAILED, pending.getStatus());
    }

    @Test
    void guardianCannotReadAnotherChildsWallet() {
        Student other = new Student();
        other.setId(UUID.randomUUID());
        other.setTenantId(tenantId);
        other.setGuardianUserId(UUID.randomUUID());
        when(students.findById(other.getId())).thenReturn(Optional.of(other));

        ApiException failure = assertThrows(ApiException.class, () -> service.walletForStudent(other.getId()));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatus());
    }

    @Test
    void directTopUpReplayDoesNotWriteTopUpTwice() {
        stubChild();
        PmTopUp existing = pendingTopUp();
        existing.setStatus(PmTopUp.Status.SUCCESS);
        existing.setFeeAmount(200L);
        existing.setNetAmount(9_800L);
        when(topUps.findByIdempotencyKey("direct-1")).thenReturn(Optional.of(existing));
        when(walletService.topUp(any(TopUpCommand.class))).thenReturn(new TopUpResult(
                UUID.randomUUID(), 10_000L, 200L, 9_800L, 9_800L, true));

        TopUpResponse response = service.topUp(
                new TopUpRequest(studentId, null, 10_000L, "USSD", null), "direct-1");

        verify(topUps, never()).save(any(PmTopUp.class));
        assertTrue(response.replayed());
        assertEquals("SUCCESS", response.status());
    }

    private void stubChild() {
        when(students.findById(studentId)).thenReturn(Optional.of(student));
        when(wallets.findByStudentId(studentId)).thenReturn(Optional.of(wallet));
        when(wallets.findById(walletId)).thenReturn(Optional.of(wallet));
    }

    private PmTopUp pendingTopUp() {
        PmTopUp row = new PmTopUp();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setParentUserId(guardianId);
        row.setWalletId(walletId);
        row.setGrossAmount(10_000L);
        row.setFeeAmount(0L);
        row.setNetAmount(10_000L);
        row.setCurrency("TZS");
        row.setProvider("USSD");
        row.setStatus(PmTopUp.Status.PENDING);
        row.setIdempotencyKey("req-1");
        row.setCreatedBy(guardianId);
        row.setCreatedAt(java.time.Instant.now());
        row.setUpdatedAt(java.time.Instant.now());
        return row;
    }

    private void authenticate(UUID userId, Role role) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId, tenantId, "guardian", "Guardian", Set.of(role), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }
}
