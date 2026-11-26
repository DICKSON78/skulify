package org.example.school_wallet.testing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** A timed online assessment prepared by a teacher for one class. */
@Entity
@Table(name = "online_tests")
@Getter
@Setter
@NoArgsConstructor
public class OnlineTest extends TenantOwnedEntity {

    /** Public identifier embedded in the signed student link. */
    @Column(nullable = false, length = 40)
    private String code;

    /** Second factor the teacher distributes separately from the link. */
    @Column(name = "access_code", nullable = false, length = 60)
    private String accessCode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "start_at", length = 20)
    private String startAt;

    @Column(name = "end_at", length = 20)
    private String endAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Column(name = "questions_json", nullable = false, length = 20000)
    private String questionsJson = "[]";

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
}
