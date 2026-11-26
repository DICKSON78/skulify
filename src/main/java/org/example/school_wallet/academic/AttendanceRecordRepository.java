package org.example.school_wallet.academic;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    java.util.Optional<AttendanceRecord> findByStudentIdAndAttendanceDate(UUID studentId, String attendanceDate);
}
