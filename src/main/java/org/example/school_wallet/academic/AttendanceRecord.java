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

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attendance_date", nullable = false, length = 20)
    private String attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.PRESENT;

    @Column(name = "check_in", length = 10)
    private String checkIn;

    @Column(length = 500)
    private String remarks;

    @Column(name = "recorded_by", length = 120)
    private String recordedBy;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "class_id")
    private UUID classId;

    public enum Status { PRESENT, ABSENT, LATE, EXCUSED }
}
