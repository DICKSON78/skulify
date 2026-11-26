package org.example.school_wallet.testing;

import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.JwtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous endpoints used by students following a signed test link. */
@RestController
@RequestMapping("/api/v1/public/tests")
public class PublicTestController {

    private final JwtService jwtService;
    private final OnlineTestService service;

    public PublicTestController(JwtService jwtService, OnlineTestService service) {
        this.jwtService = jwtService;
        this.service = service;
    }

    @GetMapping("/{token}/preview")
    public Map<String, Object> preview(
            @PathVariable String token, @RequestParam String accessCode) {
        Link link = parse(token);
        return TenantContext.callAs(link.tenantId(),
                (Supplier<Map<String, Object>>) () -> service.preview(link.testId(), accessCode));
    }

    @PostMapping("/{token}/start")
    public Map<String, Object> start(@PathVariable String token, @RequestBody Map<String, Object> body) {
        Link link = parse(token);
        return TenantContext.callAs(link.tenantId(),
                (Supplier<Map<String, Object>>) () -> service.start(
                        link.testId(),
                        String.valueOf(body.get("accessCode")),
                        String.valueOf(body.get("registrationNo"))));
    }

    @PostMapping("/{token}/submit")
    public Map<String, Object> submit(@PathVariable String token, @RequestBody Map<String, Object> body) {
        Link link = parse(token);
        return TenantContext.callAs(link.tenantId(),
                (Supplier<Map<String, Object>>) () -> service.submit(
                        link.testId(),
                        String.valueOf(body.get("accessCode")),
                        String.valueOf(body.get("registrationNo")),
                        body.get("answers")));
    }

    private Link parse(String token) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);
            if (claims.type() != JwtService.TokenType.TEST_LINK || claims.userId() == null) {
                throw Errors.badRequest("INVALID_TEST_LINK", "The test link is not valid");
            }
            return new Link(claims.tenantId(), claims.userId());
        } catch (JwtException | IllegalArgumentException ex) {
            throw Errors.badRequest("INVALID_TEST_LINK", "The test link is not valid or has expired");
        }
    }

    private record Link(UUID tenantId, UUID testId) {
    }
}
