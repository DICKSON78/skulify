package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceItem extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_amount", precision = 14, scale = 2)
    private BigDecimal unitAmount;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
}
