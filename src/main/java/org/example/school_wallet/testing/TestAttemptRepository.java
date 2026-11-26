package org.example.school_wallet.testing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {

    Optional<TestAttempt> findByTestIdAndStudentId(UUID testId, UUID studentId);

    List<TestAttempt> findByTestId(UUID testId);

    long countByTestId(UUID testId);
}
