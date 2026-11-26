package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "invoice_no"}))
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "invoice_no", nullable = false, length = 40)
    private String invoiceNo;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "fee_plan_id")
    private UUID feePlanId;

    @Column(name = "term_id")
    private UUID termId;

    @Column(name = "issue_date", length = 20)
    private String issueDate;

    @Column(name = "due_date", length = 20)
    private String dueDate;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.DRAFT;

    @Column(length = 500)
    private String remarks;

    public enum Status { DRAFT, ISSUED, PARTIAL, PAID, OVERDUE, CANCELLED }
}
