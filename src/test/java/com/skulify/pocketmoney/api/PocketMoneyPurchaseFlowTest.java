package com.skulify.pocketmoney.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseResponse;
import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import com.skulify.pocketmoney.ledger.LedgerEntryRepository;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.SchoolWalletApplication;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
        classes = SchoolWalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PocketMoneyPurchaseFlowTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private LedgerEntryRepository ledgerEntries;
    @Autowired private PocketMoneyPurchaseService purchaseService;

    private UUID tenantId;
    private UUID studentId;
    private UUID walletId;
    private UUID cashierId;
    private String cardUid;
    private String terminalCode;
    private String merchantCode;
    private String deviceKey;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        studentId = newStudent(tenantId);
        cashierId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        cardUid = "TEST-NFC-" + suffix;
        terminalCode = "TEST-POS-" + suffix;
        merchantCode = "TEST-MER-" + suffix;
        deviceKey = "TEST-SENSOR-KEY-" + suffix;
        tenant(tenantId);
        PmWallet wallet = walletService.openWallet(new OpenWalletCommand(
                UUID.randomUUID(), tenantId, studentId, "flow-wallet-" + UUID.randomUUID(),
                100_000L, 100_000L, 100_000L, 50_000L, cashierId));
        walletId = wallet.getId();
        walletService.topUp(new TopUpCommand(
                walletId, 10_000L, UUID.randomUUID(), "flow-topup-" + UUID.randomUUID(), cashierId));
        seedCredentials();
        authenticate();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sensorPurchasePostsOnceAndReplayDoesNotMoveMoneyTwice() {
        PurchaseRequest request = new PurchaseRequest(
                studentId, cardUid, terminalCode, deviceKey, 2_000L, "Mandazi and chai",
                false, "tap-001", Instant.now());

        PurchaseResponse first = purchaseService.purchase(request, "flow-purchase-001");
        assertFalse(first.replayed());
        assertEquals(2_000L, first.amount());
        assertEquals(7_800L, first.balanceAfter());
        assertEquals("APPROVED", first.status());

        PurchaseResponse replay = purchaseService.purchase(request, "flow-purchase-001");
        assertTrue(replay.replayed());
        assertEquals(first.id(), replay.id());
        assertEquals(7_800L, replay.balanceAfter());
        assertEquals("Mandazi and chai", replay.items());

        for (int attempt = 0; attempt < 9; attempt++) {
            PurchaseResponse repeated = purchaseService.purchase(request, "flow-purchase-001");
            assertTrue(repeated.replayed());
            assertEquals(first.id(), repeated.id());
            assertEquals(7_800L, repeated.balanceAfter());
        }

        assertEquals(7_800L, walletRepository.findById(walletId).orElseThrow().getBalance());
        assertEquals(7_800L, ledgerEntries.sumSignedByWallet(walletId));
        assertEquals(1, purchaseService.purchases(studentId).size());
    }

    @Test
    void wrongDeviceKeyNeverReachesTheLedger() {
        PurchaseRequest request = new PurchaseRequest(
                studentId, cardUid, terminalCode, "WRONG-KEY", 1_000L, "Book",
                false, "tap-wrong", Instant.now());

        PocketMoneyException failure = assertThrows(
                PocketMoneyException.class,
                () -> purchaseService.purchase(request, "flow-purchase-wrong-key"));
        assertEquals(PocketMoneyErrorCode.DEVICE_KEY_INVALID, failure.code());
        assertEquals(9_800L, walletRepository.findById(walletId).orElseThrow().getBalance());
        assertEquals(9_800L, ledgerEntries.sumSignedByWallet(walletId));
    }

    @Test
    void offlinePurchaseAboveTheConfiguredCapIsDeclined() {
        PurchaseRequest request = new PurchaseRequest(
                studentId, cardUid, terminalCode, deviceKey, 20_000L, "Uniform",
                true, "tap-offline", Instant.now());

        PocketMoneyException failure = assertThrows(
                PocketMoneyException.class,
                () -> purchaseService.purchase(request, "flow-purchase-offline"));
        assertEquals(PocketMoneyErrorCode.LIMIT_EXCEEDED, failure.code());
        assertEquals(9_800L, walletRepository.findById(walletId).orElseThrow().getBalance());
    }

    private void seedCredentials() {
        UUID merchantId = PocketMoneyHashes.deterministicId("MERCHANT", merchantCode);
        UUID cardId = PocketMoneyHashes.deterministicId("CARD", cardUid);
        UUID terminalId = PocketMoneyHashes.deterministicId("TERMINAL", terminalCode);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into pm_merchants
                    (id, tenant_id, code, name, category, status, settlement_account_code,
                     created_at, updated_at)
                values (?, ?, ?, 'Test merchant', 'TEST', 'ACTIVE',
                        'SHOP_SETTLEMENT', ?, ?)
                """,
                merchantId, tenantId, merchantCode, now, now);
        jdbc.update(
                """
                insert into pm_cards
                    (id, tenant_id, wallet_id, uid_hash, uid_label, status, issued_at,
                     created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                cardId,
                tenantId,
                walletId,
                PocketMoneyHashes.identifier(cardUid),
                cardUid,
                now,
                now,
                now);
        jdbc.update(
                """
                insert into pm_terminals
                    (id, tenant_id, merchant_id, code, device_key_hash, status,
                     created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                terminalId,
                tenantId,
                merchantId,
                terminalCode,
                PocketMoneyHashes.identifier(deviceKey),
                now,
                now);
    }

    private UUID newStudent(UUID tenant) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into students
                    (id, tenant_id, student_code, first_name, last_name, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenant,
                "PM-STU-" + UUID.randomUUID().toString().substring(0, 8),
                "Purchase",
                "Student",
                "ACTIVE",
                0L,
                now,
                now);
        return id;
    }

    private void tenant(UUID id) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into tenants
                    (id, code, name, o_level_enabled, a_level_enabled, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "PM-TEN-" + UUID.randomUUID().toString().substring(0, 8),
                "Purchase Flow School",
                true,
                false,
                "ACTIVE",
                0L,
                now,
                now);
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(
                cashierId, tenantId, "cashier", "Cashier", Set.of(Role.CASHIER), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }
}
