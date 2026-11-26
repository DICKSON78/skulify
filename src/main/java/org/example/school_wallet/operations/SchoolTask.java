package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Work queue for routine school operations that used to be tracked manually. */
@Entity
@Table(name = "school_tasks")
@Getter
@Setter
@NoArgsConstructor
public class SchoolTask extends TenantOwnedEntity {

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "due_date", length = 20)
    private String dueDate;

    @Column(nullable = false, length = 15)
    private String status = "OPEN";

    @Column(nullable = false, length = 15)
    private String priority = "NORMAL";

    @Column(name = "source_resource", length = 40)
    private String sourceResource;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "completed_at", length = 20)
    private String completedAt;
}
