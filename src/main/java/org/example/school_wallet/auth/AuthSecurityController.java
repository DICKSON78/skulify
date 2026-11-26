package org.example.school_wallet.auth;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.audit.AuditService;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.config.AppProperties;
import org.example.school_wallet.security.AuthUser;
import org.example.school_wallet.security.AuthUserLookup;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.JwtService;
import org.example.school_wallet.security.TotpService;
import org.example.school_wallet.user.AppUser;
import org.example.school_wallet.user.AppUserRepository;
import org.example.school_wallet.user.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthSecurityController {

    private final AppUserRepository appUserRepository;
    private final AuthUserLookup userLookup;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final org.example.school_wallet.security.SecretCipher secretCipher;
    private final AuditService auditService;

    public AuthSecurityController(
            AppUserRepository appUserRepository,
            AuthUserLookup userLookup,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TotpService totpService,
            org.example.school_wallet.security.SecretCipher secretCipher,
            AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.userLookup = userLookup;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
        this.auditService = auditService;
    }

    @PostMapping("/password/change")
    @PreAuthorize("isAuthenticated()")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = CurrentUser.require().id();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw Errors.unauthorized("AUTH_INVALID_CREDENTIALS", "Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw Errors.badRequest("PASSWORD_MISMATCH", "Passwords do not match");
        }
        validatePassword(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        appUserRepository.save(user);

        auditService.record("AUTH_PASSWORD_CHANGED", "APP_USER", user.getId().toString(), Map.of());
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    public MfaSetupResponse setupMfa() {
        UUID userId = CurrentUser.require().id();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "User not found"));

        if (user.isMfaEnabled()) {
            throw Errors.badRequest("MFA_ALREADY_ENABLED", "MFA is already enabled for this account");
        }

        String secret = totpService.generateSecret();
        user.setMfaSecret(secretCipher.encrypt(secret));
        appUserRepository.save(user);

        String issuer = "Skulify";
        String account = user.getUsername();
        String uri = totpService.otpauthUri(issuer, account, secret);
        auditService.record("AUTH_MFA_SETUP_STARTED", "APP_USER", user.getId().toString(), Map.of());
        return new MfaSetupResponse(secret, uri);
    }

    @PostMapping("/mfa/enable")
    @PreAuthorize("isAuthenticated()")
    public void enableMfa(@Valid @RequestBody MfaCodeRequest request) {
        UUID userId = CurrentUser.require().id();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "User not found"));

        if (user.isMfaEnabled()) {
            throw Errors.badRequest("MFA_ALREADY_ENABLED", "MFA is already enabled for this account");
        }
        String secret = decryptSecret(user.getMfaSecret());
        if (!totpService.verify(secret, request.code())) {
            throw Errors.unauthorized("AUTH_MFA_INVALID", "Invalid MFA code");
        }

        user.setMfaEnabled(true);
        appUserRepository.save(user);
        auditService.record("AUTH_MFA_ENABLED", "APP_USER", user.getId().toString(), Map.of());
    }

    private String decryptSecret(String secret) {
        if (secret == null) {
            throw Errors.badRequest("MFA_NOT_SETUP", "MFA has not been set up");
        }
        if (secret.startsWith("v1:")) {
            return secretCipher.decrypt(secret);
        }
        return secret;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw Errors.badRequest("PASSWORD_TOO_WEAK", "Password must be at least 12 characters");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        if (!(hasUpper && hasLower && hasDigit && hasSymbol)) {
            throw Errors.badRequest(
                    "PASSWORD_TOO_WEAK",
                    "Password must include uppercase, lowercase, number and symbol");
        }
    }

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank String currentPassword,
            @jakarta.validation.constraints.NotBlank String newPassword,
            @jakarta.validation.constraints.NotBlank String confirmPassword) {
    }

    public record MfaCodeRequest(@jakarta.validation.constraints.NotBlank String code) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {
    }
}
