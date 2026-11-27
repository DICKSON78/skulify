package org.example.school_wallet.academic;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, UUID> {
}
