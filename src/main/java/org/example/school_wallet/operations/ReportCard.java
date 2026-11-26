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

/** Generated term result summary. Guardians only ever see their own child's card. */
@Entity
@Table(name = "report_cards")
@Getter
@Setter
@NoArgsConstructor
public class ReportCard extends TenantOwnedEntity implements StudentLinked {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(length = 30)
    private String term;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "total_score", precision = 10, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "max_score", precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Column(precision = 10, scale = 2)
    private BigDecimal average;

    @Column(length = 10)
    private String grade;

    private Integer position;

    @Column(name = "class_size")
    private Integer classSize;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "published_at", length = 20)
    private String publishedAt;
}
