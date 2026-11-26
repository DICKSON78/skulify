package org.example.school_wallet.testing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnlineTestRepository extends JpaRepository<OnlineTest, UUID> {

    Optional<OnlineTest> findByCode(String code);

    List<OnlineTest> findByStatus(String status);

    List<OnlineTest> findByCreatedBy(UUID createdBy);
}
