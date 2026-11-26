package org.example.school_wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.example.school_wallet.config.AppProperties;
import org.example.school_wallet.user.Role;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public enum TokenType {
        ACCESS,
        MFA_CHALLENGE,
        MFA_ENROLL,
        TEST_LINK
    }

    public record TokenClaims(
            UUID userId,
            UUID tenantId,
            String username,
            Set<Role> roles,
            TokenType type) {
    }

    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenMinutes;

    public JwtService(AppProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes. Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getJwt().getIssuer();
        this.accessTokenMinutes = properties.getJwt().getAccessTokenMinutes();
    }

    public String issueAccessToken(AuthenticatedUser user) {
        return issue(user.id(), user.tenantId(), user.username(), user.roles(), TokenType.ACCESS,
                Instant.now().plusSeconds(accessTokenMinutes * 60));
    }

    public String issuePurposeToken(
            UUID userId, UUID tenantId, String username, Set<Role> roles, TokenType type) {
        return issue(userId, tenantId, username, roles, type, Instant.now().plusSeconds(10 * 60));
    }

    /**
     * Signed link for an anonymous online-test session. The subject carries the
     * test id and the tenant claim lets the public controller restore the school
     * scope before touching tenant-owned rows.
     */
    public String issueTestLink(UUID tenantId, UUID testId, String accessCode) {
        return issue(testId, tenantId, accessCode, Set.of(), TokenType.TEST_LINK,
                Instant.now().plusSeconds(7L * 24 * 60 * 60));
    }

    private String issue(
            UUID userId,
            UUID tenantId,
            String username,
            Set<Role> roles,
            TokenType type,
            Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles.stream().map(Enum::name).sorted().toList())
                .claim(CLAIM_TYPE, type.name())
                .signWith(key)
                .compact();
    }

    /**
     * @throws JwtException when the token is invalid, expired or has a bad signature
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                claims.get(CLAIM_USERNAME, String.class),
                readRoles(claims),
                TokenType.valueOf(claims.get(CLAIM_TYPE, String.class)));
    }

    @SuppressWarnings("unchecked")
    private Set<Role> readRoles(Claims claims) {
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public long accessTokenSeconds() {
        return accessTokenMinutes * 60;
    }
}
