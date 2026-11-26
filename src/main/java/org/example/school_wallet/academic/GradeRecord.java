package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "grade_records")
@Getter
@Setter
@NoArgsConstructor
public class GradeRecord extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "exam_type", nullable = false, length = 60)
    private String examType;

    @Column(length = 30)
    private String term;

    private Double score;

    @Column(name = "max_score")
    private Double maxScore = 100.0;

    @Column(length = 10)
    private String grade;

    @Column(length = 500)
    private String remarks;

    @Column(name = "teacher_id")
    private UUID teacherId;
}
