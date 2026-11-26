package org.example.school_wallet.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes append-only audit records. Each record is committed in its own
 * transaction so failed login attempts and rolled-back business actions are
 * still captured.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, Map<String, Object> details) {
        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        // During a super-admin "act as tenant" call the thread context is the
        // target tenant, so that is where the audit entry belongs.
        UUID tenantId = TenantContext.get() != null
                ? TenantContext.get()
                : (actor != null ? actor.tenantId() : null);
        UUID actorId = actor != null ? actor.id() : null;
        String actorName = actor != null ? actor.username() : "anonymous";
        write(tenantId != null ? tenantId : TenantContext.SYSTEM_TENANT_ID,
                actorId, actorName, action, entityType, entityId, details, remoteIp());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(
            UUID tenantId,
            UUID actorId,
            String actorName,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details) {
        write(tenantId, actorId, actorName, action, entityType, entityId, details, remoteIp());
    }

    private void write(
            UUID tenantId,
            UUID actorId,
            String actorName,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details,
            String ipAddress) {

        AuditLog entry = new AuditLog();
        entry.setTenantId(tenantId);
        entry.setActorId(actorId);
        entry.setActorUsername(actorName);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(ipAddress);
        entry.setDetails(toJson(details));
        repository.save(entry);
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(details));
        } catch (Exception ex) {
            log.warn("Unable to serialise audit details", ex);
            return null;
        }
    }

    private String remoteIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
