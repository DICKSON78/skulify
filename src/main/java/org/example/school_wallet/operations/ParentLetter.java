package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Automatically generated end-of-term letter to a student's guardian. */
@Entity
@Table(name = "parent_letters")
@Getter
@Setter
@NoArgsConstructor
public class ParentLetter extends TenantOwnedEntity implements StudentLinked {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "report_card_id")
    private UUID reportCardId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(length = 30)
    private String term;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 20000)
    private String content;

    /** Next-term requirements derived from grades, attendance and fees. */
    @Column(nullable = false, length = 20000)
    private String requirements;

    @Column(nullable = false, length = 20)
    private String status = "GENERATED";

    @Column(name = "generated_at", length = 20)
    private String generatedAt;
}
