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
@Table(name = "book_loans")
@Getter
@Setter
@NoArgsConstructor
public class BookLoan extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "copy_id", nullable = false)
    private UUID copyId;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "borrowed_at", length = 20)
    private String borrowedAt;

    @Column(name = "due_at", length = 20)
    private String dueAt;

    @Column(name = "returned_at", length = 20)
    private String returnedAt;

    @Column(length = 20)
    private String status = "BORROWED";

    @Column(name = "fine_amount", precision = 14, scale = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO;
}
