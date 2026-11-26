package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** School calendar entry used for automated reminders across all departments. */
@Entity
@Table(name = "school_events")
@Getter
@Setter
@NoArgsConstructor
public class SchoolEvent extends TenantOwnedEntity {

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_date", nullable = false, length = 20)
    private String eventDate;

    @Column(name = "start_time", length = 10)
    private String startTime;

    @Column(name = "end_time", length = 10)
    private String endTime;

    @Column(length = 120)
    private String location;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";
}
