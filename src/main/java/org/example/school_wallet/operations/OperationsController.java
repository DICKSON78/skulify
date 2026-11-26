package org.example.school_wallet.operations;

import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final SchoolOperationsService service;
    private final NotificationService notifications;
    private final ResourceAccessPolicy policy;

    public OperationsController(
            SchoolOperationsService service,
            NotificationService notifications,
            ResourceAccessPolicy policy) {
        this.service = service;
        this.notifications = notifications;
        this.policy = policy;
    }

    @PostMapping("/departments/{id}/head")
    public Department assignHead(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("departments", ResourceAccessPolicy.Action.UPDATE);
        return service.assignHead(id, body);
    }

    @PostMapping("/stock/movements")
    public StockMovement movement(@RequestBody Map<String, Object> body) {
        policy.require("stock-movements", ResourceAccessPolicy.Action.CREATE);
        return service.recordMovement(body);
    }

    @PostMapping("/purchase-requests/{id}/approve")
    public PurchaseRequest approve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("purchase-requests", ResourceAccessPolicy.Action.UPDATE);
        return service.approve(id, body);
    }

    @PostMapping("/purchase-requests/{id}/receive")
    public PurchaseRequest receive(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("purchase-requests", ResourceAccessPolicy.Action.UPDATE);
        return service.receive(id, body);
    }

    @PostMapping("/tasks/{id}/complete")
    public SchoolTask complete(@PathVariable UUID id) {
        policy.require("school-tasks", ResourceAccessPolicy.Action.UPDATE);
        return service.completeTask(id);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        policy.require("departments", ResourceAccessPolicy.Action.READ);
        return service.summary();
    }

    @GetMapping("/notifications")
    public java.util.List<NotificationOutbox> notifications() {
        policy.require("notifications", ResourceAccessPolicy.Action.READ);
        return notifications.pending();
    }
}
