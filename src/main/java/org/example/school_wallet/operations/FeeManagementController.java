package org.example.school_wallet.operations;

import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.FeePayment;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fee-management")
public class FeeManagementController {

    private final FeeManagementService service;
    private final ResourceAccessPolicy policy;

    public FeeManagementController(FeeManagementService service, ResourceAccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping("/invoices")
    public Invoice issue(@RequestBody Map<String, Object> body) {
        policy.require("invoices", ResourceAccessPolicy.Action.CREATE);
        return service.issueInvoice(body);
    }

    @PostMapping("/invoices/generate")
    public java.util.List<Invoice> generate(@RequestBody Map<String, Object> body) {
        policy.require("invoices", ResourceAccessPolicy.Action.CREATE);
        return service.generateForClass(
                UUID.fromString(String.valueOf(body.get("feePlanId"))),
                String.valueOf(body.getOrDefault("className", "")));
    }

    @PostMapping("/fees/{id}/payments")
    public FeePayment pay(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("fees", ResourceAccessPolicy.Action.UPDATE);
        return service.recordPayment(id, body);
    }

    @PostMapping("/payments/{id}/reverse")
    public FeePayment reverse(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("payments", ResourceAccessPolicy.Action.UPDATE);
        return service.reversePayment(id, String.valueOf(body.getOrDefault("reason", "")));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        policy.require("fees", ResourceAccessPolicy.Action.READ);
        return service.summary();
    }
}
