package com.skulify.retail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseResponse;
import com.skulify.pocketmoney.api.PocketMoneyPurchaseService;
import com.skulify.pocketmoney.card.PmCard;
import com.skulify.pocketmoney.card.PmCardRepository;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import com.skulify.pocketmoney.merchant.PmMerchant;
import com.skulify.pocketmoney.merchant.PmMerchantRepository;
import com.skulify.pocketmoney.purchase.PmPurchaseRepository;
import com.skulify.pocketmoney.terminal.PmTerminal;
import com.skulify.pocketmoney.terminal.PmTerminalRepository;
import com.skulify.pocketmoney.wallet.RefundCommand;
import com.skulify.pocketmoney.wallet.RefundResult;
import com.skulify.pocketmoney.wallet.WalletRepository;
import com.skulify.pocketmoney.wallet.WalletService;
import com.skulify.retail.dto.RetailDtos.AdjustRequest;
import com.skulify.retail.dto.RetailDtos.BatchResponse;
import com.skulify.retail.dto.RetailDtos.DiscountRequest;
import com.skulify.retail.dto.RetailDtos.DiscountResponse;
import com.skulify.retail.dto.RetailDtos.LowStockItemResponse;
import com.skulify.retail.dto.RetailDtos.PreviewLineResponse;
import com.skulify.retail.dto.RetailDtos.PreviewRequest;
import com.skulify.retail.dto.RetailDtos.PreviewResponse;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderItemRequest;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderItemResponse;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderRequest;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderResponse;
import com.skulify.retail.dto.RetailDtos.ReceiveOrderItemRequest;
import com.skulify.retail.dto.RetailDtos.ReceiveOrderRequest;
import com.skulify.retail.dto.RetailDtos.ReconciliationItemResponse;
import com.skulify.retail.dto.RetailDtos.ReconciliationResponse;
import com.skulify.retail.dto.RetailDtos.ReceiptResponse;
import com.skulify.retail.dto.RetailDtos.RestockRequest;
import com.skulify.retail.dto.RetailDtos.ReturnItemRequest;
import com.skulify.retail.dto.RetailDtos.ReturnRequest;
import com.skulify.retail.dto.RetailDtos.ReturnResponse;
import com.skulify.retail.dto.RetailDtos.SaleItemResponse;
import com.skulify.retail.dto.RetailDtos.SaleRequest;
import com.skulify.retail.dto.RetailDtos.SaleResponse;
import com.skulify.retail.dto.RetailDtos.StockMovementResponse;
import com.skulify.retail.dto.RetailDtos.SupplierRequest;
import com.skulify.retail.dto.RetailDtos.SupplierResponse;
import com.skulify.retail.dto.RetailDtos.TerminalResponse;
import com.skulify.retail.entity.RetailBatch;
import com.skulify.retail.entity.RetailDiscount;
import com.skulify.retail.entity.RetailDiscountKind;
import com.skulify.retail.entity.RetailPurchaseOrder;
import com.skulify.retail.entity.RetailPurchaseOrderItem;
import com.skulify.retail.entity.RetailPurchaseOrderStatus;
import com.skulify.retail.entity.RetailRefund;
import com.skulify.retail.entity.RetailRefundItem;
import com.skulify.retail.entity.RetailRefundStatus;
import com.skulify.retail.entity.RetailSale;
import com.skulify.retail.entity.RetailSaleItem;
import com.skulify.retail.entity.RetailSaleItemBatch;
import com.skulify.retail.entity.RetailSaleItemBatchId;
import com.skulify.retail.entity.RetailSaleStatus;
import com.skulify.retail.entity.RetailStockMovement;
import com.skulify.retail.entity.RetailStockMovementType;
import com.skulify.retail.entity.RetailSupplier;
import com.skulify.retail.entity.RetailWriteIdempotency;
import com.skulify.retail.entity.RetailWriteIdempotencyId;
import com.skulify.retail.repository.RetailBatchRepository;
import com.skulify.retail.repository.RetailDiscountRepository;
import com.skulify.retail.repository.RetailPurchaseOrderItemRepository;
import com.skulify.retail.repository.RetailPurchaseOrderRepository;
import com.skulify.retail.repository.RetailRefundItemRepository;
import com.skulify.retail.repository.RetailRefundRepository;
import com.skulify.retail.repository.RetailSaleItemBatchRepository;
import com.skulify.retail.repository.RetailSaleItemRepository;
import com.skulify.retail.repository.RetailSaleRepository;
import com.skulify.retail.repository.RetailStockMovementRepository;
import com.skulify.retail.repository.RetailStoreItemRepository;
import com.skulify.retail.repository.RetailSupplierRepository;
import com.skulify.retail.repository.RetailWriteIdempotencyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.SchoolContext;
import org.example.school_wallet.operations.StoreItem;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.user.AppUserRepository;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class RetailService {

    private final RetailStoreItemRepository storeItems;
    private final RetailBatchRepository batches;
    private final RetailStockMovementRepository movements;
    private final RetailSaleRepository sales;
    private final RetailSaleItemRepository saleItems;
    private final RetailSaleItemBatchRepository saleItemBatches;
    private final RetailRefundRepository refunds;
    private final RetailRefundItemRepository refundItems;
    private final RetailSupplierRepository suppliers;
    private final RetailDiscountRepository discounts;
    private final RetailPurchaseOrderRepository purchaseOrders;
    private final RetailPurchaseOrderItemRepository purchaseOrderItems;
    private final PmCardRepository cards;
    private final PmTerminalRepository terminals;
    private final PmMerchantRepository merchants;
    private final WalletRepository wallets;
    private final StudentRepository students;
    private final AppUserRepository users;
    private final PocketMoneyPurchaseService pocketMoneyPurchaseService;
    private final WalletService walletService;
    private final ResourceAccessPolicy accessPolicy;
    private final PmPurchaseRepository pmPurchases;
    private final RetailWriteIdempotencyRepository writeIdempotency;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ReentrantLock> writeLocks = new ConcurrentHashMap<>();

    public RetailService(
            RetailStoreItemRepository storeItems,
            RetailBatchRepository batches,
            RetailStockMovementRepository movements,
            RetailSaleRepository sales,
            RetailSaleItemRepository saleItems,
            RetailSaleItemBatchRepository saleItemBatches,
            RetailRefundRepository refunds,
            RetailRefundItemRepository refundItems,
            RetailSupplierRepository suppliers,
            RetailDiscountRepository discounts,
            RetailPurchaseOrderRepository purchaseOrders,
            RetailPurchaseOrderItemRepository purchaseOrderItems,
            PmCardRepository cards,
            PmTerminalRepository terminals,
            PmMerchantRepository merchants,
            WalletRepository wallets,
            StudentRepository students,
            AppUserRepository users,
            PocketMoneyPurchaseService pocketMoneyPurchaseService,
            WalletService walletService,
            ResourceAccessPolicy accessPolicy,
            PmPurchaseRepository pmPurchases,
            RetailWriteIdempotencyRepository writeIdempotency,
            ObjectMapper objectMapper) {
        this.storeItems = storeItems;
        this.batches = batches;
        this.movements = movements;
        this.sales = sales;
        this.saleItems = saleItems;
        this.saleItemBatches = saleItemBatches;
        this.refunds = refunds;
        this.refundItems = refundItems;
        this.suppliers = suppliers;
        this.discounts = discounts;
        this.purchaseOrders = purchaseOrders;
        this.purchaseOrderItems = purchaseOrderItems;
        this.cards = cards;
        this.terminals = terminals;
        this.merchants = merchants;
        this.wallets = wallets;
        this.students = students;
        this.users = users;
        this.pocketMoneyPurchaseService = pocketMoneyPurchaseService;
        this.walletService = walletService;
        this.accessPolicy = accessPolicy;
        this.pmPurchases = pmPurchases;
        this.writeIdempotency = writeIdempotency;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------------
    // Preview / Sale
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PreviewResponse preview(PreviewRequest request) {
        accessPolicy.require("store-pos", ResourceAccessPolicy.Action.CREATE);
        UUID tenantId = tenantId();
        PricedCart cart = priceCart(tenantId, request == null ? null : request.items(), request == null ? null : request.discountCode());
        return toPreviewResponse(cart, defaultMerchantCode(tenantId), defaultTerminalCode(tenantId));
    }

    public SaleResponse sale(SaleRequest request, String idempotencyKey) {
        accessPolicy.require("store-pos", ResourceAccessPolicy.Action.CREATE);
        SaleRequest valid = validateSaleRequest(request);
        UUID tenantId = tenantId();
        String requestHash = saleRequestHash(valid, idempotencyKey);
        return withIdempotency(tenantId, "SALE", idempotencyKey, requestHash,
                () -> createSale(valid, tenantId, requestHash, idempotencyKey),
                SaleResponse::id, SaleResponse.class);
    }

    private SaleResponse createSale(
            SaleRequest valid, UUID tenantId, String requestHash, String idempotencyKey) {
        UUID actor = currentUser().id();

        // Serialise sales of the same product before reading stock or prices.
        for (UUID productId : distinctProductIds(valid.items())) {
            storeItems.findForUpdate(tenantId, productId)
                    .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
        }

        PricedCart cart = priceCart(tenantId, valid.items(), valid.discountCode());
        if (valid.pricingFingerprint() != null && !valid.pricingFingerprint().isBlank()
                && !valid.pricingFingerprint().equals(cart.fingerprint())) {
            throw new org.example.school_wallet.common.error.ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "PRICE_CHANGED",
                    "Prices changed since preview. Refresh the preview and retry.",
                    toPreviewResponse(cart, defaultMerchantCode(tenantId), defaultTerminalCode(tenantId)));
        }

        Student student = students.findById(valid.studentId())
                .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
        requireSameTenant(student.getTenantId(), tenantId, "Student belongs to another school");

        String reference = nextReference("RSL");
        RetailSale sale = new RetailSale();
        sale.setReference(reference);
        sale.setStudentId(valid.studentId());
        sale.setSubtotal(cart.subtotal());
        sale.setDiscount(cart.discountAmount());
        sale.setTotal(cart.total());
        sale.setStatus(RetailSaleStatus.COMPLETED);
        sale.setPaymentMethod("POCKET_MONEY");
        sale.setPricingFingerprint(cart.fingerprint());
        sale.setIdempotencyKey(idempotencyKey);
        sale.setRequestHash(requestHash);
        sale.setReceiptNo(nextReference("RCT"));
        sale.setNotes(blankToNull(valid.notes()));

        // Stock consumption and the Pocket Money debit join this same transaction.
        List<ConsumedSaleLine> consumed = new ArrayList<>();
        for (PricedLine line : cart.lines()) {
            consumed.add(consumeFefoForSale(tenantId, actor, line, reference));
        }

        PurchaseResponse purchase = pocketMoneyPurchaseService.purchase(new PurchaseRequest(
                valid.studentId(),
                valid.cardUid(),
                valid.terminalCode(),
                valid.deviceKey(),
                cart.total(),
                "Retail sale " + reference,
                false,
                valid.nonce(),
                valid.requestTimestamp()),
                "retail-sale-" + idempotencyKey);

        PmCard card = cards.findByUidHash(PocketMoneyHashes.identifier(valid.cardUid()))
                .orElseThrow(() -> Errors.notFound("CARD_NOT_FOUND", "Card not found"));
        requireSameTenant(card.getTenantId(), tenantId, "Card belongs to another school");
        PmTerminal terminal = terminals.findByTenantIdAndCode(tenantId, valid.terminalCode().toUpperCase())
                .orElseThrow(() -> Errors.notFound("TERMINAL_NOT_FOUND", "Terminal not found"));
        PmMerchant merchant = merchants.findById(terminal.getMerchantId())
                .orElseThrow(() -> Errors.notFound("MERCHANT_NOT_FOUND", "Merchant not found"));
        var wallet = wallets.findById(card.getWalletId())
                .orElseThrow(() -> Errors.notFound("WALLET_NOT_FOUND", "Wallet not found"));

        sale.setWalletId(wallet.getId());
        sale.setCardId(card.getId());
        sale.setMerchantId(merchant.getId());
        sale.setTerminalId(terminal.getId());
        sale.setPocketPurchaseId(purchase.id());
        RetailSale saved = sales.save(sale);

        for (ConsumedSaleLine line : consumed) {
            RetailSaleItem item = line.item();
            item.setSaleId(saved.getId());
            RetailSaleItem savedItem = saleItems.save(item);
            for (RetailSaleItemBatch allocation : line.allocations()) {
                allocation.setId(new RetailSaleItemBatchId(savedItem.getId(), allocation.getId().getBatchId()));
                saleItemBatches.save(allocation);
            }
        }

        return toSaleResponse(saved, purchase.replayed(), purchase.balanceAfter());
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> listSales(UUID studentId, Integer limit) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        int size = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        List<RetailSale> rows = studentId == null
                ? sales.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, size))
                : sales.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId, PageRequest.of(0, size));
        return rows.stream().map(s -> toSaleResponse(s, false, saleBalanceAfter(s))).toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse getSale(UUID id) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        RetailSale sale = sales.findByTenantIdAndId(tenantId(), id)
                .orElseThrow(() -> Errors.notFound("SALE_NOT_FOUND", "Retail sale not found"));
        return toSaleResponse(sale, false, saleBalanceAfter(sale));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID id) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        RetailSale sale = sales.findByTenantIdAndId(tenantId(), id)
                .orElseThrow(() -> Errors.notFound("SALE_NOT_FOUND", "Retail sale not found"));
        List<RetailSaleItem> items = saleItems.findBySaleIdOrderByCreatedAtAsc(sale.getId());
        List<ReturnResponse> saleRefunds = refunds.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId(), sale.getId())
                .stream().map(r -> new ReturnResponse(sale.getId(), r.getId(), r.getAmount(), 0L, false, r.getStatus().name(), r.getCreatedAt()))
                .toList();
        return new ReceiptResponse(
                sale.getReceiptNo(),
                sale.getReference(),
                sale.getCreatedAt(),
                studentName(sale.getStudentId()),
                cardLabel(sale.getCardId()),
                merchantCode(sale.getMerchantId()),
                terminalCode(sale.getTerminalId()),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTotal(),
                "TZS",
                sale.getStatus().name(),
                items.stream().map(this::toSaleItemResponse).toList(),
                saleRefunds);
    }

    @Transactional(readOnly = true)
    public String receiptHtml(UUID id) {
        ReceiptResponse receipt = receipt(id);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>Receipt ")
                .append(escape(receipt.receiptNo()))
                .append("</title><style>body{font-family:Arial,sans-serif;margin:24px;color:#10233d;}h1{font-size:20px;margin-bottom:4px;}table{width:100%;border-collapse:collapse;margin-top:16px;}th,td{padding:8px;border-bottom:1px solid #d8e0ea;text-align:left;font-size:14px;} .totals{margin-top:16px;width:280px;margin-left:auto;} .totals div{display:flex;justify-content:space-between;padding:4px 0;}</style></head><body>");
        html.append("<h1>Skulify Receipt</h1>");
        html.append("<div>Receipt: ").append(escape(receipt.receiptNo())).append("</div>");
        html.append("<div>Reference: ").append(escape(receipt.reference())).append("</div>");
        html.append("<div>Student: ").append(escape(receipt.studentName())).append("</div>");
        html.append("<div>Card: ").append(escape(receipt.cardUid())).append("</div>");
        html.append("<div>Terminal: ").append(escape(receipt.terminalCode())).append("</div>");
        html.append("<div>Status: ").append(escape(receipt.status())).append("</div>");
        html.append("<table><thead><tr><th>Item</th><th>Qty</th><th>Unit</th><th>Total</th></tr></thead><tbody>");
        for (SaleItemResponse item : receipt.items()) {
            html.append("<tr><td>").append(escape(item.name())).append("</td><td>")
                    .append(item.quantity()).append("</td><td>TZS ")
                    .append(item.unitPrice()).append("</td><td>TZS ")
                    .append(item.lineTotal()).append("</td></tr>");
        }
        html.append("</tbody></table><div class='totals'>");
        html.append("<div><span>Subtotal</span><span>TZS ").append(receipt.subtotal()).append("</span></div>");
        html.append("<div><span>Discount</span><span>TZS ").append(receipt.discount()).append("</span></div>");
        html.append("<div><strong>Total</strong><strong>TZS ").append(receipt.total()).append("</strong></div>");
        html.append("</div></body></html>");
        return html.toString();
    }

    public ReturnResponse returnSale(UUID saleId, ReturnRequest request, String idempotencyKey) {
        accessPolicy.require("store-pos", ResourceAccessPolicy.Action.CREATE);
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw Errors.badRequest("RETURN_ITEMS_REQUIRED", "At least one return item is required");
        }
        UUID tenantId = tenantId();
        String hash = returnRequestHash(saleId, request, idempotencyKey);
        return withIdempotency(tenantId, "RETURN", idempotencyKey, hash,
                () -> createReturn(saleId, request, idempotencyKey, hash, tenantId),
                ReturnResponse::refundId, ReturnResponse.class);
    }

    private ReturnResponse createReturn(
            UUID saleId,
            ReturnRequest request,
            String idempotencyKey,
            String requestHash,
            UUID tenantId) {
        UUID actor = currentUser().id();
        RetailSale sale = sales.findForUpdate(tenantId, saleId)
                .orElseThrow(() -> Errors.notFound("SALE_NOT_FOUND", "Retail sale not found"));
        if (sale.getStatus() == RetailSaleStatus.RETURNED) {
            throw Errors.conflict("SALE_ALREADY_RETURNED", "Sale has already been fully returned");
        }

        Map<UUID, RetailSaleItem> itemsById = saleItems.findBySaleIdOrderByCreatedAtAsc(saleId).stream()
                .collect(Collectors.toMap(RetailSaleItem::getId, i -> i));
        Map<UUID, Integer> alreadyReturned = returnedQuantitiesBySaleItem(tenantId, saleId);

        long requestedSubtotal = 0L;
        List<ReturnLinePlan> plans = new ArrayList<>();
        for (ReturnItemRequest itemReq : request.items()) {
            if (itemReq == null || itemReq.saleItemId() == null) {
                throw Errors.badRequest("SALE_ITEM_REQUIRED", "saleItemId is required");
            }
            if (itemReq.quantity() <= 0) {
                throw Errors.badRequest("INVALID_QUANTITY", "Return quantity must be positive");
            }
            String disposition = itemReq.disposition() == null ? "" : itemReq.disposition().trim().toUpperCase(Locale.ROOT);
            if (!"RESTOCK".equals(disposition) && !"WRITE_OFF".equals(disposition)) {
                throw Errors.badRequest("INVALID_DISPOSITION", "Disposition must be RESTOCK or WRITE_OFF");
            }
            RetailSaleItem saleItem = itemsById.get(itemReq.saleItemId());
            if (saleItem == null) {
                throw Errors.notFound("SALE_ITEM_NOT_FOUND", "Sale item not found in sale");
            }
            int returned = alreadyReturned.getOrDefault(saleItem.getId(), 0);
            int remaining = saleItem.getQuantity() - returned;
            if (itemReq.quantity() > remaining) {
                throw Errors.badRequest("RETURN_QUANTITY_EXCEEDED",
                        "Return quantity exceeds remaining quantity for " + saleItem.getName());
            }
            requestedSubtotal = addExact(requestedSubtotal,
                    proportionalAmount(saleItem.getLineTotal(), itemReq.quantity(), saleItem.getQuantity()));
            plans.add(new ReturnLinePlan(saleItem, itemReq.quantity(), disposition));
        }

        long refundAmount = refundAmountForSubtotal(sale, requestedSubtotal);
        if (refundAmount <= 0) {
            throw Errors.badRequest("INVALID_REFUND_AMOUNT", "Refund amount must be positive");
        }
        long refundedSoFar = refunds.sumCompletedAmountBySale(tenantId, saleId);
        if (addExact(refundedSoFar, refundAmount) > sale.getTotal()) {
            throw Errors.badRequest("REFUND_EXCEEDS_SALE", "Refund amount exceeds original sale total");
        }

        List<RetailRefundItem> refundRows = new ArrayList<>();
        for (ReturnLinePlan plan : plans) {
            refundRows.addAll(applyReturnToStock(tenantId, actor, sale, plan, request.reason()));
        }

        RefundResult walletRefund = walletService.refund(new RefundCommand(
                sale.getWalletId(),
                refundAmount,
                sale.getTotal(),
                refundedSoFar,
                sale.getCreatedAt(),
                "retail-return-" + idempotencyKey,
                actor));

        RetailRefund refund = new RetailRefund();
        refund.setSaleId(saleId);
        refund.setAmount(refundAmount);
        refund.setReason(blankToNull(request.reason()));
        refund.setStatus(RetailRefundStatus.COMPLETED);
        refund.setIdempotencyKey(idempotencyKey);
        refund.setRequestHash(requestHash);
        RetailRefund savedRefund = refunds.save(refund);

        for (RetailRefundItem row : refundRows) {
            row.setRefundId(savedRefund.getId());
        }
        refundItems.saveAll(refundRows);

        long totalReturned = refunds.sumCompletedAmountBySale(tenantId, saleId);
        if (totalReturned >= sale.getTotal()) {
            sale.setStatus(RetailSaleStatus.RETURNED);
        } else {
            sale.setStatus(RetailSaleStatus.PARTIALLY_RETURNED);
        }
        sales.save(sale);

        return new ReturnResponse(saleId, savedRefund.getId(), refundAmount,
                walletRefund.balanceAfter(), false, savedRefund.getStatus().name(), savedRefund.getCreatedAt());
    }

    // ---------------------------------------------------------------------
    // Stock
    // ---------------------------------------------------------------------

    public BatchResponse restock(RestockRequest request, String idempotencyKey) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.CREATE);
        validateRestock(request);
        UUID tenantId = tenantId();
        String hash = restockHash(request, idempotencyKey);
        return withIdempotency(tenantId, "RESTOCK", idempotencyKey, hash,
                () -> createRestock(request, tenantId), BatchResponse::id, BatchResponse.class);
    }

    private void validateRestock(RestockRequest request) {
        if (request == null || request.productId() == null) {
            throw Errors.badRequest("PRODUCT_REQUIRED", "productId is required");
        }
        if (request.quantity() <= 0) {
            throw Errors.badRequest("INVALID_QUANTITY", "Quantity must be positive");
        }
        if (request.batchNumber() == null || request.batchNumber().isBlank()) {
            throw Errors.badRequest("BATCH_REQUIRED", "batchNumber is required");
        }
    }

    private BatchResponse createRestock(RestockRequest request, UUID tenantId) {
        UUID actor = currentUser().id();
        StoreItem product = storeItems.findForUpdate(tenantId, request.productId())
                .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
        requireSameTenant(product.getTenantId(), tenantId, "Product belongs to another school");
        if (request.supplierId() != null) {
            suppliers.findByIdAndTenantId(request.supplierId(), tenantId)
                    .orElseThrow(() -> Errors.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));
        }

        var existingBatch = batches.findByTenantIdAndProductIdAndBatchNumber(
                tenantId, request.productId(), request.batchNumber().trim());
        if (existingBatch.isPresent()) {
            throw Errors.conflict("BATCH_EXISTS", "Batch number already exists for this product");
        }

        RetailBatch batch = new RetailBatch();
        batch.setProductId(product.getId());
        batch.setBatchNumber(request.batchNumber().trim());
        batch.setQuantity(request.quantity());
        batch.setQuantityRemaining(request.quantity());
        batch.setUnitCost(request.unitCost() == null ? BigDecimal.ZERO : request.unitCost());
        batch.setExpiryDate(request.expiryDate());
        batch.setReceivedAt(Instant.now());
        batch.setSupplierId(request.supplierId());
        batch.setSource("RESTOCK");
        batch.setReference(blankToNull(request.reference()));
        RetailBatch savedBatch = batches.save(batch);

        int quantityAfter = product.getStockQuantity() + request.quantity();
        product.setStockQuantity(quantityAfter);
        storeItems.save(product);

        RetailStockMovement movement = new RetailStockMovement();
        movement.setProductId(product.getId());
        movement.setType(RetailStockMovementType.RESTOCK);
        movement.setQuantity(request.quantity());
        movement.setQuantityAfter(quantityAfter);
        movement.setReference(blankToNull(request.reference()));
        movement.setReason("Manual restock");
        movement.setBatchId(savedBatch.getId());
        movement.setPerformedBy(actor);
        movements.save(movement);

        return toBatchResponse(savedBatch, product.getName());
    }

    public StockMovementResponse adjust(AdjustRequest request, String idempotencyKey) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.UPDATE);
        validateAdjust(request);
        UUID tenantId = tenantId();
        String hash = adjustHash(request, idempotencyKey);
        return withIdempotency(tenantId, "ADJUST", idempotencyKey, hash,
                () -> createAdjust(request, tenantId, idempotencyKey),
                StockMovementResponse::id, StockMovementResponse.class);
    }

    private void validateAdjust(AdjustRequest request) {
        if (request == null || request.productId() == null) {
            throw Errors.badRequest("PRODUCT_REQUIRED", "productId is required");
        }
        if (request.quantity() == 0) {
            throw Errors.badRequest("INVALID_QUANTITY", "Adjustment quantity cannot be zero");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw Errors.badRequest("REASON_REQUIRED", "reason is required");
        }
    }

    private StockMovementResponse createAdjust(AdjustRequest request, UUID tenantId, String idempotencyKey) {
        UUID actor = currentUser().id();
        StoreItem product = storeItems.findForUpdate(tenantId, request.productId())
                .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
        requireSameTenant(product.getTenantId(), tenantId, "Product belongs to another school");

        if (request.quantity() < 0) {
            int decrease = Math.abs(request.quantity());
            if (decrease > product.getStockQuantity()) {
                throw Errors.badRequest("INSUFFICIENT_STOCK", "Negative adjustment exceeds available stock");
            }
            deductFromBatches(tenantId, product.getId(), decrease);
        } else {
            RetailBatch batch = new RetailBatch();
            batch.setProductId(product.getId());
            batch.setBatchNumber("ADJ-" + sha256(idempotencyKey).substring(0, 10).toUpperCase(Locale.ROOT));
            batch.setQuantity(request.quantity());
            batch.setQuantityRemaining(request.quantity());
            batch.setUnitCost(BigDecimal.ZERO);
            batch.setReceivedAt(Instant.now());
            batch.setSource("ADJUST");
            batch.setReference("adjustment");
            batches.save(batch);
        }

        int quantityAfter = product.getStockQuantity() + request.quantity();
        if (quantityAfter < 0) quantityAfter = 0;
        product.setStockQuantity(quantityAfter);
        storeItems.save(product);

        RetailStockMovement movement = new RetailStockMovement();
        movement.setProductId(product.getId());
        movement.setType(RetailStockMovementType.ADJUST);
        movement.setQuantity(request.quantity());
        movement.setQuantityAfter(quantityAfter);
        movement.setReference("adjustment");
        movement.setReason(request.reason().trim());
        movement.setPerformedBy(actor);
        RetailStockMovement saved = movements.save(movement);
        return toMovementResponse(saved, product.getName());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listBatches(UUID productId) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        List<RetailBatch> rows = productId == null
                ? batches.findAll().stream().filter(b -> tenantId.equals(b.getTenantId())).sorted(Comparator.comparing(RetailBatch::getReceivedAt).reversed()).toList()
                : batches.findByTenantIdAndProductIdOrderByReceivedAtDesc(tenantId, productId);
        Map<UUID, String> names = productNames(rows.stream().map(RetailBatch::getProductId).toList());
        return rows.stream().map(b -> toBatchResponse(b, names.getOrDefault(b.getProductId(), ""))).toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> listMovements(UUID productId) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        List<RetailStockMovement> rows = productId == null
                ? movements.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : movements.findByTenantIdAndProductIdOrderByCreatedAtDesc(tenantId, productId);
        Map<UUID, String> names = productNames(rows.stream().map(RetailStockMovement::getProductId).toList());
        return rows.stream().map(m -> toMovementResponse(m, names.getOrDefault(m.getProductId(), ""))).toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse reconciliation() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        List<StoreItem> products = storeItems.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        Map<UUID, Long> ledger = movements.sumQuantitiesByTenantId(tenantId).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).longValue()));
        List<ReconciliationItemResponse> items = new ArrayList<>();
        boolean balanced = true;
        for (StoreItem product : products) {
            long ledgerStock = ledger.getOrDefault(product.getId(), 0L);
            long delta = (long) product.getStockQuantity() - ledgerStock;
            if (delta != 0) balanced = false;
            items.add(new ReconciliationItemResponse(
                    product.getId(), product.getSku(), product.getName(), product.getStockQuantity(), ledgerStock, delta));
        }
        return new ReconciliationResponse(items, balanced);
    }

    @Transactional(readOnly = true)
    public List<LowStockItemResponse> lowStock() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        return storeItems.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .filter(p -> p.getStockQuantity() <= p.getReorderLevel())
                .map(p -> new LowStockItemResponse(p.getId(), p.getSku(), p.getName(), p.getStockQuantity(), p.getReorderLevel(), p.getLocation()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Suppliers / Discounts
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        return suppliers.findByTenantIdOrderByNameAsc(tenantId()).stream()
                .map(s -> new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getPhone(), s.getEmail(), s.getStatus()))
                .toList();
    }

    public SupplierResponse createSupplier(SupplierRequest request, String idempotencyKey) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.CREATE);
        if (request == null || request.code() == null || request.code().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw Errors.badRequest("SUPPLIER_REQUIRED", "code and name are required");
        }
        UUID tenantId = tenantId();
        String hash = supplierHash(request, idempotencyKey);
        return withIdempotency(tenantId, "SUPPLIER", idempotencyKey, hash,
                () -> createSupplierRecord(request), SupplierResponse::id, SupplierResponse.class);
    }

    private SupplierResponse createSupplierRecord(SupplierRequest request) {
        RetailSupplier supplier = new RetailSupplier();
        supplier.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        supplier.setName(request.name().trim());
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setEmail(blankToNull(request.email()));
        supplier.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT));
        return toSupplierResponse(suppliers.save(supplier));
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> listDiscounts() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        return discounts.findByTenantIdOrderByCodeAsc(tenantId()).stream()
                .map(d -> new DiscountResponse(d.getId(), d.getCode(), d.getKind().name(), d.getValue(), d.getStatus(), d.getExpiresAt()))
                .toList();
    }

    public DiscountResponse createDiscount(DiscountRequest request, String idempotencyKey) {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.CREATE);
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw Errors.badRequest("DISCOUNT_CODE_REQUIRED", "code is required");
        }
        if (request.kind() == null || request.value() == null) {
            throw Errors.badRequest("DISCOUNT_REQUIRED", "kind and value are required");
        }
        UUID tenantId = tenantId();
        String hash = discountHash(request, idempotencyKey);
        return withIdempotency(tenantId, "DISCOUNT", idempotencyKey, hash,
                () -> createDiscountRecord(request), DiscountResponse::id, DiscountResponse.class);
    }

    private DiscountResponse createDiscountRecord(DiscountRequest request) {
        RetailDiscountKind kind;
        try {
            kind = RetailDiscountKind.valueOf(request.kind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw Errors.badRequest("INVALID_DISCOUNT_KIND", "kind must be PERCENT or FIXED");
        }
        RetailDiscount discount = new RetailDiscount();
        discount.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        discount.setKind(kind);
        discount.setValue(request.value());
        discount.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT));
        discount.setExpiresAt(request.expiresAt());
        return toDiscountResponse(discounts.save(discount));
    }

    // ---------------------------------------------------------------------
    // Purchase Orders
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> listPurchaseOrders() {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        return purchaseOrders.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toPurchaseOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(UUID id) {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.READ);
        RetailPurchaseOrder order = purchaseOrders.findByIdAndTenantId(id, tenantId())
                .orElseThrow(() -> Errors.notFound("PO_NOT_FOUND", "Purchase order not found"));
        return toPurchaseOrderResponse(order);
    }

    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request, String idempotencyKey) {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.CREATE);
        if (request == null || request.supplierId() == null) {
            throw Errors.badRequest("SUPPLIER_REQUIRED", "supplierId is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw Errors.badRequest("PO_ITEMS_REQUIRED", "At least one item is required");
        }
        UUID tenantId = tenantId();
        String hash = purchaseOrderHash(request, idempotencyKey);
        return withIdempotency(tenantId, "PO_CREATE", idempotencyKey, hash,
                () -> createPurchaseOrderRecord(request, tenantId),
                PurchaseOrderResponse::id, PurchaseOrderResponse.class);
    }

    private PurchaseOrderResponse createPurchaseOrderRecord(PurchaseOrderRequest request, UUID tenantId) {
        RetailSupplier supplier = suppliers.findByIdAndTenantId(request.supplierId(), tenantId)
                .orElseThrow(() -> Errors.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));

        RetailPurchaseOrder order = new RetailPurchaseOrder();
        order.setReference(nextReference("PO"));
        order.setSupplierId(supplier.getId());
        order.setStatus(RetailPurchaseOrderStatus.OPEN);
        order.setNotes(blankToNull(request.notes()));
        BigDecimal total = BigDecimal.ZERO;
        List<RetailPurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderItemRequest itemReq : request.items()) {
            if (itemReq == null || itemReq.productId() == null || itemReq.quantity() <= 0) {
                throw Errors.badRequest("INVALID_PO_ITEM", "Each PO item needs productId and positive quantity");
            }
            StoreItem product = storeItems.findById(itemReq.productId())
                    .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
            requireSameTenant(product.getTenantId(), tenantId, "Product belongs to another school");
            BigDecimal unitCost = itemReq.unitCost() == null ? BigDecimal.ZERO : itemReq.unitCost();
            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(lineTotal);
            RetailPurchaseOrderItem item = new RetailPurchaseOrderItem();
            item.setProductId(product.getId());
            item.setQuantity(itemReq.quantity());
            item.setQuantityReceived(0);
            item.setUnitCost(unitCost);
            item.setLineTotal(lineTotal);
            items.add(item);
        }
        order.setTotal(total);
        RetailPurchaseOrder saved = purchaseOrders.save(order);
        for (RetailPurchaseOrderItem item : items) {
            item.setOrderId(saved.getId());
            purchaseOrderItems.save(item);
        }
        return toPurchaseOrderResponse(saved);
    }

    public PurchaseOrderResponse approvePurchaseOrder(UUID id, String idempotencyKey) {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.UPDATE);
        UUID tenantId = tenantId();
        String hash = purchaseOrderActionHash("PO_APPROVE", id, idempotencyKey);
        return withIdempotency(tenantId, "PO_APPROVE", idempotencyKey, hash,
                () -> approvePurchaseOrderRecord(id, tenantId),
                PurchaseOrderResponse::id, PurchaseOrderResponse.class);
    }

    private PurchaseOrderResponse approvePurchaseOrderRecord(UUID id, UUID tenantId) {
        RetailPurchaseOrder order = purchaseOrders.findForUpdate(id, tenantId)
                .orElseThrow(() -> Errors.notFound("PO_NOT_FOUND", "Purchase order not found"));
        if (order.getStatus() != RetailPurchaseOrderStatus.OPEN) {
            throw Errors.conflict("PO_NOT_OPEN", "Only OPEN purchase orders can be approved");
        }
        order.setStatus(RetailPurchaseOrderStatus.APPROVED);
        order.setApprovedBy(currentUser().id());
        order.setApprovedAt(Instant.now());
        return toPurchaseOrderResponse(purchaseOrders.save(order));
    }

    public PurchaseOrderResponse receivePurchaseOrder(UUID id, ReceiveOrderRequest request, String idempotencyKey) {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.UPDATE);
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw Errors.badRequest("RECEIVE_ITEMS_REQUIRED", "At least one receive item is required");
        }
        UUID tenantId = tenantId();
        String hash = receiveOrderHash(id, request, idempotencyKey);
        return withIdempotency(tenantId, "PO_RECEIVE", idempotencyKey, hash,
                () -> receivePurchaseOrderRecord(id, request, tenantId),
                PurchaseOrderResponse::id, PurchaseOrderResponse.class);
    }

    private PurchaseOrderResponse receivePurchaseOrderRecord(UUID id, ReceiveOrderRequest request, UUID tenantId) {
        UUID actor = currentUser().id();
        RetailPurchaseOrder order = purchaseOrders.findForUpdate(id, tenantId)
                .orElseThrow(() -> Errors.notFound("PO_NOT_FOUND", "Purchase order not found"));
        if (order.getStatus() != RetailPurchaseOrderStatus.APPROVED && order.getStatus() != RetailPurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw Errors.conflict("PO_NOT_RECEIVABLE", "Purchase order is not in a receivable state");
        }
        Map<UUID, RetailPurchaseOrderItem> itemsById = purchaseOrderItems.lockByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(RetailPurchaseOrderItem::getId, i -> i));

        for (ReceiveOrderItemRequest receive : request.items()) {
            if (receive == null || receive.orderItemId() == null) {
                throw Errors.badRequest("ORDER_ITEM_REQUIRED", "orderItemId is required");
            }
            if (receive.quantity() <= 0) {
                throw Errors.badRequest("INVALID_QUANTITY", "Receive quantity must be positive");
            }
            if (receive.batchNumber() == null || receive.batchNumber().isBlank()) {
                throw Errors.badRequest("BATCH_REQUIRED", "batchNumber is required");
            }
            RetailPurchaseOrderItem item = itemsById.get(receive.orderItemId());
            if (item == null) {
                throw Errors.notFound("PO_ITEM_NOT_FOUND", "Purchase order item not found");
            }
            int remaining = item.getQuantity() - item.getQuantityReceived();
            if (receive.quantity() > remaining) {
                throw Errors.badRequest("RECEIVE_EXCEEDS_ORDER", "Receive quantity exceeds remaining order quantity");
            }
            StoreItem product = storeItems.findForUpdate(tenantId, item.getProductId())
                    .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
            requireSameTenant(product.getTenantId(), tenantId, "Product belongs to another school");

            RetailBatch batch = new RetailBatch();
            batch.setProductId(product.getId());
            batch.setBatchNumber(receive.batchNumber().trim());
            batch.setQuantity(receive.quantity());
            batch.setQuantityRemaining(receive.quantity());
            batch.setUnitCost(item.getUnitCost());
            batch.setExpiryDate(receive.expiryDate());
            batch.setReceivedAt(Instant.now());
            batch.setSupplierId(order.getSupplierId());
            batch.setSource("PURCHASE_ORDER");
            batch.setReference(order.getReference());
            RetailBatch savedBatch = batches.save(batch);

            int quantityAfter = product.getStockQuantity() + receive.quantity();
            product.setStockQuantity(quantityAfter);
            storeItems.save(product);

            RetailStockMovement movement = new RetailStockMovement();
            movement.setProductId(product.getId());
            movement.setType(RetailStockMovementType.RESTOCK);
            movement.setQuantity(receive.quantity());
            movement.setQuantityAfter(quantityAfter);
            movement.setReference(order.getReference());
            movement.setReason("Purchase order receive");
            movement.setBatchId(savedBatch.getId());
            movement.setPerformedBy(actor);
            movements.save(movement);

            item.setQuantityReceived(item.getQuantityReceived() + receive.quantity());
            purchaseOrderItems.save(item);
        }

        boolean fullyReceived = purchaseOrderItems.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .allMatch(i -> i.getQuantityReceived() >= i.getQuantity());
        order.setStatus(fullyReceived ? RetailPurchaseOrderStatus.RECEIVED : RetailPurchaseOrderStatus.PARTIALLY_RECEIVED);
        purchaseOrders.save(order);
        return toPurchaseOrderResponse(order);
    }

    public PurchaseOrderResponse cancelPurchaseOrder(UUID id, String idempotencyKey) {
        accessPolicy.require("purchase-requests", ResourceAccessPolicy.Action.UPDATE);
        UUID tenantId = tenantId();
        String hash = purchaseOrderActionHash("PO_CANCEL", id, idempotencyKey);
        return withIdempotency(tenantId, "PO_CANCEL", idempotencyKey, hash,
                () -> cancelPurchaseOrderRecord(id, tenantId),
                PurchaseOrderResponse::id, PurchaseOrderResponse.class);
    }

    private PurchaseOrderResponse cancelPurchaseOrderRecord(UUID id, UUID tenantId) {
        RetailPurchaseOrder order = purchaseOrders.findForUpdate(id, tenantId)
                .orElseThrow(() -> Errors.notFound("PO_NOT_FOUND", "Purchase order not found"));
        if (order.getStatus() == RetailPurchaseOrderStatus.RECEIVED
                || order.getStatus() == RetailPurchaseOrderStatus.CANCELLED) {
            throw Errors.conflict("PO_NOT_CANCELLABLE", "Purchase order cannot be cancelled in its current state");
        }
        order.setStatus(RetailPurchaseOrderStatus.CANCELLED);
        return toPurchaseOrderResponse(purchaseOrders.save(order));
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> terminals() {
        accessPolicy.require("store-items", ResourceAccessPolicy.Action.READ);
        UUID tenantId = tenantId();
        List<PmTerminal> rows = terminals.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, String> merchantCodes = merchants.findAll().stream()
                .filter(m -> tenantId.equals(m.getTenantId()))
                .collect(Collectors.toMap(PmMerchant::getId, PmMerchant::getCode, (a, b) -> a));
        return rows.stream()
                .map(t -> new TerminalResponse(t.getId(), t.getCode(),
                        merchantCodes.getOrDefault(t.getMerchantId(), ""), t.getStatus().name(), t.getLastSeenAt()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private record PricedLine(UUID productId, String sku, String name, int quantity, long unitPrice, long lineTotal, int availableStock, long unitCost) {}
    private record PricedCart(List<PricedLine> lines, long subtotal, long discountAmount, long total, String fingerprint) {}
    private record ConsumedSaleLine(RetailSaleItem item, List<RetailSaleItemBatch> allocations) {}
    private record ReturnLinePlan(RetailSaleItem saleItem, int quantity, String disposition) {}

    private PricedCart priceCart(UUID tenantId, List<com.skulify.retail.dto.RetailDtos.PreviewItemRequest> items, String discountCode) {
        if (items == null || items.isEmpty()) {
            throw Errors.badRequest("ITEMS_REQUIRED", "At least one item is required");
        }
        Map<UUID, Integer> quantities = new HashMap<>();
        for (var item : items) {
            if (item == null || item.productId() == null) {
                throw Errors.badRequest("PRODUCT_REQUIRED", "productId is required");
            }
            if (item.quantity() <= 0) {
                throw Errors.badRequest("INVALID_QUANTITY", "Quantity must be positive");
            }
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<PricedLine> lines = new ArrayList<>();
        long subtotal = 0L;
        for (var entry : quantities.entrySet()) {
            StoreItem product = storeItems.findById(entry.getKey())
                    .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found: " + entry.getKey()));
            requireSameTenant(product.getTenantId(), tenantId, "Product belongs to another school");
            if (!product.isActive()) {
                throw Errors.badRequest("ITEM_INACTIVE", "Store item is inactive: " + product.getName());
            }
            int available = availableStock(tenantId, product.getId());
            if (entry.getValue() > available) {
                throw Errors.conflict("OUT_OF_STOCK",
                        "Insufficient stock for " + product.getName() + ": requested " + entry.getValue() + ", available " + available);
            }
            long unitPrice = toShillings(product.getPrice());
            long lineTotal = multiplyExact(unitPrice, entry.getValue());
            subtotal = addExact(subtotal, lineTotal);
            long unitCost = estimateUnitCost(tenantId, product.getId());
            lines.add(new PricedLine(product.getId(), product.getSku(), product.getName(), entry.getValue(), unitPrice, lineTotal, available, unitCost));
        }
        lines.sort(Comparator.comparing(PricedLine::name, String.CASE_INSENSITIVE_ORDER));

        long discountAmount = resolveDiscount(tenantId, discountCode, subtotal);
        long total = Math.max(0L, subtotal - discountAmount);
        String fingerprint = pricingFingerprint(lines, discountCode, discountAmount, total);
        return new PricedCart(List.copyOf(lines), subtotal, discountAmount, total, fingerprint);
    }

    private PreviewResponse toPreviewResponse(PricedCart cart, String merchantCode, String terminalCode) {
        List<PreviewLineResponse> lines = cart.lines().stream()
                .map(l -> new PreviewLineResponse(l.productId(), l.sku(), l.name(), l.quantity(), l.unitPrice(), l.lineTotal(), l.availableStock()))
                .toList();
        return new PreviewResponse(cart.fingerprint(), cart.subtotal(), cart.discountAmount(), cart.total(), "TZS", lines, merchantCode, terminalCode);
    }

    private long resolveDiscount(UUID tenantId, String discountCode, long subtotal) {
        if (discountCode == null || discountCode.isBlank()) {
            return 0L;
        }
        RetailDiscount discount = discounts.findByTenantIdAndCodeIgnoreCase(tenantId, discountCode.trim())
                .orElseThrow(() -> Errors.badRequest("DISCOUNT_NOT_FOUND", "Discount code not found"));
        if (!"ACTIVE".equalsIgnoreCase(discount.getStatus())) {
            throw Errors.badRequest("DISCOUNT_INACTIVE", "Discount is not active");
        }
        if (discount.getExpiresAt() != null && discount.getExpiresAt().isBefore(Instant.now())) {
            throw Errors.badRequest("DISCOUNT_EXPIRED", "Discount has expired");
        }
        long amount;
        if (discount.getKind() == RetailDiscountKind.PERCENT) {
            amount = BigDecimal.valueOf(subtotal)
                    .multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } else {
            amount = toShillings(discount.getValue());
        }
        if (amount < 0) amount = 0;
        if (amount > subtotal) amount = subtotal;
        return amount;
    }

    private String pricingFingerprint(List<PricedLine> lines, String discountCode, long discountAmount, long total) {
        String payload = lines.stream()
                .map(l -> l.productId() + ":" + l.quantity() + ":" + l.unitPrice() + ":" + l.lineTotal())
                .collect(Collectors.joining("|"))
                + "|discount=" + (discountCode == null ? "" : discountCode.trim().toUpperCase())
                + "|discountAmount=" + discountAmount
                + "|total=" + total;
        return sha256(payload);
    }

    private List<UUID> distinctProductIds(List<com.skulify.retail.dto.RetailDtos.PreviewItemRequest> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(Objects::nonNull)
                .map(com.skulify.retail.dto.RetailDtos.PreviewItemRequest::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private long proportionalAmount(long total, int quantity, int totalQuantity) {
        if (total <= 0 || quantity <= 0 || totalQuantity <= 0) return 0L;
        try {
            return BigDecimal.valueOf(total)
                    .multiply(BigDecimal.valueOf(quantity))
                    .divide(BigDecimal.valueOf(totalQuantity), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw Errors.badRequest("AMOUNT_OVERFLOW", "Refund amount overflow");
        }
    }

    /** Applies the sale-level discount once, so a full return always refunds exactly the paid total. */
    private long refundAmountForSubtotal(RetailSale sale, long requestedSubtotal) {
        if (sale.getSubtotal() <= 0 || requestedSubtotal <= 0) return 0L;
        if (requestedSubtotal >= sale.getSubtotal()) return sale.getTotal();
        try {
            return BigDecimal.valueOf(sale.getTotal())
                    .multiply(BigDecimal.valueOf(requestedSubtotal))
                    .divide(BigDecimal.valueOf(sale.getSubtotal()), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw Errors.badRequest("AMOUNT_OVERFLOW", "Refund amount overflow");
        }
    }

    private int availableStock(UUID tenantId, UUID productId) {
        return batches.findFefoBatches(tenantId, productId, LocalDate.now()).stream()
                .mapToInt(RetailBatch::getQuantityRemaining)
                .sum();
    }

    private ConsumedSaleLine consumeFefoForSale(UUID tenantId, UUID actor, PricedLine line, String reference) {
        StoreItem product = storeItems.findById(line.productId())
                .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
        List<RetailBatch> fefo = batches.lockFefoBatches(tenantId, line.productId(), LocalDate.now());
        int remaining = line.quantity();
        List<RetailSaleItemBatch> allocations = new ArrayList<>();
        long costTotal = 0L;
        int costQty = 0;
        for (RetailBatch batch : fefo) {
            if (remaining <= 0) break;
            int take = Math.min(remaining, batch.getQuantityRemaining());
            if (take <= 0) continue;
            batch.setQuantityRemaining(batch.getQuantityRemaining() - take);
            batches.save(batch);

            int quantityAfter = Math.max(0, product.getStockQuantity() - take);
            product.setStockQuantity(quantityAfter);

            RetailStockMovement movement = new RetailStockMovement();
            movement.setProductId(product.getId());
            movement.setType(RetailStockMovementType.SALE);
            movement.setQuantity(-take);
            movement.setQuantityAfter(quantityAfter);
            movement.setReference(reference);
            movement.setReason("Retail sale");
            movement.setBatchId(batch.getId());
            movement.setPerformedBy(actor);
            movements.save(movement);

            RetailSaleItemBatch allocation = new RetailSaleItemBatch();
            allocation.setId(new RetailSaleItemBatchId(UUID.randomUUID(), batch.getId())); // temp saleItemId
            allocation.setQuantity(take);
            allocations.add(allocation);

            long batchUnitCost = toShillings(batch.getUnitCost());
            costTotal += multiplyExact(batchUnitCost, take);
            costQty += take;
            remaining -= take;
        }
        if (remaining > 0) {
            throw Errors.conflict("OUT_OF_STOCK", "Insufficient FEFO stock for " + line.name());
        }
        storeItems.save(product);

        RetailSaleItem item = new RetailSaleItem();
        item.setProductId(line.productId());
        item.setSku(line.sku());
        item.setName(line.name());
        item.setQuantity(line.quantity());
        item.setUnitPrice(line.unitPrice());
        item.setLineTotal(line.lineTotal());
        item.setUnitCost(costQty == 0 ? 0L : Math.round((double) costTotal / costQty));
        item.setPriceSource("CATALOG");
        return new ConsumedSaleLine(item, allocations);
    }

    private List<RetailRefundItem> applyReturnToStock(
            UUID tenantId, UUID actor, RetailSale sale, ReturnLinePlan plan, String reason) {
        RetailSaleItem saleItem = plan.saleItem();
        StoreItem product = storeItems.findForUpdate(tenantId, saleItem.getProductId())
                .orElseThrow(() -> Errors.notFound("PRODUCT_NOT_FOUND", "Store item not found"));
        String itemLabel = (saleItem.getSku() == null || saleItem.getSku().isBlank())
                ? saleItem.getName()
                : saleItem.getSku() + " - " + saleItem.getName();
        String movementReason = "Returned " + itemLabel
                + (reason == null || reason.isBlank() ? "" : "; " + reason.trim());
        boolean restock = "RESTOCK".equals(plan.disposition());
        List<RetailRefundItem> rows = new ArrayList<>();
        int remaining = plan.quantity();

        List<RetailSaleItemBatch> allocations = saleItemBatches.findByIdSaleItemId(saleItem.getId());
        for (RetailSaleItemBatch allocation : allocations) {
            if (remaining <= 0) break;
            UUID batchId = allocation.getId().getBatchId();
            RetailBatch batch = batches.findForUpdate(tenantId, batchId).orElse(null);
            if (batch == null) continue;
            int alreadyReturned = refundItems.sumReturnedByBatch(tenantId, saleItem.getId(), batchId);
            int available = allocation.getQuantity() - alreadyReturned;
            int take = Math.min(remaining, Math.max(0, available));
            if (take <= 0) continue;

            if (restock) {
                if (batch.getQuantityRemaining() + take > batch.getQuantity()) {
                    throw Errors.conflict("RETURN_ALLOCATION_INVALID", "Returned quantity exceeds the original batch");
                }
                batch.setQuantityRemaining(batch.getQuantityRemaining() + take);
                batches.save(batch);

                int quantityAfter = product.getStockQuantity() + take;
                product.setStockQuantity(quantityAfter);
                storeItems.save(product);

                saveMovement(product.getId(), RetailStockMovementType.RETURN, take, quantityAfter,
                        sale.getReference(), movementReason, batch.getId(), actor);
            }
            rows.add(refundRow(tenantId, sale, saleItem, batch.getId(), take));
            remaining -= take;
        }

        if (remaining > 0) {
            if (!restock) {
                throw Errors.conflict("RETURN_ALLOCATION_INVALID", "Returned quantity exceeds the original allocations");
            }
            RetailBatch batch = new RetailBatch();
            batch.setProductId(product.getId());
            batch.setBatchNumber("RTN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            batch.setQuantity(remaining);
            batch.setQuantityRemaining(remaining);
            batch.setUnitCost(BigDecimal.ZERO);
            batch.setReceivedAt(Instant.now());
            batch.setSource("RETURN");
            batch.setReference(sale.getReference());
            RetailBatch savedBatch = batches.save(batch);

            int quantityAfter = product.getStockQuantity() + remaining;
            product.setStockQuantity(quantityAfter);
            storeItems.save(product);
            saveMovement(product.getId(), RetailStockMovementType.RETURN, remaining, quantityAfter,
                    sale.getReference(), movementReason, savedBatch.getId(), actor);
            rows.add(refundRow(tenantId, sale, saleItem, savedBatch.getId(), remaining));
        }

        if (!restock) {
            // The sale already removed the stock. Paired RETURN/WRITE_OFF rows keep
            // the movement ledger net zero while preserving the audit trail.
            int quantityAfter = product.getStockQuantity();
            saveMovement(product.getId(), RetailStockMovementType.RETURN, plan.quantity(),
                    quantityAfter + plan.quantity(), sale.getReference(),
                    "Write-off return: " + itemLabel + "; sale already reversed", null, actor);
            saveMovement(product.getId(), RetailStockMovementType.WRITE_OFF, -plan.quantity(),
                    quantityAfter, sale.getReference(), movementReason, null, actor);
        }
        return rows;
    }

    private void saveMovement(
            UUID productId,
            RetailStockMovementType type,
            int quantity,
            int quantityAfter,
            String reference,
            String reason,
            UUID batchId,
            UUID actor) {
        RetailStockMovement movement = new RetailStockMovement();
        movement.setProductId(productId);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setQuantityAfter(quantityAfter);
        movement.setReference(reference);
        movement.setReason(reason);
        movement.setBatchId(batchId);
        movement.setPerformedBy(actor);
        movements.save(movement);
    }

    private RetailRefundItem refundRow(
            UUID tenantId, RetailSale sale, RetailSaleItem saleItem, UUID batchId, int quantity) {
        RetailRefundItem row = new RetailRefundItem();
        row.setTenantId(tenantId);
        row.setSaleId(sale.getId());
        row.setSaleItemId(saleItem.getId());
        row.setBatchId(batchId);
        row.setQuantity(quantity);
        return row;
    }

    private Map<UUID, Integer> returnedQuantitiesBySaleItem(UUID tenantId, UUID saleId) {
        Map<UUID, Integer> result = new HashMap<>();
        for (RetailRefundItem row : refundItems.findBySaleId(saleId)) {
            requireSameTenant(row.getTenantId(), tenantId, "Refund item belongs to another school");
            result.merge(row.getSaleItemId(), row.getQuantity(), Integer::sum);
        }
        return result;
    }

    private void deductFromBatches(UUID tenantId, UUID productId, int quantity) {
        int remaining = quantity;
        for (RetailBatch batch : batches.lockFefoBatches(tenantId, productId, LocalDate.now())) {
            if (remaining <= 0) break;
            int take = Math.min(remaining, batch.getQuantityRemaining());
            batch.setQuantityRemaining(batch.getQuantityRemaining() - take);
            batches.save(batch);
            remaining -= take;
        }
        // If not enough FEFO batches, deduct from any remaining batch
        if (remaining > 0) {
            for (RetailBatch batch : batches.lockAvailableBatches(tenantId, productId)) {
                if (remaining <= 0) break;
                if (batch.getQuantityRemaining() <= 0) continue;
                int take = Math.min(remaining, batch.getQuantityRemaining());
                batch.setQuantityRemaining(batch.getQuantityRemaining() - take);
                batches.save(batch);
                remaining -= take;
            }
        }
    }

    private SaleResponse toSaleResponse(RetailSale sale, boolean replayed, long balanceAfter) {
        List<SaleItemResponse> items = saleItems.findBySaleIdOrderByCreatedAtAsc(sale.getId()).stream()
                .map(this::toSaleItemResponse)
                .toList();
        return new SaleResponse(
                sale.getId(), sale.getReference(), sale.getReceiptNo(), sale.getStudentId(),
                studentName(sale.getStudentId()), cardLabel(sale.getCardId()), terminalCode(sale.getTerminalId()),
                merchantCode(sale.getMerchantId()), sale.getSubtotal(), sale.getDiscount(), sale.getTotal(),
                "TZS", sale.getStatus().name(), sale.getPaymentMethod(), sale.getPocketPurchaseId(),
                sale.getPricingFingerprint(), replayed, balanceAfter, sale.getCreatedAt(), sale.getNotes(), items);
    }

    private SaleItemResponse toSaleItemResponse(RetailSaleItem item) {
        return new SaleItemResponse(item.getId(), item.getProductId(), item.getSku(), item.getName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal(), item.getUnitCost(), item.getPriceSource());
    }

    private BatchResponse toBatchResponse(RetailBatch batch, String productName) {
        return new BatchResponse(batch.getId(), batch.getProductId(), productName, batch.getBatchNumber(),
                batch.getQuantity(), batch.getQuantityRemaining(), batch.getUnitCost(), batch.getExpiryDate(),
                batch.getReceivedAt(), batch.getSupplierId(), batch.getSource(), batch.getReference());
    }

    private StockMovementResponse toMovementResponse(RetailStockMovement movement, String productName) {
        String batchNumber = movement.getBatchId() == null ? ""
                : batches.findById(movement.getBatchId()).map(RetailBatch::getBatchNumber).orElse("");
        String actor = movement.getPerformedBy() == null ? "System"
                : users.findById(movement.getPerformedBy())
                        .map(u -> u.getFullName() == null ? u.getUsername() : u.getFullName())
                        .orElse("Staff");
        return new StockMovementResponse(movement.getId(), movement.getProductId(), productName,
                movement.getType().name(), movement.getQuantity(), movement.getQuantityAfter(),
                movement.getReference(), movement.getReason(), batchNumber, actor,
                movement.getCreatedAt());
    }

    private PurchaseOrderResponse toPurchaseOrderResponse(RetailPurchaseOrder order) {
        String supplierName = suppliers.findById(order.getSupplierId()).map(RetailSupplier::getName).orElse("");
        List<PurchaseOrderItemResponse> items = purchaseOrderItems.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(i -> {
                    StoreItem product = storeItems.findById(i.getProductId()).orElse(null);
                    return new PurchaseOrderItemResponse(i.getId(), i.getProductId(),
                            product == null ? "" : product.getName(), product == null ? "" : product.getSku(),
                            i.getQuantity(), i.getQuantityReceived(), i.getUnitCost(), i.getLineTotal());
                })
                .toList();
        return new PurchaseOrderResponse(order.getId(), order.getReference(), order.getSupplierId(), supplierName,
                order.getStatus().name(), order.getTotal(), order.getNotes(), order.getApprovedBy(),
                order.getApprovedAt(), order.getCreatedAt(), items);
    }

    private long saleBalanceAfter(RetailSale sale) {
        if (sale.getPocketPurchaseId() == null) return 0L;
        return pmPurchases.findById(sale.getPocketPurchaseId())
                .map(p -> p.getBalanceAfter() == null ? 0L : p.getBalanceAfter())
                .orElse(0L);
    }

    private String studentName(UUID studentId) {
        return students.findById(studentId)
                .map(s -> (nullToEmpty(s.getFirstName()) + " " + nullToEmpty(s.getLastName())).trim())
                .filter(s -> !s.isBlank())
                .orElse("Student");
    }

    private String cardLabel(UUID cardId) {
        if (cardId == null) return "";
        return cards.findById(cardId).map(c -> c.getUidLabel() == null ? "" : c.getUidLabel()).orElse("");
    }

    private String merchantCode(UUID merchantId) {
        if (merchantId == null) return "";
        return merchants.findById(merchantId).map(PmMerchant::getCode).orElse("");
    }

    private String terminalCode(UUID terminalId) {
        if (terminalId == null) return "";
        return terminals.findById(terminalId).map(PmTerminal::getCode).orElse("");
    }

    private Map<UUID, String> productNames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return storeItems.findAllById(ids).stream()
                .collect(Collectors.toMap(StoreItem::getId, StoreItem::getName, (a, b) -> a));
    }

    private long estimateUnitCost(UUID tenantId, UUID productId) {
        List<RetailBatch> fefo = batches.findFefoBatches(tenantId, productId, LocalDate.now());
        if (fefo.isEmpty()) return 0L;
        return toShillings(fefo.get(0).getUnitCost());
    }

    private long toShillings(BigDecimal value) {
        if (value == null) return 0L;
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private SaleRequest validateSaleRequest(SaleRequest request) {
        if (request == null) throw Errors.badRequest("SALE_REQUIRED", "Sale request is required");
        if (request.studentId() == null) throw Errors.badRequest("STUDENT_REQUIRED", "studentId is required");
        if (request.cardUid() == null || request.cardUid().isBlank()) throw Errors.badRequest("CARD_UID_REQUIRED", "cardUid is required");
        if (request.terminalCode() == null || request.terminalCode().isBlank()) throw Errors.badRequest("TERMINAL_REQUIRED", "terminalCode is required");
        if (request.deviceKey() == null || request.deviceKey().isBlank()) throw Errors.badRequest("DEVICE_KEY_REQUIRED", "deviceKey is required");
        if (request.nonce() == null || request.nonce().isBlank()) throw Errors.badRequest("NONCE_REQUIRED", "nonce is required");
        if (request.items() == null || request.items().isEmpty()) throw Errors.badRequest("ITEMS_REQUIRED", "At least one item is required");
        return request;
    }

    private String saleRequestHash(SaleRequest request, String idempotencyKey) {
        String items = request.items().stream()
                .sorted(Comparator.comparing(i -> String.valueOf(i.productId())))
                .map(i -> i.productId() + ":" + i.quantity())
                .collect(Collectors.joining(","));
        String payload = String.join("|",
                nullToEmpty(request.studentId()),
                nullToEmpty(request.cardUid()),
                nullToEmpty(request.terminalCode()),
                nullToEmpty(request.deviceKey()),
                items,
                nullToEmpty(request.discountCode()),
                nullToEmpty(request.pricingFingerprint()),
                nullToEmpty(request.notes()),
                nullToEmpty(request.nonce()),
                nullToEmpty(request.requestTimestamp()),
                nullToEmpty(idempotencyKey));
        return sha256(payload);
    }

    private <T> T withIdempotency(
            UUID tenantId,
            String scope,
            String idempotencyKey,
            String requestHash,
            Supplier<T> action,
            Function<T, UUID> resultRef,
            Class<T> responseType) {
        requireKey(idempotencyKey);
        String lockKey = tenantId + "|" + scope + "|" + idempotencyKey;
        ReentrantLock lock = writeLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Keep the in-process lock until the surrounding transaction commits;
            // releasing it in a finally block would let a retry read a row that is
            // still uncommitted and fail on the unique key instead of replaying.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    lock.unlock();
                }
            });
        }
        try {
            var stored = writeIdempotency.findForWrite(tenantId, scope, idempotencyKey);
            if (stored.isPresent()) {
                RetailWriteIdempotency row = stored.get();
                if (!requestHash.equals(row.getRequestHash())) {
                    throw Errors.conflict("IDEMPOTENCY_CONFLICT",
                            "Idempotency key was reused with different request details");
                }
                if (row.getResultRef() == null || row.getResultJson() == null) {
                    throw Errors.conflict("IDEMPOTENCY_PENDING",
                            "The original request has not finished; retry shortly");
                }
                return deserializeResult(row.getResultJson(), responseType, true);
            }

            RetailWriteIdempotency row = new RetailWriteIdempotency();
            row.setId(new RetailWriteIdempotencyId(tenantId, scope, idempotencyKey));
            row.setRequestHash(requestHash);
            row.setCreatedAt(Instant.now());
            writeIdempotency.saveAndFlush(row);

            T result = action.get();
            row.setResultRef(resultRef.apply(result));
            row.setResultJson(objectMapper.writeValueAsString(result));
            writeIdempotency.save(row);
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store idempotent retail response", exception);
        } finally {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                lock.unlock();
            }
        }
    }

    private <T> T deserializeResult(String json, Class<T> responseType, boolean replayed) {
        try {
            if (!replayed) {
                return objectMapper.readValue(json, responseType);
            }
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode && objectNode.has("replayed")) {
                objectNode.put("replayed", true);
            }
            return objectMapper.treeToValue(node, responseType);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay idempotent retail response", exception);
        }
    }

    private SaleResponse loadSale(UUID tenantId, UUID id) {
        RetailSale sale = sales.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> Errors.notFound("SALE_NOT_FOUND", "Retail sale not found"));
        return toSaleResponse(sale, true, saleBalanceAfter(sale));
    }

    private ReturnResponse loadReturn(UUID tenantId, UUID id) {
        RetailRefund refund = refunds.findById(id)
                .orElseThrow(() -> Errors.notFound("REFUND_NOT_FOUND", "Retail refund not found"));
        requireSameTenant(refund.getTenantId(), tenantId, "Refund belongs to another school");
        UUID walletId = sales.findById(refund.getSaleId())
                .map(RetailSale::getWalletId).orElse(null);
        long balanceAfter = walletId == null ? 0L
                : wallets.findById(walletId).map(w -> w.getBalance()).orElse(0L);
        return new ReturnResponse(refund.getSaleId(), refund.getId(), refund.getAmount(),
                balanceAfter, true, refund.getStatus().name(), refund.getCreatedAt());
    }

    private BatchResponse loadBatch(UUID tenantId, UUID id) {
        RetailBatch batch = batches.findById(id)
                .orElseThrow(() -> Errors.notFound("BATCH_NOT_FOUND", "Batch not found"));
        requireSameTenant(batch.getTenantId(), tenantId, "Batch belongs to another school");
        String name = storeItems.findById(batch.getProductId())
                .map(StoreItem::getName).orElse("");
        return toBatchResponse(batch, name);
    }

    private StockMovementResponse loadMovement(UUID tenantId, UUID id) {
        RetailStockMovement movement = movements.findById(id)
                .orElseThrow(() -> Errors.notFound("MOVEMENT_NOT_FOUND", "Stock movement not found"));
        requireSameTenant(movement.getTenantId(), tenantId, "Movement belongs to another school");
        String name = storeItems.findById(movement.getProductId())
                .map(StoreItem::getName).orElse("");
        return toMovementResponse(movement, name);
    }

    private SupplierResponse loadSupplier(UUID tenantId, UUID id) {
        RetailSupplier supplier = suppliers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> Errors.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));
        return toSupplierResponse(supplier);
    }

    private DiscountResponse loadDiscount(UUID tenantId, UUID id) {
        RetailDiscount discount = discounts.findById(id)
                .orElseThrow(() -> Errors.notFound("DISCOUNT_NOT_FOUND", "Discount not found"));
        requireSameTenant(discount.getTenantId(), tenantId, "Discount belongs to another school");
        return toDiscountResponse(discount);
    }

    private PurchaseOrderResponse loadPurchaseOrder(UUID tenantId, UUID id) {
        RetailPurchaseOrder order = purchaseOrders.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> Errors.notFound("PO_NOT_FOUND", "Purchase order not found"));
        return toPurchaseOrderResponse(order);
    }

    private SupplierResponse toSupplierResponse(RetailSupplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getCode(), supplier.getName(),
                supplier.getPhone(), supplier.getEmail(), supplier.getStatus());
    }

    private DiscountResponse toDiscountResponse(RetailDiscount discount) {
        return new DiscountResponse(discount.getId(), discount.getCode(), discount.getKind().name(),
                discount.getValue(), discount.getStatus(), discount.getExpiresAt());
    }

    private String restockHash(RestockRequest request, String key) {
        return sha256(String.join("|", "RESTOCK", key,
                nullToEmpty(request.productId()), nullToEmpty(request.quantity()),
                nullToEmpty(request.unitCost()), nullToEmpty(request.batchNumber()),
                nullToEmpty(request.expiryDate()), nullToEmpty(request.supplierId()),
                nullToEmpty(request.reference())));
    }

    private String adjustHash(AdjustRequest request, String key) {
        return sha256(String.join("|", "ADJUST", key,
                nullToEmpty(request.productId()), nullToEmpty(request.quantity()),
                nullToEmpty(request.reason())));
    }

    private String supplierHash(SupplierRequest request, String key) {
        return sha256(String.join("|", "SUPPLIER", key,
                nullToEmpty(request.code()), nullToEmpty(request.name()),
                nullToEmpty(request.phone()), nullToEmpty(request.email()),
                nullToEmpty(request.status())));
    }

    private String discountHash(DiscountRequest request, String key) {
        return sha256(String.join("|", "DISCOUNT", key,
                nullToEmpty(request.code()), nullToEmpty(request.kind()),
                nullToEmpty(request.value()), nullToEmpty(request.status()),
                nullToEmpty(request.expiresAt())));
    }

    private String purchaseOrderHash(PurchaseOrderRequest request, String key) {
        String items = request == null || request.items() == null ? "" : request.items().stream()
                .map(i -> nullToEmpty(i.productId()) + ":" + nullToEmpty(i.quantity())
                        + ":" + nullToEmpty(i.unitCost()))
                .sorted()
                .collect(Collectors.joining(","));
        return sha256(String.join("|", "PO_CREATE", key,
                request == null ? "" : nullToEmpty(request.supplierId()),
                request == null ? "" : nullToEmpty(request.notes()), items));
    }

    private String purchaseOrderActionHash(String action, UUID orderId, String key) {
        return sha256(String.join("|", action, key, nullToEmpty(orderId)));
    }

    private String receiveOrderHash(UUID orderId, ReceiveOrderRequest request, String key) {
        String items = request == null || request.items() == null ? "" : request.items().stream()
                .map(i -> nullToEmpty(i.orderItemId()) + ":" + nullToEmpty(i.quantity())
                        + ":" + nullToEmpty(i.batchNumber()) + ":" + nullToEmpty(i.expiryDate()))
                .sorted()
                .collect(Collectors.joining(","));
        return sha256(String.join("|", "PO_RECEIVE", key, nullToEmpty(orderId), items));
    }

    private String returnRequestHash(UUID saleId, ReturnRequest request, String key) {
        String items = request == null || request.items() == null ? "" : request.items().stream()
                .map(i -> nullToEmpty(i.saleItemId()) + ":" + nullToEmpty(i.quantity())
                        + ":" + nullToEmpty(i.disposition()))
                .sorted()
                .collect(Collectors.joining(","));
        return sha256(String.join("|", "RETURN", key, nullToEmpty(saleId), items,
                request == null ? "" : nullToEmpty(request.reason())));
    }

    private void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw Errors.badRequest("IDEMPOTENCY_REQUIRED", "An Idempotency-Key header is required");
        }
    }

    private UUID tenantId() {
        AuthenticatedUser user = currentUser();
        return SchoolContext.require(user.tenantId(), user.isSuperAdmin());
    }

    private AuthenticatedUser currentUser() {
        return CurrentUser.get()
                .orElseThrow(() -> Errors.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }

    private void requireSameTenant(UUID entityTenant, UUID expected, String message) {
        if (entityTenant != null && !entityTenant.equals(expected)) {
            throw Errors.forbidden("FORBIDDEN", message);
        }
    }

    private String nextReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private long multiplyExact(long a, int b) {
        try {
            return Math.multiplyExact(a, (long) b);
        } catch (ArithmeticException e) {
            throw Errors.badRequest("AMOUNT_OVERFLOW", "Money amount overflow");
        }
    }

    private long addExact(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw Errors.badRequest("AMOUNT_OVERFLOW", "Money amount overflow");
        }
    }

    private String defaultMerchantCode(UUID tenantId) {
        return merchants.findAll().stream()
                .filter(m -> tenantId.equals(m.getTenantId()))
                .map(PmMerchant::getCode)
                .findFirst()
                .orElse(null);
    }

    private String defaultTerminalCode(UUID tenantId) {
        return terminals.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(PmTerminal::getCode)
                .findFirst()
                .orElse(null);
    }
}
