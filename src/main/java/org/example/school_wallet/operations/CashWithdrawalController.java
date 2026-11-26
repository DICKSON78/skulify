package org.example.school_wallet.operations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Teacher cash requests, guardian approval and agent payout confirmation. */
@RestController
@RequestMapping("/api/v1/cash-requests")
public class CashWithdrawalController {

    private final CashWithdrawalService service;
    private final ResourceAccessPolicy policy;

    public CashWithdrawalController(CashWithdrawalService service, ResourceAccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping
    public List<CashWithdrawalRequest> list() {
        policy.require("cash-requests", ResourceAccessPolicy.Action.READ);
        return service.list();
    }

    @PostMapping
    public CashWithdrawalRequest create(@RequestBody Map<String, Object> body) {
        policy.require("cash-requests", ResourceAccessPolicy.Action.CREATE);
        return service.create(body);
    }

    @PostMapping("/{id}/decide")
    public CashWithdrawalRequest decide(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("cash-requests", ResourceAccessPolicy.Action.UPDATE);
        return service.decide(
                id,
                String.valueOf(body.getOrDefault("decision", "")),
                String.valueOf(body.getOrDefault("note", "")));
    }

    @PostMapping("/{id}/payout")
    public CashWithdrawalRequest payout(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("cash-requests", ResourceAccessPolicy.Action.UPDATE);
        return service.payout(
                id,
                String.valueOf(body.getOrDefault("agentCode", "")),
                String.valueOf(body.getOrDefault("note", "")));
    }
}
