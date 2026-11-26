package org.example.school_wallet.academic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees")
public class FeePaymentController {

    private final FeeService service;
    private final ResourceAccessPolicy accessPolicy;

    public FeePaymentController(FeeService service, ResourceAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping("/{id}/payments")
    public FeePayment pay(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        accessPolicy.require("fees", Action.UPDATE);
        return service.recordPayment(id, body);
    }

    @GetMapping("/{id}/payments")
    public List<FeePayment> history(@PathVariable UUID id) {
        accessPolicy.require("fees", Action.READ);
        return service.history(id);
    }
}
