package org.example.school_wallet.academic;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores messages in a durable outbox. Provider adapters (SMS/email/WhatsApp)
 * can process PENDING rows later; nothing is lost on restart.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outbox;

    public NotificationService(NotificationOutboxRepository outbox) {
        this.outbox = outbox;
    }

    public NotificationOutbox send(
            NotificationOutbox.Channel channel,
            String recipient,
            String subject,
            String body,
            String relatedResource,
            UUID relatedId) {
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        NotificationOutbox message = new NotificationOutbox();
        message.setChannel(channel);
        message.setRecipient(recipient.trim());
        message.setSubject(subject);
        message.setBody(body);
        message.setRelatedResource(relatedResource);
        message.setRelatedId(relatedId);
        message.setStatus(NotificationOutbox.Status.PENDING);
        message.setAttempts(0);
        if (TenantContext.get() == null) {
            TenantContext.set(
                    CurrentUser.get().map(user -> user.tenantId()).orElse(TenantContext.SYSTEM_TENANT_ID));
        }
        return outbox.save(message);
    }

    public List<NotificationOutbox> pending() {
        return outbox.findAll().stream()
                .filter(row -> row.getStatus() == NotificationOutbox.Status.PENDING)
                .toList();
    }

    /**
     * Drains the outbox. The default adapter only records delivery; replace the
     * body of deliver() with SMS/email/WhatsApp gateway calls when credentials
     * are available. PENDING rows are never deleted, so retries remain auditable.
     */
    public int dispatchPending() {
        int dispatched = 0;
        for (NotificationOutbox message : pending()) {
            try {
                deliver(message);
                message.setStatus(NotificationOutbox.Status.SENT);
                message.setAttempts(message.getAttempts() + 1);
                message.setLastError(null);
                dispatched++;
            } catch (Exception ex) {
                message.setStatus(NotificationOutbox.Status.FAILED);
                message.setAttempts(message.getAttempts() + 1);
                message.setLastError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
            outbox.save(message);
        }
        return dispatched;
    }

    private void deliver(NotificationOutbox message) {
        log.info(
                "Notification [{}] to {}: {} — {}",
                message.getChannel(),
                message.getRecipient(),
                message.getSubject(),
                message.getBody());
    }
}
