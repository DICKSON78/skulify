package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "staff_attendance")
@Getter
@Setter
@NoArgsConstructor
public class StaffAttendance extends TenantOwnedEntity {

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "attendance_date", nullable = false, length = 20)
    private String attendanceDate;

    @Column(nullable = false, length = 15)
    private String status = "PRESENT";

    @Column(name = "check_in", length = 20)
    private String checkIn;

    @Column(name = "check_out", length = 20)
    private String checkOut;

    @Column(name = "recorded_by", length = 120)
    private String recordedBy;

    @Column(length = 500)
    private String remarks;
}
