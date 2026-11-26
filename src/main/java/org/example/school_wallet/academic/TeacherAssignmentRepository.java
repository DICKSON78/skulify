package org.example.school_wallet.academic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {

    List<TeacherAssignment> findByTeacherIdAndStatus(UUID teacherId, TeacherAssignment.Status status);

    List<TeacherAssignment> findByTeacherIdAndClassIdAndStatus(
            UUID teacherId, UUID classId, TeacherAssignment.Status status);
}
