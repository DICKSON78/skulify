package org.example.school_wallet.academic;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {
    Optional<Teacher> findByUserId(UUID userId);
}
