package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "book_reservations")
@Getter
@Setter
@NoArgsConstructor
public class BookReservation extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "reserved_at", length = 20)
    private String reservedAt;

    @Column(length = 20)
    private String status = "ACTIVE";
}
