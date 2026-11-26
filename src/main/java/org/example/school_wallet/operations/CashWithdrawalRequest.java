package org.example.school_wallet.operations;

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
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/**
 * A teacher asks for cash on behalf of a student. The guardian approves it,
 * the child's card is debited and the teacher receives a payout reference to
 * withdraw the cash from an agent.
 */
@Entity
@Table(name = "cash_withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
public class CashWithdrawalRequest extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 240)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "requested_at", length = 30)
    private String requestedAt;

    @Column(name = "decided_at", length = 30)
    private String decidedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "payout_reference", length = 60)
    private String payoutReference;

    @Column(name = "agent_code", length = 60)
    private String agentCode;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    @Column(length = 300)
    private String note;

    public enum Status { PENDING, APPROVED, DECLINED, PAID }
}
