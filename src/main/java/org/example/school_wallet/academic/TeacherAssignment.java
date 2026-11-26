package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Links a teacher user to a class and/or subject for an academic year. */
@Entity
@Table(name = "teacher_assignments")
@Getter
@Setter
@NoArgsConstructor
public class TeacherAssignment extends TenantOwnedEntity {

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Scope scope = Scope.SUBJECT_TEACHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum Scope { CLASS_TEACHER, SUBJECT_TEACHER, HEAD_OF_DEPARTMENT }

    public enum Status { ACTIVE, INACTIVE }
}
