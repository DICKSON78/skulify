package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "purchase_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "reference"}))
@Getter
@Setter
@NoArgsConstructor
public class PurchaseRequest extends TenantOwnedEntity {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 300)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at", length = 20)
    private String approvedAt;

    public enum Status { PENDING, APPROVED, REJECTED, RECEIVED, CANCELLED }
}
