package org.example.school_wallet.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.audit.AuditService;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.config.AppProperties;
import org.example.school_wallet.security.AuthUser;
import org.example.school_wallet.security.AuthUserLookup;
import org.example.school_wallet.security.JwtService;
import org.example.school_wallet.security.TotpService;
import org.example.school_wallet.user.RefreshToken;
import org.example.school_wallet.user.RefreshTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final AuthUserLookup userLookup;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties properties;
    private final AuditService auditService;
    private final TokenService tokenService;

    public AuthApiController(
            AuthUserLookup userLookup,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TotpService totpService,
            RefreshTokenRepository refreshTokenRepository,
            AppProperties properties,
            AuditService auditService,
            TokenService tokenService) {
        this.userLookup = userLookup;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.auditService = auditService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Transactional
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthUser user = userLookup
                .findByLogin(request.identifier(), org.example.school_wallet.common.util.PhoneNumbers.normalize(request.identifier()))
                .orElseThrow(() -> {
                    auditService.record("AUTH_LOGIN_FAILED", null, null, Map.of("identifier", request.identifier()));
                    return Errors.unauthorized("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
                });

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_LOGIN_FAILED", "APP_USER", user.id().toString(), Map.of());
            throw Errors.unauthorized("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
        }

        if (!user.canAuthenticate()) {
            auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_ACCOUNT_DISABLED", "APP_USER", user.id().toString(), Map.of());
            throw Errors.forbidden("AUTH_ACCOUNT_DISABLED", "This account is not allowed to sign in");
        }

        // Require MFA for privileged roles when enabled.
        if (user.requiresMfa() && user.mfaEnabled()) {
            auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_MFA_REQUIRED", "APP_USER", user.id().toString(), Map.of());
            String challengeToken = jwtService.issuePurposeToken(
                    user.id(), user.tenantId(), user.username(), user.roles(), JwtService.TokenType.MFA_CHALLENGE);
            return new LoginResponse(null, null, challengeToken, true, null, null);
        }

        return issueSession(user, http);
    }

    @PostMapping("/mfa/verify")
    @Transactional
    public LoginResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http) {
        JwtService.TokenClaims claims = parsePurposeToken(request.token(), JwtService.TokenType.MFA_CHALLENGE);
        AuthUser user = userLookup.findById(claims.userId())
                .orElseThrow(() -> Errors.unauthorized("AUTH_INVALID_TOKEN", "MFA token is invalid"));

        if (!user.mfaEnabled() || user.mfaSecret() == null) {
            throw Errors.badRequest("MFA_NOT_ENABLED", "MFA is not enabled for this account");
        }

        String plainSecret = mfaSecret(user);
        if (!totpService.verify(plainSecret, request.code())) {
            auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_MFA_FAILED", "APP_USER", user.id().toString(), Map.of());
            throw Errors.unauthorized("AUTH_MFA_INVALID", "Invalid MFA code");
        }

        auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_MFA_VERIFIED", "APP_USER", user.id().toString(), Map.of());
        return issueSession(user, http);
    }

    @PostMapping("/refresh")
    @Transactional
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        String presentedHash = tokenService.hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> Errors.unauthorized("AUTH_REFRESH_INVALID", "Refresh token is invalid"));

        if (stored.getRevokedAt() != null) {
            // Token reuse = theft. Kill the whole family.
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
            auditService.recordAs(
                    tenantOf(stored.getUserId()),
                    stored.getUserId(),
                    "unknown",
                    "AUTH_REFRESH_REUSE_DETECTED",
                    "APP_USER",
                    stored.getUserId().toString(),
                    Map.of());
            throw Errors.unauthorized("AUTH_REFRESH_REVOKED", "Session has been revoked");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw Errors.unauthorized("AUTH_REFRESH_EXPIRED", "Refresh token has expired");
        }

        AuthUser user = userLookup.findById(stored.getUserId())
                .orElseThrow(() -> Errors.unauthorized("AUTH_REFRESH_INVALID", "Refresh token is invalid"));
        if (!user.canAuthenticate()) {
            throw Errors.forbidden("AUTH_ACCOUNT_DISABLED", "This account is not allowed to sign in");
        }

        Instant now = Instant.now();
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        String accessToken = jwtService.issueAccessToken(user.toPrincipal());
        TokenService.TokenPair pair = tokenService.issueRefreshToken(user, http.getRemoteAddr());
        stored.setReplacedByHash(pair.refreshTokenHash());
        refreshTokenRepository.save(stored);

        auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_REFRESH", "APP_USER", user.id().toString(), Map.of());
        return new TokenPairResponse(accessToken, pair.rawRefreshToken(), jwtService.accessTokenSeconds());
    }

    @PostMapping("/logout")
    @Transactional
    public void logout(@Valid @RequestBody RefreshRequest request) {
        String hash = tokenService.hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private LoginResponse issueSession(AuthUser user, HttpServletRequest http) {
        TenantContext.set(user.tenantId());
        String accessToken = jwtService.issueAccessToken(user.toPrincipal());
        TokenService.TokenPair pair = tokenService.issueRefreshToken(user, http.getRemoteAddr());
        auditService.recordAs(user.tenantId(), user.id(), user.username(), "AUTH_LOGIN", "APP_USER", user.id().toString(), Map.of());
        return new LoginResponse(accessToken, pair.rawRefreshToken(), null, false, jwtService.accessTokenSeconds(), user.mustChangePassword());
    }

    private JwtService.TokenClaims parsePurposeToken(String token, JwtService.TokenType expected) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);
            if (claims.type() != expected) {
                throw Errors.unauthorized("AUTH_INVALID_TOKEN", "Token type is not valid for this request");
            }
            return claims;
        } catch (io.jsonwebtoken.JwtException ex) {
            throw Errors.unauthorized("AUTH_INVALID_TOKEN", "Token is invalid or expired");
        }
    }

    private String mfaSecret(AuthUser user) {
        String secret = user.mfaSecret();
        if (secret == null) {
            throw Errors.badRequest("MFA_SECRET_INVALID", "MFA secret could not be read");
        }
        if (secret.startsWith("v1:")) {
            try {
                org.example.school_wallet.security.SecretCipher cipher =
                        org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext()
                                .getBean(org.example.school_wallet.security.SecretCipher.class);
                return cipher.decrypt(secret);
            } catch (Exception ex) {
                throw Errors.badRequest("MFA_SECRET_INVALID", "MFA secret could not be read");
            }
        }
        return secret;
    }

    private java.util.UUID tenantOf(java.util.UUID userId) {
        return userLookup.findById(userId).map(AuthUser::tenantId).orElse(TenantContext.SYSTEM_TENANT_ID);
    }

    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
    }

    public record MfaVerifyRequest(@NotBlank String token, @NotBlank String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            String mfaChallengeToken,
            boolean mfaRequired,
            Long expiresIn,
            Boolean mustChangePassword) {
    }

    public record TokenPairResponse(String accessToken, String refreshToken, Long expiresIn) {
    }
}
