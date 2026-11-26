package org.example.school_wallet.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.user.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me() {
        AuthenticatedUser user = CurrentUser.require();
        return new MeResponse(user.id(), user.tenantId(), user.username(), user.fullName(), user.roles());
    }

    public record MeResponse(
            java.util.UUID id,
            java.util.UUID tenantId,
            String username,
            String fullName,
            Set<Role> roles) {
    }
}
