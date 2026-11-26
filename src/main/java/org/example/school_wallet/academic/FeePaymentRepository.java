package org.example.school_wallet.academic;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {
}
