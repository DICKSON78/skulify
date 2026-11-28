package com.skulify.retail.controller;

import com.skulify.retail.dto.RetailDtos.AdjustRequest;
import com.skulify.retail.dto.RetailDtos.BatchResponse;
import com.skulify.retail.dto.RetailDtos.DiscountRequest;
import com.skulify.retail.dto.RetailDtos.DiscountResponse;
import com.skulify.retail.dto.RetailDtos.LowStockItemResponse;
import com.skulify.retail.dto.RetailDtos.PreviewRequest;
import com.skulify.retail.dto.RetailDtos.PreviewResponse;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderRequest;
import com.skulify.retail.dto.RetailDtos.PurchaseOrderResponse;
import com.skulify.retail.dto.RetailDtos.ReceiveOrderRequest;
import com.skulify.retail.dto.RetailDtos.ReconciliationResponse;
import com.skulify.retail.dto.RetailDtos.RestockRequest;
import com.skulify.retail.dto.RetailDtos.ReturnRequest;
import com.skulify.retail.dto.RetailDtos.ReturnResponse;
import com.skulify.retail.dto.RetailDtos.SaleRequest;
import com.skulify.retail.dto.RetailDtos.SaleResponse;
import com.skulify.retail.dto.RetailDtos.StockMovementResponse;
import com.skulify.retail.dto.RetailDtos.SupplierRequest;
import com.skulify.retail.dto.RetailDtos.SupplierResponse;
import com.skulify.retail.dto.RetailDtos.TerminalResponse;
import com.skulify.retail.service.RetailService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retail")
public class RetailController {

    private final RetailService service;

    public RetailController(RetailService service) {
        this.service = service;
    }

    @PostMapping("/sales/preview")
    public PreviewResponse preview(@RequestBody PreviewRequest request) {
        return service.preview(request);
    }

    @PostMapping("/sales")
    public SaleResponse sale(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SaleRequest request) {
        return service.sale(request, idempotencyKey);
    }

    @GetMapping("/sales")
    public List<SaleResponse> sales(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) Integer limit) {
        return service.listSales(studentId, limit);
    }

    @GetMapping("/sales/{id}")
    public SaleResponse sale(@PathVariable UUID id) {
        return service.getSale(id);
    }

    @GetMapping("/sales/{id}/receipt")
    public Object receipt(@PathVariable UUID id) {
        return service.receipt(id);
    }

    @GetMapping(value = "/sales/{id}/receipt/print", produces = MediaType.TEXT_HTML_VALUE)
    public String receiptPrint(@PathVariable UUID id) {
        return service.receiptHtml(id);
    }

    @PostMapping("/sales/{id}/return")
    public ReturnResponse returnSale(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReturnRequest request) {
        return service.returnSale(id, request, idempotencyKey);
    }

    @PostMapping("/stock/restock")
    public BatchResponse restock(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RestockRequest request) {
        return service.restock(request, idempotencyKey);
    }

    @PostMapping("/stock/adjust")
    public StockMovementResponse adjust(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AdjustRequest request) {
        return service.adjust(request, idempotencyKey);
    }

    @GetMapping("/batches")
    public List<BatchResponse> batches(@RequestParam(required = false) UUID productId) {
        return service.listBatches(productId);
    }

    @GetMapping("/stock-movements")
    public List<StockMovementResponse> movements(@RequestParam(required = false) UUID productId) {
        return service.listMovements(productId);
    }

    @GetMapping("/stock/reconciliation")
    public ReconciliationResponse reconciliation() {
        return service.reconciliation();
    }

    @GetMapping("/low-stock")
    public List<LowStockItemResponse> lowStock() {
        return service.lowStock();
    }

    @GetMapping("/suppliers")
    public List<SupplierResponse> suppliers() {
        return service.listSuppliers();
    }

    @PostMapping("/suppliers")
    public SupplierResponse createSupplier(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SupplierRequest request) {
        return service.createSupplier(request, idempotencyKey);
    }

    @GetMapping("/discounts")
    public List<DiscountResponse> discounts() {
        return service.listDiscounts();
    }

    @PostMapping("/discounts")
    public DiscountResponse createDiscount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody DiscountRequest request) {
        return service.createDiscount(request, idempotencyKey);
    }

    @PostMapping("/purchase-orders")
    public PurchaseOrderResponse createPurchaseOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PurchaseOrderRequest request) {
        return service.createPurchaseOrder(request, idempotencyKey);
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderResponse> purchaseOrders() {
        return service.listPurchaseOrders();
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderResponse purchaseOrder(@PathVariable UUID id) {
        return service.getPurchaseOrder(id);
    }

    @PostMapping("/purchase-orders/{id}/approve")
    public PurchaseOrderResponse approvePurchaseOrder(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.approvePurchaseOrder(id, idempotencyKey);
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public PurchaseOrderResponse receivePurchaseOrder(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReceiveOrderRequest request) {
        return service.receivePurchaseOrder(id, request, idempotencyKey);
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public PurchaseOrderResponse cancelPurchaseOrder(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.cancelPurchaseOrder(id, idempotencyKey);
    }

    @GetMapping("/terminals")
    public List<TerminalResponse> terminals() {
        return service.terminals();
    }
}
