package org.example.school_wallet.testing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** One student's session for an online test, including the submitted answers. */
@Entity
@Table(name = "test_attempts")
@Getter
@Setter
@NoArgsConstructor
public class TestAttempt extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "registration_no", nullable = false, length = 40)
    private String registrationNo;

    @Column(name = "answers_json", length = 20000)
    private String answersJson = "{}";

    @Column(nullable = false)
    private double score;

    @Column(name = "max_score", nullable = false)
    private double maxScore;

    @Column(nullable = false, length = 20)
    private String status = "IN_PROGRESS";

    @Column(name = "started_at", length = 20)
    private String startedAt;

    @Column(name = "submitted_at", length = 20)
    private String submittedAt;
}
