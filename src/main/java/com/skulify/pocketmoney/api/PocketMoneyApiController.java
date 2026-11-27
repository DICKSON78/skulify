package com.skulify.pocketmoney.api;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.CardResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.IssueCardRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.LedgerEntryResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.MerchantRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.MerchantResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.OpenWalletRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.PurchaseResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.ReconciliationResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TerminalRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TerminalResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.WalletLimitsRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.WalletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST surface for the ledger-backed Pocket Money module. */
@RestController
@RequestMapping("/api/v1/pocket-money")
public class PocketMoneyApiController {

    private final PocketMoneyApiService service;
    private final PocketMoneyProvisioningService provisioning;
    private final PocketMoneyPurchaseService purchases;

    public PocketMoneyApiController(
            PocketMoneyApiService service,
            PocketMoneyProvisioningService provisioning,
            PocketMoneyPurchaseService purchases) {
        this.service = service;
        this.provisioning = provisioning;
        this.purchases = purchases;
    }

    @PostMapping("/wallets")
    public WalletResponse openWallet(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody OpenWalletRequest request) {
        return service.openWallet(request, idempotencyKey);
    }

    @GetMapping("/wallets/student/{studentId}")
    public WalletResponse wallet(@PathVariable UUID studentId) {
        return service.walletForStudent(studentId);
    }

    @GetMapping("/wallets/{walletId}/ledger")
    public List<LedgerEntryResponse> ledger(@PathVariable UUID walletId) {
        return service.ledger(walletId);
    }

    @PutMapping("/wallets/{walletId}/limits")
    public WalletResponse updateLimits(
            @PathVariable UUID walletId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody WalletLimitsRequest request) {
        return service.updateLimits(walletId, request, idempotencyKey);
    }

    @PostMapping("/topups")
    public TopUpResponse topUp(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TopUpRequest request) {
        return service.topUp(request, idempotencyKey);
    }

    @PostMapping("/topups/request")
    public TopUpResponse requestTopUp(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TopUpRequest request) {
        return service.requestTopUp(request, idempotencyKey);
    }

    @PostMapping("/topups/{id}/approve")
    public TopUpResponse approve(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.decideTopUp(id, true, idempotencyKey);
    }

    @PostMapping("/topups/{id}/decline")
    public TopUpResponse decline(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.decideTopUp(id, false, idempotencyKey);
    }

    @GetMapping("/topups")
    public List<TopUpResponse> topUps(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID walletId) {
        return service.topUps(studentId, walletId);
    }

    @GetMapping("/cards")
    public List<CardResponse> cards() {
        return provisioning.cards();
    }

    @PostMapping("/cards")
    public CardResponse issueCard(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody IssueCardRequest request) {
        return provisioning.issueCard(request, idempotencyKey);
    }

    @GetMapping("/merchants")
    public List<MerchantResponse> merchants() {
        return provisioning.merchants();
    }

    @PostMapping("/merchants")
    public MerchantResponse registerMerchant(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody MerchantRequest request) {
        return provisioning.registerMerchant(request, idempotencyKey);
    }

    @GetMapping("/terminals")
    public List<TerminalResponse> terminals() {
        return provisioning.terminals();
    }

    @PostMapping("/terminals")
    public TerminalResponse registerTerminal(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TerminalRequest request) {
        return provisioning.registerTerminal(request, idempotencyKey);
    }

    @PostMapping("/purchases")
    public PurchaseResponse purchase(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PurchaseRequest request) {
        return purchases.purchase(request, idempotencyKey);
    }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases(@RequestParam(required = false) UUID studentId) {
        return purchases.purchases(studentId);
    }

    @GetMapping("/reconciliation/{walletId}")
    public ReconciliationResponse reconciliation(@PathVariable UUID walletId) {
        return service.reconcile(walletId);
    }
}
