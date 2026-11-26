package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** One scheduled paper for a class and subject inside an exam. */
@Entity
@Table(name = "exam_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ExamSession extends TenantOwnedEntity {

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "exam_date", nullable = false, length = 20)
    private String examDate;

    @Column(name = "start_time", nullable = false, length = 10)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 10)
    private String endTime;

    @Column(length = 40)
    private String room;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(length = 500)
    private String remarks;
}
