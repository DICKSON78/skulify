package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Durable outbox so notifications survive restarts and can be retried. */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox extends TenantOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Channel channel = Channel.IN_APP;

    @Column(nullable = false, length = 200)
    private String recipient;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "related_resource", length = 40)
    private String relatedResource;

    @Column(name = "related_id")
    private UUID relatedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public enum Channel { SMS, EMAIL, WHATSAPP, IN_APP }

    public enum Status { PENDING, SENT, FAILED }
}
