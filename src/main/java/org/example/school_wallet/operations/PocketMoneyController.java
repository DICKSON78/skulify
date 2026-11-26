package org.example.school_wallet.operations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PocketMoneyController {

    private final PocketMoneyService service;
    private final ResourceAccessPolicy policy;

    public PocketMoneyController(PocketMoneyService service, ResourceAccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping("/pocket/cards")
    public PocketCard issue(@RequestBody Map<String, Object> body) {
        policy.require("pocket-cards", ResourceAccessPolicy.Action.CREATE);
        return service.issueCard(body);
    }

    @GetMapping("/pocket/cards/{id}/balance")
    public Map<String, Object> balance(@PathVariable UUID id) {
        policy.require("pocket-cards", ResourceAccessPolicy.Action.READ);
        return service.balance(id);
    }

    @PostMapping("/pocket/cards/{id}/topups")
    public PocketTransaction topUp(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("pocket-topups", ResourceAccessPolicy.Action.CREATE);
        return service.topUp(id, body);
    }

    @PostMapping("/pocket/cards/{id}/topups/request")
    public PocketTransaction requestTopUp(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("pocket-topups", ResourceAccessPolicy.Action.CREATE);
        return service.requestTopUp(id, body);
    }

    @PostMapping("/pocket/topups/{id}/approve")
    public PocketTransaction approveTopUp(@PathVariable UUID id) {
        policy.require("pocket-topups", ResourceAccessPolicy.Action.UPDATE);
        return service.decideTopUp(id, true);
    }

    @PostMapping("/pocket/topups/{id}/decline")
    public PocketTransaction declineTopUp(@PathVariable UUID id) {
        policy.require("pocket-topups", ResourceAccessPolicy.Action.UPDATE);
        return service.decideTopUp(id, false);
    }

    @PutMapping("/pocket/cards/{id}/limits")
    public Map<String, Object> limits(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("spending-limits", ResourceAccessPolicy.Action.UPDATE);
        return service.setLimits(id, body);
    }

    @PostMapping("/pocket/cards/{id}/status")
    public PocketCard status(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("pocket-cards", ResourceAccessPolicy.Action.UPDATE);
        return service.setStatus(id, PocketCard.Status.valueOf(
                String.valueOf(body.getOrDefault("status", "ACTIVE")).toUpperCase()));
    }

    @GetMapping("/pocket/cards/{id}/transactions")
    public List<PocketTransaction> history(@PathVariable UUID id) {
        policy.require("pocket-transactions", ResourceAccessPolicy.Action.READ);
        return service.history(id);
    }

    @PostMapping("/store/pos/charge")
    public PocketTransaction charge(@RequestBody Map<String, Object> body) {
        policy.require("store-pos", ResourceAccessPolicy.Action.CREATE);
        return service.charge(body);
    }

    @PostMapping("/store/transactions/{id}/refund")
    public PocketTransaction refund(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("store-pos", ResourceAccessPolicy.Action.UPDATE);
        return service.refund(id, String.valueOf(body.getOrDefault("reason", "")));
    }
}
