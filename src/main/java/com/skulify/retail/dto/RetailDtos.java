package com.skulify.retail.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RetailDtos {
    private RetailDtos() {}

    public record PreviewItemRequest(UUID productId, int quantity) {}
    public record PreviewRequest(List<PreviewItemRequest> items, String discountCode) {}
    public record PreviewLineResponse(
            UUID productId, String sku, String name, int quantity,
            long unitPrice, long lineTotal, int availableStock) {}
    public record PreviewResponse(
            String pricingFingerprint, long subtotal, long discount, long total, String currency,
            List<PreviewLineResponse> lines, String merchantCode, String terminalCode) {}

    public record SaleRequest(
            UUID studentId, String cardUid, String terminalCode, String deviceKey,
            List<PreviewItemRequest> items, String discountCode, String pricingFingerprint,
            String notes, String nonce, Instant requestTimestamp) {}

    public record SaleItemResponse(
            UUID id, UUID productId, String sku, String name, int quantity,
            long unitPrice, long lineTotal, long unitCost, String priceSource) {}
    public record SaleResponse(
            UUID id, String reference, String receiptNo, UUID studentId, String studentName,
            String cardUid, String terminalCode, String merchantCode,
            long subtotal, long discount, long total, String currency, String status,
            String paymentMethod, UUID pocketPurchaseId, String pricingFingerprint,
            boolean replayed, long balanceAfter, Instant createdAt, String notes,
            List<SaleItemResponse> items) {}

    public record ReturnItemRequest(UUID saleItemId, int quantity, String disposition) {}
    public record ReturnRequest(List<ReturnItemRequest> items, String reason) {}
    public record ReturnResponse(
            UUID saleId, UUID refundId, long amount, long balanceAfter, boolean replayed,
            String status, Instant createdAt) {}

    public record RestockRequest(
            UUID productId, int quantity, BigDecimal unitCost, String batchNumber,
            LocalDate expiryDate, UUID supplierId, String reference) {}
    public record AdjustRequest(UUID productId, int quantity, String reason) {}

    public record BatchResponse(
            UUID id, UUID productId, String productName, String batchNumber,
            int quantity, int quantityRemaining, BigDecimal unitCost,
            LocalDate expiryDate, Instant receivedAt, UUID supplierId, String source, String reference) {}

    public record StockMovementResponse(
            UUID id, UUID productId, String productName, String type, int quantity,
            int quantityAfter, String reference, String reason, String batchNumber,
            String performedBy, Instant createdAt) {}

    public record ReconciliationItemResponse(
            UUID productId, String sku, String name, int cachedStock, long ledgerStock, long delta) {}
    public record ReconciliationResponse(List<ReconciliationItemResponse> items, boolean balanced) {}

    public record LowStockItemResponse(
            UUID productId, String sku, String name, int stockQuantity, int reorderLevel, String location) {}

    public record SupplierRequest(String code, String name, String phone, String email, String status) {}
    public record SupplierResponse(UUID id, String code, String name, String phone, String email, String status) {}

    public record DiscountRequest(String code, String kind, BigDecimal value, String status, Instant expiresAt) {}
    public record DiscountResponse(UUID id, String code, String kind, BigDecimal value, String status, Instant expiresAt) {}

    public record PurchaseOrderItemRequest(UUID productId, int quantity, BigDecimal unitCost) {}
    public record PurchaseOrderRequest(UUID supplierId, String notes, List<PurchaseOrderItemRequest> items) {}
    public record PurchaseOrderItemResponse(
            UUID id, UUID productId, String productName, String sku, int quantity,
            int quantityReceived, BigDecimal unitCost, BigDecimal lineTotal) {}
    public record PurchaseOrderResponse(
            UUID id, String reference, UUID supplierId, String supplierName, String status,
            BigDecimal total, String notes, UUID approvedBy, Instant approvedAt, Instant createdAt,
            List<PurchaseOrderItemResponse> items) {}

    public record ReceiveOrderItemRequest(UUID orderItemId, int quantity, String batchNumber, LocalDate expiryDate) {}
    public record ReceiveOrderRequest(List<ReceiveOrderItemRequest> items) {}

    public record TerminalResponse(
            UUID id, String code, String merchantCode, String status, Instant lastSeenAt) {}

    public record ReceiptResponse(
            String receiptNo, String reference, Instant createdAt, String schoolName,
            String cashier, String studentName, String cardUid, String merchantCode,
            String terminalCode, long subtotal, long discount, long total,
            String currency, String status, String paymentMethod, long balanceAfter,
            List<SaleItemResponse> items, List<ReturnResponse> refunds) {}
}
