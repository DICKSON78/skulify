package org.example.school_wallet.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentLetterRepository extends JpaRepository<ParentLetter, UUID> {

    List<ParentLetter> findByStudentId(UUID studentId);

    Optional<ParentLetter> findByReportCardId(UUID reportCardId);
}
