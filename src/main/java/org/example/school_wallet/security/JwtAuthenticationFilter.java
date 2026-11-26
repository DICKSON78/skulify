package org.example.school_wallet.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.user.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final AuthUserLookup userLookup;

    public JwtAuthenticationFilter(JwtService jwtService, AuthUserLookup userLookup) {
        this.jwtService = jwtService;
        this.userLookup = userLookup;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtService.TokenClaims claims = jwtService.parse(header.substring(BEARER.length()));
            if (claims.type() != JwtService.TokenType.ACCESS) {
                filterChain.doFilter(request, response);
                return;
            }

            // Load the user on every request so deactivation/suspension takes
            // effect immediately, even while an access token is still valid.
            userLookup.findById(claims.userId())
                    .filter(AuthUser::canAuthenticate)
                    .ifPresent(user -> authenticate(request, user));
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid token: continue unauthenticated; protected endpoints will 401.
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request, AuthUser user) {
        TenantContext.set(user.tenantId());
        Set<Role> roles = user.roles();
        Set<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.toPrincipal(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
