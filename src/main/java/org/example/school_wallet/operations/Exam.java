package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** A planned examination: the single source for exam sessions and result cards. */
@Entity
@Table(name = "exams", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Exam extends TenantOwnedEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "exam_type", nullable = false, length = 60)
    private String examType;

    @Column(length = 30)
    private String term;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(length = 500)
    private String remarks;
}
