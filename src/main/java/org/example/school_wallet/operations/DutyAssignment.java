package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Teacher duty roster entry (assembly, discipline, lunch, dismissal, etc.). */
@Entity
@Table(name = "duty_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DutyAssignment extends TenantOwnedEntity {

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "duty_type", nullable = false, length = 40)
    private String dutyType;

    @Column(name = "duty_date", length = 20)
    private String dutyDate;

    @Column(name = "day_of_week", length = 15)
    private String dayOfWeek;

    @Column(name = "start_time", length = 10)
    private String startTime;

    @Column(name = "end_time", length = 10)
    private String endTime;

    @Column(length = 120)
    private String location;

    @Column(length = 30)
    private String term;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(length = 500)
    private String notes;
}
