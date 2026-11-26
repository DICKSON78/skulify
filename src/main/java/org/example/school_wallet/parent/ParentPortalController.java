package org.example.school_wallet.parent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Guardian-facing read API. Row scope is enforced again in the service. */
@RestController
@RequestMapping("/api/v1/parent-portal")
public class ParentPortalController {

    private final ParentPortalService service;
    private final ResourceAccessPolicy policy;

    public ParentPortalController(ParentPortalService service, ResourceAccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/children")
    public List<Map<String, Object>> children() {
        policy.require("students", Action.READ);
        return service.children();
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) UUID studentId) {
        policy.require("students", Action.READ);
        return service.overview(studentId);
    }
}
