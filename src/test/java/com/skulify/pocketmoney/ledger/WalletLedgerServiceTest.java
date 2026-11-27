package com.skulify.pocketmoney.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.school_wallet.SchoolWalletApplication;
import com.skulify.pocketmoney.PocketMoneyProperties;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.PurchaseCommand;
import com.skulify.pocketmoney.wallet.PurchaseResult;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.TopUpResult;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = SchoolWalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WalletLedgerServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private PocketMoneyProperties properties;

    @Autowired
    private LedgerService ledgerService;

    @Test
    void balanceAlwaysEqualsLedgerSumAfterTopUpAndPurchase() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        UUID parent = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        TopUpResult topUp = walletService.topUp(new TopUpCommand(
                wallet.getId(), 10_000L, parent, key("topup-balance"), actor));
        assertEquals(200L, topUp.feeAmount());
        assertEquals(9_800L, topUp.netAmount());

        PurchaseResult purchase = walletService.purchase(new PurchaseCommand(
                wallet.getId(), 3_000L, UUID.randomUUID(), UUID.randomUUID(), key("purchase-balance"), actor));
        assertEquals(6_800L, purchase.balanceAfter());

        assertEquals(6_800L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
        assertEquals(6_800L, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
        assertEquals(3, ledgerEntryRepository.findByReferenceIdOrderByCreatedAtAsc(topUp.referenceId()).size());
        assertEquals(
                2, ledgerEntryRepository.findByReferenceIdOrderByCreatedAtAsc(purchase.referenceId()).size());
        assertTrue(reconciliationService.reconcile(wallet.getId()).healthy());
    }

    @Test
    void replayingTheSameIdempotencyKeyTenTimesNeverMovesMoneyTwice() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        UUID parent = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        String topUpKey = key("topup-replay");

        TopUpResult firstTopUp = walletService.topUp(
                new TopUpCommand(wallet.getId(), 5_000L, parent, topUpKey, actor));
        for (int attempt = 0; attempt < 9; attempt++) {
            TopUpResult replay = walletService.topUp(
                    new TopUpCommand(wallet.getId(), 5_000L, parent, topUpKey, actor));
            assertTrue(replay.replayed());
            assertEquals(firstTopUp.referenceId(), replay.referenceId());
            assertEquals(firstTopUp.balanceAfter(), replay.balanceAfter());
        }
        assertEquals(3, ledgerEntryRepository.findByReferenceIdOrderByCreatedAtAsc(firstTopUp.referenceId()).size());

        String purchaseKey = key("purchase-replay");
        UUID merchant = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        PurchaseResult firstPurchase = walletService.purchase(new PurchaseCommand(
                wallet.getId(), 1_000L, merchant, terminal, purchaseKey, actor));
        for (int attempt = 0; attempt < 9; attempt++) {
            PurchaseResult replay = walletService.purchase(new PurchaseCommand(
                    wallet.getId(), 1_000L, merchant, terminal, purchaseKey, actor));
            assertTrue(replay.replayed());
            assertEquals(firstPurchase.referenceId(), replay.referenceId());
        }
        assertEquals(
                2,
                ledgerEntryRepository.findByReferenceIdOrderByCreatedAtAsc(firstPurchase.referenceId()).size());
        assertEquals(3_900L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
        assertEquals(3_900L, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
    }

    @Test
    void idempotencyKeyCannotBeReusedForADifferentOperationOrAmount() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        String topUpKey = key("topup-conflict");

        walletService.topUp(new TopUpCommand(
                wallet.getId(), 5_000L, UUID.randomUUID(), topUpKey, UUID.randomUUID()));

        PocketMoneyException purchaseConflict = assertThrows(
                PocketMoneyException.class,
                () -> walletService.purchase(new PurchaseCommand(
                        wallet.getId(),
                        1_000L,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        topUpKey,
                        UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.IDEMPOTENCY_CONFLICT, purchaseConflict.code());

        PocketMoneyException amountConflict = assertThrows(
                PocketMoneyException.class,
                () -> walletService.topUp(new TopUpCommand(
                        wallet.getId(), 6_000L, UUID.randomUUID(), topUpKey, UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.IDEMPOTENCY_CONFLICT, amountConflict.code());
        assertEquals(4_900L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
        assertEquals(4_900L, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
    }

    @Test
    void purchaseKeyCannotBeReusedForADifferentMerchantOrTerminal() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        walletService.topUp(new TopUpCommand(
                wallet.getId(), 10_000L, UUID.randomUUID(), key("topup-merchant"), UUID.randomUUID()));
        String purchaseKey = key("purchase-merchant");
        UUID merchant = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();

        walletService.purchase(new PurchaseCommand(
                wallet.getId(), 1_000L, merchant, terminal, purchaseKey, UUID.randomUUID()));

        PocketMoneyException conflict = assertThrows(
                PocketMoneyException.class,
                () -> walletService.purchase(new PurchaseCommand(
                        wallet.getId(),
                        1_000L,
                        UUID.randomUUID(),
                        terminal,
                        purchaseKey,
                        UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());
        assertEquals(8_800L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
    }

    @Test
    void replayingATopUpUsesTheOriginalFeeAfterConfigChanges() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        TopUpCommand command = new TopUpCommand(
                wallet.getId(), 10_000L, UUID.randomUUID(), key("topup-fee-replay"), UUID.randomUUID());
        TopUpResult original = walletService.topUp(command);
        assertEquals(200L, original.feeAmount());

        properties.setTopupFeePercent(new BigDecimal("0.10"));
        try {
            TopUpResult replay = walletService.topUp(command);
            assertTrue(replay.replayed());
            assertEquals(original.referenceId(), replay.referenceId());
            assertEquals(200L, replay.feeAmount());
            assertEquals(9_800L, replay.netAmount());
        } finally {
            properties.setTopupFeePercent(new BigDecimal("0.02"));
        }
        assertEquals(9_800L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
    }

    @Test
    void replayingAPurchaseAfterThePerTxnLimitShrinksStillReturnsStoredResult() {
        PmWallet wallet = openWallet(100_000L, 5_000L);
        walletService.topUp(new TopUpCommand(
                wallet.getId(), 10_000L, UUID.randomUUID(), key("topup-limit-replay"), UUID.randomUUID()));
        UUID merchant = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        PurchaseCommand command = new PurchaseCommand(
                wallet.getId(), 1_000L, merchant, terminal, key("purchase-limit-replay"), actor);
        PurchaseResult original = walletService.purchase(command);

        PmWallet stored = walletRepository.findById(wallet.getId()).orElseThrow();
        stored.setPerTxnLimit(100L);
        walletRepository.saveAndFlush(stored);
        try {
            PurchaseResult replay = walletService.purchase(command);
            assertTrue(replay.replayed());
            assertEquals(original.referenceId(), replay.referenceId());
            assertEquals(1_000L, replay.amount());
        } finally {
            stored.setPerTxnLimit(5_000L);
            walletRepository.saveAndFlush(stored);
        }
        assertEquals(8_800L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
    }

    @Test
    void walletOpenKeyCannotBeReusedByADifferentActor() {
        UUID tenant = newTenant("OPN-" + shortId());
        UUID student = newStudent(tenant, "OPN-STU-" + shortId());
        String openKey = key("wallet-open-actor");
        walletService.openWallet(new OpenWalletCommand(
                UUID.randomUUID(), tenant, student, openKey, 10_000L, 10_000L, 10_000L, 10_000L, UUID.randomUUID()));

        PocketMoneyException conflict = assertThrows(
                PocketMoneyException.class,
                () -> walletService.openWallet(new OpenWalletCommand(
                        UUID.randomUUID(),
                        tenant,
                        student,
                        openKey,
                        10_000L,
                        10_000L,
                        10_000L,
                        10_000L,
                        UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());
    }

    @Test
    void insufficientBalanceIsRejectedWithoutWritingLedgerRows() {
        PmWallet wallet = openWallet(10_000L, 10_000L);
        walletService.topUp(new TopUpCommand(
                wallet.getId(), 1_000L, UUID.randomUUID(), key("topup-small"), UUID.randomUUID()));
        String purchaseKey = key("purchase-too-large");

        PocketMoneyException exception = assertThrows(
                PocketMoneyException.class,
                () -> walletService.purchase(new PurchaseCommand(
                        wallet.getId(),
                        2_000L,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        purchaseKey,
                        UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.INSUFFICIENT_FUNDS, exception.code());
        assertEquals(980L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
        assertEquals(980L, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
        assertFalse(ledgerEntryRepository.existsByIdempotencyKey(purchaseKey));
    }

    @Test
    void unbalancedDoubleEntryPostingIsRejectedAndKeyIsNotStored() {
        UUID tenant = newTenant("UNBAL-" + shortId());
        UUID reference = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        String idempotencyKey = key("unbalanced");

        PocketMoneyException exception = assertThrows(PocketMoneyException.class, () -> {
            ledgerService.post(new LedgerPostingRequest(
                    idempotencyKey,
                    "TEST",
                    reference,
                    tenant,
                    actor,
                    List.of(
                            LedgerPosting.clearingPosting(
                                    AccountCode.PARENT_FUNDS_IN,
                                    Direction.DEBIT,
                                    1_000L,
                                    LedgerEntryType.TOP_UP,
                                    AccountCode.STUDENT_WALLET),
                            LedgerPosting.clearingPosting(
                                    AccountCode.SHOP_SETTLEMENT,
                                    Direction.CREDIT,
                                    900L,
                                    LedgerEntryType.PURCHASE,
                                    AccountCode.STUDENT_WALLET))));
        });
        assertEquals(PocketMoneyErrorCode.UNBALANCED_POSTING, exception.code());
        assertFalse(idempotencyKeyRepository.existsById(idempotencyKey));
    }

    @Test
    void reconciliationDetectsDeliberatelyCorruptedCachedBalance() {
        PmWallet wallet = openWallet(100_000L, 50_000L);
        walletService.topUp(new TopUpCommand(
                wallet.getId(), 10_000L, UUID.randomUUID(), key("topup-corrupt"), UUID.randomUUID()));

        jdbc.update("update pm_wallets set balance = ? where id = ?", 12_345L, wallet.getId());

        ReconciliationResult result = reconciliationService.reconcile(wallet.getId());
        assertFalse(result.healthy());
        assertEquals(12_345L, result.cachedBalance());
        assertEquals(9_800L, result.ledgerBalance());
        assertEquals(2_545L, result.difference());
        assertNotEquals(result.cachedBalance(), result.ledgerBalance());
    }

    @Test
    void negativeAmountsAreRejectedBeforeAnyKeyIsClaimed() {
        PmWallet wallet = openWallet(10_000L, 10_000L);
        String idempotencyKey = key("negative-amount");

        PocketMoneyException exception = assertThrows(
                PocketMoneyException.class,
                () -> walletService.topUp(new TopUpCommand(
                        wallet.getId(), -1L, UUID.randomUUID(), idempotencyKey, UUID.randomUUID())));
        assertEquals(PocketMoneyErrorCode.INVALID_AMOUNT, exception.code());
        assertFalse(idempotencyKeyRepository.existsById(idempotencyKey));
    }

    private PmWallet openWallet(long dailyLimit, long perTxnLimit) {
        UUID tenant = newTenant("PKT-" + shortId());
        UUID student = newStudent(tenant, "STU-" + shortId());
        return walletService.openWallet(new OpenWalletCommand(
                UUID.randomUUID(),
                tenant,
                student,
                key("wallet-open"),
                dailyLimit,
                dailyLimit,
                dailyLimit,
                perTxnLimit,
                UUID.randomUUID()));
    }

    private UUID newTenant(String code) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into tenants
                    (id, code, name, o_level_enabled, a_level_enabled, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                code,
                "Pocket Money Test School",
                true,
                false,
                "ACTIVE",
                0L,
                now,
                now);
        return id;
    }

    private UUID newStudent(UUID tenantId, String studentCode) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into students
                    (id, tenant_id, student_code, first_name, last_name, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                studentCode,
                "Test",
                "Student",
                "ACTIVE",
                0L,
                now,
                now);
        return id;
    }

    private String key(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
