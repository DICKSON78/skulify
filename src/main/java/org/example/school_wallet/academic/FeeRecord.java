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

@Entity
@Table(name = "fee_records")
@Getter
@Setter
@NoArgsConstructor
public class FeeRecord extends TenantOwnedEntity implements StudentLinked {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "fee_type", nullable = false, length = 80)
    private String feeType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "due_date", length = 20)
    private String dueDate;

    @Column(name = "paid_date", length = 20)
    private String paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(length = 500)
    private String remarks;

    public enum Status { PENDING, PARTIAL, PAID, OVERDUE, CANCELLED }
}
