package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "report_definitions")
@Getter
@Setter
@NoArgsConstructor
public class ReportDefinition extends TenantOwnedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "report_type", nullable = false, length = 60)
    private String reportType;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String format = "PDF";

    @Column(name = "last_generated", length = 30)
    private String lastGenerated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, DRAFT, ARCHIVED }
}
