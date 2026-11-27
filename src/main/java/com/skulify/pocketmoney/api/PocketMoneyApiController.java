package com.skulify.pocketmoney.api;

import com.skulify.pocketmoney.api.PocketMoneyApiDtos.LedgerEntryResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.OpenWalletRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.ReconciliationResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpRequest;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.TopUpResponse;
import com.skulify.pocketmoney.api.PocketMoneyApiDtos.WalletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PocketMoneyApiController(PocketMoneyApiService service) {
        this.service = service;
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

    @GetMapping("/reconciliation/{walletId}")
    public ReconciliationResponse reconciliation(@PathVariable UUID walletId) {
        return service.reconcile(walletId);
    }
}
