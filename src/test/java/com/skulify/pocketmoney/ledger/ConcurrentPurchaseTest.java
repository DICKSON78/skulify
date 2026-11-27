package com.skulify.pocketmoney.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.school_wallet.SchoolWalletApplication;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.PurchaseCommand;
import com.skulify.pocketmoney.wallet.PurchaseResult;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = SchoolWalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrentPurchaseTest {

    private static final int ATTEMPTS = 50;
    private static final long PURCHASE_AMOUNT = 1_000L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void fiftyParallelPurchasesNeverOverspend() throws Exception {
        PmWallet wallet = openWallet();
        TopUpCommand topUp = new TopUpCommand(
                wallet.getId(),
                20_000L,
                UUID.randomUUID(),
                "parallel-topup-" + wallet.getId(),
                UUID.randomUUID());
        long initialBalance = walletService.topUp(topUp).netAmount();

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        int approved;
        try {
            CountDownLatch ready = new CountDownLatch(ATTEMPTS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        walletService.purchase(new PurchaseCommand(
                                wallet.getId(),
                                PURCHASE_AMOUNT,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "parallel-purchase-" + wallet.getId() + "-" + UUID.randomUUID(),
                                UUID.randomUUID()));
                        return true;
                    } catch (PocketMoneyException exception) {
                        if (exception.code() == PocketMoneyErrorCode.INSUFFICIENT_FUNDS) {
                            return false;
                        }
                        throw exception;
                    }
                }));
            }

            ready.await();
            start.countDown();
            approved = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(90, TimeUnit.SECONDS)) {
                    approved++;
                }
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        long finalBalance = walletRepository.findById(wallet.getId()).orElseThrow().getBalance();
        long expectedFinalBalance = initialBalance - (approved * PURCHASE_AMOUNT);

        assertTrue(approved > 0, "at least one concurrent tap must be approved");
        assertTrue(approved < ATTEMPTS, "some concurrent taps must be declined");
        assertTrue(finalBalance >= 0, "cached balance must never go below zero");
        assertEquals(expectedFinalBalance, finalBalance);
        assertEquals(finalBalance, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
        assertTrue(reconciliationService.reconcile(wallet.getId()).healthy());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void tenConcurrentReplaysOfOnePurchaseKeyDebitOnlyOnce() throws Exception {
        PmWallet wallet = openWallet();
        walletService.topUp(new TopUpCommand(
                wallet.getId(),
                10_000L,
                UUID.randomUUID(),
                "replay-topup-" + wallet.getId(),
                UUID.randomUUID()));
        String purchaseKey = "replay-purchase-" + wallet.getId();
        UUID merchant = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<PurchaseResult>> futures = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int attempt = 0; attempt < 10; attempt++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return walletService.purchase(new PurchaseCommand(
                            wallet.getId(),
                            PURCHASE_AMOUNT,
                            merchant,
                            terminal,
                            purchaseKey,
                            actor));
                }));
            }
            ready.await();
            start.countDown();

            for (Future<PurchaseResult> future : futures) {
                PurchaseResult result = future.get(90, TimeUnit.SECONDS);
                assertEquals(1_000L, result.amount());
                assertEquals(8_800L, result.balanceAfter());
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(8_800L, walletRepository.findById(wallet.getId()).orElseThrow().getBalance());
        assertEquals(8_800L, ledgerEntryRepository.sumSignedByWallet(wallet.getId()));
        assertEquals(2L, ledgerEntryRepository.findAll().stream()
                .filter(entry -> purchaseKey.equals(entry.getIdempotencyKey()))
                .count());
        assertTrue(reconciliationService.reconcile(wallet.getId()).healthy());
    }

    private PmWallet openWallet() {
        UUID tenant = newTenant();
        UUID student = newStudent(tenant);
        return walletService.openWallet(new OpenWalletCommand(
                UUID.randomUUID(),
                tenant,
                student,
                "parallel-wallet-" + UUID.randomUUID(),
                1_000_000L,
                1_000_000L,
                1_000_000L,
                PURCHASE_AMOUNT,
                UUID.randomUUID()));
    }

    private UUID newTenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into tenants
                    (id, code, name, o_level_enabled, a_level_enabled, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "PAR-" + UUID.randomUUID().toString().substring(0, 8),
                "Concurrency Test School",
                true,
                false,
                "ACTIVE",
                0L,
                now,
                now);
        return id;
    }

    private UUID newStudent(UUID tenantId) {
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
                "PAR-STU-" + UUID.randomUUID().toString().substring(0, 8),
                "Concurrent",
                "Student",
                "ACTIVE",
                0L,
                now,
                now);
        return id;
    }
}
