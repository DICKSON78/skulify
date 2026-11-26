package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Immutable-style payment ledger entry for a fee record. */
@Entity
@Table(name = "fee_payments")
@Getter
@Setter
@NoArgsConstructor
public class FeePayment extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "fee_id", nullable = false)
    private UUID feeId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(name = "provider_reference", length = 80)
    private String providerReference;

    @Column(name = "paid_at", nullable = false, length = 30)
    private String paidAt;

    @Column(name = "recorded_by", length = 120)
    private String recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CONFIRMED;

    public enum Status { PENDING, CONFIRMED, REVERSED }
}
