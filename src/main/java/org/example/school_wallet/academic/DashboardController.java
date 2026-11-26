package org.example.school_wallet.academic;

import java.util.Map;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final ResourceAccessPolicy accessPolicy;

    public DashboardController(DashboardService service, ResourceAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    public Map<String, Object> summary() {
        accessPolicy.require("students", Action.READ);
        return service.summary();
    }

    @GetMapping("/recent-students")
    public java.util.List<Map<String, Object>> recentStudents() {
        accessPolicy.require("students", Action.READ);
        return service.recentStudents();
    }
}
