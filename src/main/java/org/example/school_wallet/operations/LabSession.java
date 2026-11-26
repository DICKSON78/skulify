package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "lab_sessions", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "reference"}))
@Getter
@Setter
@NoArgsConstructor
public class LabSession extends TenantOwnedEntity implements StudentLinked {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "book_id")
    private UUID bookId;

    @Column(length = 60)
    private String lab;

    @Column(length = 20)
    private String seat;

    @Column(name = "start_at", length = 20)
    private String startAt;

    @Column(name = "end_at", length = 20)
    private String endAt;

    @Column(length = 300)
    private String purpose;

    @Column(length = 20)
    private String status = "BOOKED";
}
