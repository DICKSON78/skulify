package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Staff leave requests; approved leave automatically excludes teachers from duty rosters. */
@Entity
@Table(name = "staff_leaves")
@Getter
@Setter
@NoArgsConstructor
public class StaffLeave extends TenantOwnedEntity {

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "leave_type", nullable = false, length = 40)
    private String leaveType;

    @Column(name = "start_date", nullable = false, length = 20)
    private String startDate;

    @Column(name = "end_date", nullable = false, length = 20)
    private String endDate;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at", length = 20)
    private String approvedAt;
}
