package org.example.school_wallet.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.example.school_wallet.config.AppProperties;
import org.example.school_wallet.security.AuthUser;
import org.example.school_wallet.user.RefreshToken;
import org.example.school_wallet.user.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AppProperties properties;

    public TokenService(RefreshTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public TokenPair issueRefreshToken(AuthUser user, String remoteIp) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(raw);

        RefreshToken token = new RefreshToken();
        token.setUserId(user.id());
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(properties.getJwt().refreshTokenTtl()));
        token.setCreatedIp(remoteIp);
        repository.save(token);

        return new TokenPair(raw, hash);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }

    public record TokenPair(String rawRefreshToken, String refreshTokenHash) {
    }
}
