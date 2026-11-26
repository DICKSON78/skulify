package org.example.school_wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.example.school_wallet.common.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private final AuthUserLookup userLookup;

    public TenantFilter(AuthUserLookup userLookup) {
        this.userLookup = userLookup;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("X-Tenant-Id");
            if (header != null && !header.isBlank() && CurrentUser.get().isPresent()) {
                AuthenticatedUser user = CurrentUser.require();
                if (user.isSuperAdmin()) {
                    try {
                        TenantContext.set(UUID.fromString(header.trim()));
                    } catch (IllegalArgumentException ignored) {
                        // Fall back to the super admin's own tenant.
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
