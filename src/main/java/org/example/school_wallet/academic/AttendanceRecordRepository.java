package org.example.school_wallet.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByStudentIdAndAttendanceDate(UUID studentId, String attendanceDate);

    List<AttendanceRecord> findByClassId(UUID classId);
}
