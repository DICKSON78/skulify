package org.example.school_wallet.academic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {

    List<Enrolment> findByStudentIdAndStatus(UUID studentId, Enrolment.Status status);
}
