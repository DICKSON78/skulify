package com.skulify.retail;

import static org.junit.jupiter.api.Assertions.*;

import com.skulify.pocketmoney.api.PocketMoneyPurchaseService;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.TopUpCommand;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import com.skulify.retail.dto.RetailDtos.AdjustRequest;
import com.skulify.retail.dto.RetailDtos.BatchResponse;
import com.skulify.retail.dto.RetailDtos.DiscountRequest;
import com.skulify.retail.dto.RetailDtos.PreviewItemRequest;
import com.skulify.retail.dto.RetailDtos.PreviewRequest;
import com.skulify.retail.dto.RetailDtos.PreviewResponse;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderItemRequest;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderRequest;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderResponse;
import com.skulify.retail.dto.RetailDtos.ReceiveOrderItemRequest;
import com.skulify.retail.dto.RetailDtos.ReceiveOrderRequest;
import com.skulify.retail.dto.RetailDtos.ReconciliationResponse;
import com.skulify.retail.dto.RetailDtos.RestockRequest;
import com.skulify.retail.dto.RetailDtos.ReturnItemRequest;
import com.skulify.retail.dto.RetailDtos.ReturnRequest;
import com.skulify.retail.dto.RetailDtos.ReturnResponse;
import com.skulify.retail.dto.RetailDtos.SaleRequest;
import com.skulify.retail.dto.RetailDtos.SaleResponse;
import com.skulify.retail.controller.RetailController;
import com.skulify.retail.service.RetailService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.school_wallet.SchoolWalletApplication;
import org.example.school_wallet.common.error.ApiException;
import org.example.school_wallet.common.tenant.TenantContext;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@SpringBootTest(classes = SchoolWalletApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RetailIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RetailService retailService;
    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private PocketMoneyPurchaseService pocketMoneyPurchaseService;

    private UUID tenantId;
    private UUID studentId;
    private UUID walletId;
    private UUID cashierId;
    private UUID storeKeeperId;
    private String cardUid;
    private String terminalCode;
    private String deviceKey;
    private UUID merchantId;
    private String lastSuffix;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        cashierId = UUID.randomUUID();
        storeKeeperId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        cardUid = "RET-NFC-" + suffix;
        terminalCode = "RET-POS-" + suffix;
        deviceKey = "RET-KEY-" + suffix;
        insertTenant(tenantId);
        TenantContext.set(tenantId);
        studentId = insertStudent(tenantId, "RET-STU-" + suffix);
        PmWallet wallet = walletService.openWallet(new OpenWalletCommand(
                UUID.randomUUID(), tenantId, studentId, "ret-wallet-" + UUID.randomUUID(),
                200_000L, 500_000L, 1_000_000L, 100_000L, cashierId));
        walletId = wallet.getId();
        walletService.topUp(new TopUpCommand(walletId, 50_000L, UUID.randomUUID(), "ret-topup-" + suffix, cashierId));
        seedPmCredentials();
        authenticateCashier();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void previewFingerprintChangesWhenPriceChanges() {
        UUID productId = insertStoreItem("RET-SKU-1-" + suffix(), "Water", 1000, 20, 5);
        insertBatch(productId, "B1-" + suffix(), 20, LocalDate.now().plusDays(30));

        PreviewResponse preview = retailService.preview(new PreviewRequest(
                List.of(new PreviewItemRequest(productId, 2)), null));
        assertNotNull(preview.pricingFingerprint());
        assertEquals(2000L, preview.total());

        jdbc.update("update store_items set price = 1200, version = version + 1 where id = ?", productId);

        PreviewResponse changed = retailService.preview(new PreviewRequest(
                List.of(new PreviewItemRequest(productId, 2)), null));
        assertEquals(2400L, changed.total());
        assertNotEquals(preview.pricingFingerprint(), changed.pricingFingerprint());

        ApiException ex = assertThrows(ApiException.class, () -> retailService.sale(new SaleRequest(
                studentId, cardUid, terminalCode, deviceKey,
                List.of(new PreviewItemRequest(productId, 2)), null, preview.pricingFingerprint(),
                null, "nonce-1", Instant.now()), "ret-sale-price-1"));
        assertEquals("PRICE_CHANGED", ex.getCode());
        assertNotNull(ex.getDetails());
    }

    @Test
    void saleDebitsWalletConsumesFefoAndUpdatesReconciliation() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-2-" + sfx, "Juice", 1500, 13, 2);
        // FEFO: earlier expiry should be consumed first
        String earlyBatch = "EARLY-" + sfx;
        String lateBatch = "LATE-" + sfx;
        insertBatch(productId, earlyBatch, 3, LocalDate.now().plusDays(1));
        insertBatch(productId, lateBatch, 10, LocalDate.now().plusDays(30));

        SaleResponse sale = retailService.sale(new SaleRequest(
                studentId, cardUid, terminalCode, deviceKey,
                List.of(new PreviewItemRequest(productId, 4)), null, null,
                null, "nonce-fefo", Instant.now()), "ret-sale-fefo-1-" + sfx);

        assertFalse(sale.replayed());
        assertEquals(6000L, sale.total());
        assertNotNull(sale.receiptNo());
        assertEquals(43000L, sale.balanceAfter()); // 49000 - 6000
        assertEquals(43000L, walletRepository.findById(walletId).orElseThrow().getBalance());

        Integer remainingEarly = jdbc.queryForObject(
                "select quantity_remaining from retail_batches where tenant_id = ? and batch_number = ?",
                Integer.class, tenantId, earlyBatch);
        Integer remainingLate = jdbc.queryForObject(
                "select quantity_remaining from retail_batches where tenant_id = ? and batch_number = ?",
                Integer.class, tenantId, lateBatch);
        assertEquals(0, remainingEarly);
        assertEquals(9, remainingLate);

        Integer stockQty = jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId);
        assertEquals(9, stockQty);

        ReconciliationResponse recon = retailService.reconciliation();
        var item = recon.items().stream().filter(i -> i.productId().equals(productId)).findFirst().orElseThrow();
        assertEquals(9, item.cachedStock());
        assertEquals(9, item.ledgerStock());
        assertEquals(0, item.delta());
    }

    @Test
    void replayReturnsOneSaleNoDoubleDebitOrStockDecrement() {
        UUID productId = insertStoreItem("RET-SKU-3-" + suffix(), "Snack", 2000, 10, 2);
        insertBatch(productId, "B3-" + suffix(), 10, LocalDate.now().plusDays(20));

        SaleRequest request = new SaleRequest(
                studentId, cardUid, terminalCode, deviceKey,
                List.of(new PreviewItemRequest(productId, 2)), null, null,
                null, "nonce-replay", Instant.now());

        SaleResponse first = retailService.sale(request, "ret-sale-replay-1");
        SaleResponse second = retailService.sale(request, "ret-sale-replay-1");

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.id(), second.id());
        assertEquals(45000L, walletRepository.findById(walletId).orElseThrow().getBalance());
        Integer stockQty = jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId);
        assertEquals(8, stockQty);
        Integer saleCount = jdbc.queryForObject(
                "select count(*) from retail_sales where tenant_id = ? and idempotency_key = ?",
                Integer.class, tenantId, "ret-sale-replay-1");
        assertEquals(1, saleCount);
    }

    @Test
    void returnRestocksAndCreditsWalletThroughLedger() {
        UUID productId = insertStoreItem("RET-SKU-4-" + suffix(), "Book", 3000, 5, 1);
        insertBatch(productId, "B4-" + suffix(), 5, LocalDate.now().plusDays(20));

        SaleResponse sale = retailService.sale(new SaleRequest(
                studentId, cardUid, terminalCode, deviceKey,
                List.of(new PreviewItemRequest(productId, 2)), null, null,
                null, "nonce-return", Instant.now()), "ret-sale-return-1");
        assertEquals(43000L, walletRepository.findById(walletId).orElseThrow().getBalance());
        assertEquals(3, jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId));

        UUID saleItemId = sale.items().get(0).id();
        ReturnResponse refund = retailService.returnSale(sale.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(saleItemId, 1, "RESTOCK")), "Damaged package"), "ret-return-1");

        assertEquals(3000L, refund.amount());
        assertEquals(46000L, walletRepository.findById(walletId).orElseThrow().getBalance());
        assertEquals(4, jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId));
        Integer movementCount = jdbc.queryForObject(
                "select count(*) from retail_stock_movements where tenant_id = ? and reference = ? and type = 'RETURN'",
                Integer.class, tenantId, sale.reference());
        assertTrue(movementCount != null && movementCount >= 1);
        String reason = jdbc.queryForObject(
                "select reason from retail_stock_movements where tenant_id = ? and reference = ? and type = 'RETURN' order by created_at desc limit 1",
                String.class, tenantId, sale.reference());
        assertNotNull(reason);
        assertFalse(reason.matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*"));
        assertTrue(reason.contains("Damaged package"));
    }

    @Test
    void purchaseOrderApproveAndReceiveFlow() {
        authenticateStoreKeeper();
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-5-" + sfx, "Uniform", 5000, 0, 2);
        var supplier = retailService.createSupplier(
                new com.skulify.retail.dto.RetailDtos.SupplierRequest("SUP-" + sfx, "Acme", "0700", "a@b.com", "ACTIVE"),
                "sup-key-" + sfx);

        PurchaseOrderResponse created = retailService.createPurchaseOrder(new PurchaseOrderRequest(
                supplier.id(), "Restock uniforms",
                List.of(new PurchaseOrderItemRequest(productId, 10, new BigDecimal("3000.00")))),
                "po-create-" + sfx);
        assertEquals("OPEN", created.status());

        PurchaseOrderResponse approved = retailService.approvePurchaseOrder(created.id(), "po-approve-" + sfx);
        assertEquals("APPROVED", approved.status());

        UUID orderItemId = approved.items().get(0).id();
        String batchNumber = "POB-" + sfx;
        PurchaseOrderResponse received = retailService.receivePurchaseOrder(created.id(), new ReceiveOrderRequest(
                List.of(new ReceiveOrderItemRequest(orderItemId, 10, batchNumber, LocalDate.now().plusDays(90)))),
                "po-receive-" + sfx);

        assertEquals("RECEIVED", received.status());
        assertEquals(10, jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId));
        Integer batchCount = jdbc.queryForObject(
                "select count(*) from retail_batches where tenant_id = ? and product_id = ? and batch_number = ?",
                Integer.class, tenantId, productId, batchNumber);
        assertEquals(1, batchCount);
    }

    @Test
    void discountedReturnRefundsExactlyThePaidTotal() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-DISC-" + sfx, "Notebook", 1000, 5, 1);
        insertBatch(productId, "DISC-" + sfx, 5, LocalDate.now().plusDays(20));
        String code = "TEN-" + sfx;
        authenticateStoreKeeper();
        retailService.createDiscount(
                new DiscountRequest(code, "PERCENT", new BigDecimal("10"), "ACTIVE", null),
                "disc-key-" + sfx);
        authenticateCashier();

        SaleResponse sale = retailService.sale(saleRequest(productId, 2, code, "nonce-disc-" + sfx),
                "ret-sale-disc-" + sfx);
        assertEquals(2000L, sale.subtotal());
        assertEquals(200L, sale.discount());
        assertEquals(1800L, sale.total());
        assertEquals(47200L, walletRepository.findById(walletId).orElseThrow().getBalance());

        ReturnResponse refund = retailService.returnSale(sale.id(),
                new ReturnRequest(List.of(new ReturnItemRequest(sale.items().get(0).id(), 2, "RESTOCK")), null),
                "ret-return-disc-" + sfx);
        assertEquals(1800L, refund.amount());
        assertEquals(49000L, walletRepository.findById(walletId).orElseThrow().getBalance());
    }

    @Test
    void writeOffReturnKeepsCachedStockAndLedgerBalanced() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-WO-" + sfx, "Pen", 1000, 5, 1);
        insertBatch(productId, "WO-" + sfx, 5, LocalDate.now().plusDays(20));

        SaleResponse sale = retailService.sale(saleRequest(productId, 2, null, "nonce-wo-" + sfx),
                "ret-sale-wo-" + sfx);
        Integer remainingAfterSale = jdbc.queryForObject(
                "select quantity_remaining from retail_batches where tenant_id = ? and batch_number = ?",
                Integer.class, tenantId, "WO-" + sfx);

        retailService.returnSale(sale.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(sale.items().get(0).id(), 1, "WRITE_OFF")), "Damaged"),
                "ret-return-wo-" + sfx);

        assertEquals(3, jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId));
        assertEquals(remainingAfterSale, jdbc.queryForObject(
                "select quantity_remaining from retail_batches where tenant_id = ? and batch_number = ?",
                Integer.class, tenantId, "WO-" + sfx));
        ReconciliationResponse recon = retailService.reconciliation();
        var item = recon.items().stream().filter(i -> i.productId().equals(productId)).findFirst().orElseThrow();
        assertEquals(3, item.cachedStock());
        assertEquals(3, item.ledgerStock());
        assertEquals(0, item.delta());
        assertTrue(recon.balanced());
    }

    @Test
    void replaysReturnTheOriginalResponseSnapshot() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-SNAP-" + sfx, "File", 1000, 10, 1);
        String restockKey = "ret-restock-snap-" + sfx;
        authenticateStoreKeeper();
        BatchResponse restocked = retailService.restock(new RestockRequest(
                productId, 5, new BigDecimal("100.00"), "SNAP-" + sfx,
                LocalDate.now().plusDays(30), null, "restock"), restockKey);
        assertEquals(5, restocked.quantityRemaining());
        authenticateCashier();

        SaleRequest firstRequest = saleRequest(productId, 1, null, "nonce-snap-1-" + sfx);
        SaleResponse first = retailService.sale(firstRequest, "ret-sale-snap-1-" + sfx);
        retailService.sale(saleRequest(productId, 1, null, "nonce-snap-2-" + sfx), "ret-sale-snap-2-" + sfx);
        String returnKey = "ret-return-snap-" + sfx;
        ReturnResponse originalReturn = retailService.returnSale(first.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(first.items().get(0).id(), 1, "RESTOCK")), null), returnKey);
        retailService.sale(saleRequest(productId, 1, null, "nonce-snap-3-" + sfx), "ret-sale-snap-3-" + sfx);

        SaleResponse replayedSale = retailService.sale(firstRequest, "ret-sale-snap-1-" + sfx);
        assertTrue(replayedSale.replayed());
        assertEquals(first.id(), replayedSale.id());
        assertEquals("COMPLETED", replayedSale.status());
        assertEquals(first.balanceAfter(), replayedSale.balanceAfter());

        ReturnResponse replayedReturn = retailService.returnSale(first.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(first.items().get(0).id(), 1, "RESTOCK")), null), returnKey);
        assertTrue(replayedReturn.replayed());
        assertEquals(originalReturn.balanceAfter(), replayedReturn.balanceAfter());

        authenticateStoreKeeper();
        BatchResponse replayedRestock = retailService.restock(new RestockRequest(
                productId, 5, new BigDecimal("100.00"), "SNAP-" + sfx,
                LocalDate.now().plusDays(30), null, "restock"), restockKey);
        assertEquals(5, replayedRestock.quantityRemaining());
    }

    @Test
    void partialReturnsCannotRestockTheSameBatchTwice() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-PART-" + sfx, "Crayon", 1000, 8, 1);
        insertBatch(productId, "PART-EARLY-" + sfx, 3, LocalDate.now().plusDays(1));
        insertBatch(productId, "PART-LATE-" + sfx, 5, LocalDate.now().plusDays(30));

        SaleResponse sale = retailService.sale(saleRequest(productId, 4, null, "nonce-part-" + sfx),
                "ret-sale-part-" + sfx);
        UUID saleItemId = sale.items().get(0).id();

        assertEquals(2000L, retailService.returnSale(sale.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(saleItemId, 2, "RESTOCK")), null),
                "ret-return-part-1-" + sfx).amount());
        assertEquals(2000L, retailService.returnSale(sale.id(), new ReturnRequest(
                List.of(new ReturnItemRequest(saleItemId, 2, "RESTOCK")), null),
                "ret-return-part-2-" + sfx).amount());
        assertEquals(49000L, walletRepository.findById(walletId).orElseThrow().getBalance());
        assertEquals(8, jdbc.queryForObject("select stock_quantity from store_items where id = ?", Integer.class, productId));

        ApiException ex = assertThrows(ApiException.class, () -> retailService.returnSale(sale.id(),
                new ReturnRequest(List.of(new ReturnItemRequest(saleItemId, 1, "RESTOCK")), null),
                "ret-return-part-3-" + sfx));
        assertEquals("SALE_ALREADY_RETURNED", ex.getCode());
        Integer refundUnits = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from retail_refund_items where tenant_id = ? and sale_item_id = ?",
                Integer.class, tenantId, saleItemId);
        assertEquals(4, refundUnits);
        ReconciliationResponse recon = retailService.reconciliation();
        var item = recon.items().stream().filter(i -> i.productId().equals(productId)).findFirst().orElseThrow();
        assertEquals(0, item.delta());
    }

    @Test
    void concurrentReplaysPostExactlyOneSale() throws Exception {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-CONC-" + sfx, "Tea", 1000, 10, 1);
        insertBatch(productId, "CONC-" + sfx, 10, LocalDate.now().plusDays(20));
        String key = "ret-sale-concurrent-" + sfx;
        SaleRequest request = saleRequest(productId, 1, null, "nonce-conc-" + sfx);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<SaleResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    TenantContext.set(tenantId);
                    authenticateCashier();
                    start.await();
                    try {
                        return retailService.sale(request, key);
                    } finally {
                        SecurityContextHolder.clearContext();
                        TenantContext.clear();
                    }
                }));
            }
            start.countDown();
            SaleResponse a = futures.get(0).get(30, TimeUnit.SECONDS);
            SaleResponse b = futures.get(1).get(30, TimeUnit.SECONDS);
            assertEquals(a.id(), b.id());
            assertEquals(1, (a.replayed() ? 1 : 0) + (b.replayed() ? 1 : 0));
        } finally {
            pool.shutdownNow();
            authenticateCashier();
        }
        Integer saleCount = jdbc.queryForObject(
                "select count(*) from retail_sales where tenant_id = ? and idempotency_key = ?",
                Integer.class, tenantId, key);
        assertEquals(1, saleCount);
        Integer purchaseCount = jdbc.queryForObject(
                "select count(*) from pm_purchases where tenant_id = ? and idempotency_key = ?",
                Integer.class, tenantId, "retail-sale-" + key);
        assertEquals(1, purchaseCount);
        assertEquals(48000L, walletRepository.findById(walletId).orElseThrow().getBalance());
    }

    @Test
    void refundWindowIsEnforcedFromTheOriginalSaleTimestamp() {
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-WIN-" + sfx, "Chalk", 1000, 5, 1);
        insertBatch(productId, "WIN-" + sfx, 5, LocalDate.now().plusDays(20));
        SaleResponse sale = retailService.sale(saleRequest(productId, 1, null, "nonce-win-" + sfx),
                "ret-sale-win-" + sfx);
        jdbc.update("update retail_sales set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(java.time.Duration.ofMinutes(45))), sale.id());

        PocketMoneyException ex = assertThrows(PocketMoneyException.class, () -> retailService.returnSale(
                sale.id(), new ReturnRequest(List.of(new ReturnItemRequest(
                        sale.items().get(0).id(), 1, "RESTOCK")), null), "ret-return-win-" + sfx));
        assertEquals("REFUND_WINDOW_EXPIRED", ex.code().name());
    }

    @Test
    void supplierTenantIsolationAndIdempotencyGuardsHold() {
        authenticateStoreKeeper();
        String sfx = suffix();
        UUID productId = insertStoreItem("RET-SKU-GUARD-" + sfx, "Paper", 1000, 5, 1);
        insertBatch(productId, "GUARD-" + sfx, 5, LocalDate.now().plusDays(20));
        UUID otherTenant = UUID.randomUUID();
        insertTenant(otherTenant);
        UUID foreignSupplier = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into retail_suppliers
                    (id, tenant_id, code, name, status, version, created_at, updated_at)
                values (?, ?, ?, 'Foreign supplier', 'ACTIVE', 0, ?, ?)
                """, foreignSupplier, otherTenant, "FOREIGN-" + sfx, now, now);

        ApiException foreign = assertThrows(ApiException.class, () -> retailService.createPurchaseOrder(
                new PurchaseOrderRequest(foreignSupplier, null,
                        List.of(new PurchaseOrderItemRequest(productId, 1, new BigDecimal("500.00")))),
                "po-foreign-" + sfx));
        assertEquals("SUPPLIER_NOT_FOUND", foreign.getCode());

        authenticateCashier();
        ApiException missing = assertThrows(ApiException.class, () -> retailService.sale(
                saleRequest(productId, 1, null, "nonce-missing-" + sfx), null));
        assertEquals("IDEMPOTENCY_REQUIRED", missing.getCode());

        String key = "ret-sale-conflict-" + sfx;
        retailService.sale(saleRequest(productId, 1, null, "nonce-conflict-" + sfx), key);
        ApiException conflict = assertThrows(ApiException.class, () -> retailService.sale(
                saleRequest(productId, 2, null, "nonce-conflict-" + sfx), key));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.getCode());
        assertEquals(48000L, walletRepository.findById(walletId).orElseThrow().getBalance());
    }

    @Test
    void everyRetailWriteEndpointRequiresAnIdempotencyHeader() {
        for (Method method : RetailController.class.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post == null || java.util.Arrays.stream(post.value()).anyMatch(path -> path.contains("preview"))) {
                continue;
            }
            boolean guarded = java.util.Arrays.stream(method.getParameterAnnotations())
                    .flatMap(java.util.Arrays::stream)
                    .anyMatch(annotation -> annotation instanceof RequestHeader header
                            && java.util.Arrays.asList(header.value()).contains("Idempotency-Key"));
            assertTrue(guarded, method.getName() + " must require Idempotency-Key");
        }
    }

    // ---- helpers ----

    private String suffix() {
        lastSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return lastSuffix;
    }

    private SaleRequest saleRequest(UUID productId, int quantity, String discountCode, String nonce) {
        return new SaleRequest(studentId, cardUid, terminalCode, deviceKey,
                List.of(new PreviewItemRequest(productId, quantity)), discountCode, null,
                null, nonce, Instant.now());
    }

    private void authenticateCashier() {
        AuthenticatedUser user = new AuthenticatedUser(
                cashierId, tenantId, "cashier", "Cashier", Set.of(Role.CASHIER), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }

    private void authenticateStoreKeeper() {
        AuthenticatedUser user = new AuthenticatedUser(
                storeKeeperId, tenantId, "storekeeper", "Store Keeper", Set.of(Role.STORE_KEEPER), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }

    private void insertTenant(UUID id) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into tenants (id, code, name, o_level_enabled, a_level_enabled, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "RET-TEN-" + UUID.randomUUID().toString().substring(0, 8),
                "Retail Test School", true, false, "ACTIVE", 0L, now, now);
    }

    private UUID insertStudent(UUID tenant, String code) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into students (id, tenant_id, student_code, first_name, last_name, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenant, code, "Retail", "Student", "ACTIVE", 0L, now, now);
        return id;
    }

    private void seedPmCredentials() {
        UUID cardId = PocketMoneyHashes.deterministicId("CARD", cardUid);
        UUID terminalId = PocketMoneyHashes.deterministicId("TERMINAL", terminalCode);
        merchantId = PocketMoneyHashes.deterministicId("MERCHANT", "RET-MER-" + terminalCode);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into pm_merchants (id, tenant_id, code, name, category, status, settlement_account_code, created_at, updated_at)
                values (?, ?, ?, 'Retail Merchant', 'RETAIL', 'ACTIVE', 'SHOP_SETTLEMENT', ?, ?)
                """, merchantId, tenantId, "RET-MER-" + terminalCode, now, now);
        jdbc.update("""
                insert into pm_cards (id, tenant_id, wallet_id, uid_hash, uid_label, status, issued_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, cardId, tenantId, walletId, PocketMoneyHashes.identifier(cardUid), cardUid, now, now, now);
        jdbc.update("""
                insert into pm_terminals (id, tenant_id, merchant_id, code, device_key_hash, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, terminalId, tenantId, merchantId, terminalCode, PocketMoneyHashes.identifier(deviceKey), now, now);
    }

    private UUID insertStoreItem(String sku, String name, int price, int stockQty, int reorderLevel) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into store_items (id, tenant_id, sku, name, category, price, stock_quantity, reorder_level, unit, location, active, version, created_at, updated_at)
                values (?, ?, ?, ?, 'TEST', ?, ?, ?, 'PIECE', 'Store', true, 0, ?, ?)
                """, id, tenantId, sku, name, new BigDecimal(price), stockQty, reorderLevel, now, now);
        return id;
    }

    private void insertBatch(UUID productId, String batchNumber, int quantity, LocalDate expiryDate) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into retail_batches (id, tenant_id, product_id, batch_number, quantity, quantity_remaining, unit_cost, expiry_date, received_at, source, reference, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 0, ?, ?, 'SEED', 'TEST', 0, ?, ?)
                """, id, tenantId, productId, batchNumber, quantity, quantity,
                expiryDate == null ? null : java.sql.Date.valueOf(expiryDate), now, now, now);
        // also create matching stock movement so reconciliation is consistent
        UUID movementId = UUID.randomUUID();
        jdbc.update("""
                insert into retail_stock_movements (id, tenant_id, product_id, type, quantity, quantity_after, reference, reason, batch_id, version, created_at, updated_at)
                values (?, ?, ?, 'RESTOCK', ?, ?, 'TEST', 'Test seed', ?, 0, ?, ?)
                """, movementId, tenantId, productId, quantity, quantity, id, now, now);
        lastSuffix = batchNumber.contains("-") ? batchNumber.substring(batchNumber.lastIndexOf('-') + 1) : batchNumber;
    }
}
