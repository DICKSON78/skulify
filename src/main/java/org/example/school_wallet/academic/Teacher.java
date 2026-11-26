package org.example.school_wallet.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "teachers", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "employee_code"}))
@Getter
@Setter
@NoArgsConstructor
public class Teacher extends TenantOwnedEntity {

    @Column(name = "employee_code", nullable = false, length = 40)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String specialization;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "hire_date", length = 20)
    private String hireDate;

    @Column(length = 255)
    private String address;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_type", length = 30)
    private EmployeeType employeeType = EmployeeType.TEACHER;

    @Column(length = 80)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT, VOLUNTEER }

    public enum EmployeeType {
        TEACHER,
        SECURITY_GUARD,
        CLEANER,
        ADMINISTRATIVE
    }

    public enum Status { ACTIVE, ON_LEAVE, INACTIVE }
}
