package org.example.school_wallet.security;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public Map<String, Object> me() {
        AuthenticatedUser user = CurrentUser.require();
        return Map.of(
                "id", user.id(),
                "tenantId", user.tenantId(),
                "username", user.username(),
                "fullName", user.fullName(),
                "roles", user.roles(),
                "mustChangePassword", user.mustChangePassword());
    }
}
