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
@Table(name = "students", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "student_code"}))
@Getter
@Setter
@NoArgsConstructor
public class Student extends TenantOwnedEntity {

    @Column(name = "student_code", nullable = false, length = 40)
    private String studentCode;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "date_of_birth", length = 20)
    private String dateOfBirth;

    @Column(name = "admission_date", length = 20)
    private String admissionDate;

    @Column(name = "class_name", length = 60)
    private String className;

    @Column(name = "guardian_name", length = 120)
    private String guardianName;

    @Column(name = "guardian_phone", length = 30)
    private String guardianPhone;

    @Column(name = "guardian_region", length = 80)
    private String guardianRegion;

    @Column(name = "guardian_district", length = 80)
    private String guardianDistrict;

    @Column(name = "guardian_street", length = 120)
    private String guardianStreet;

    @Column(length = 255)
    private String address;

    @Column(length = 80)
    private String region;

    @Column(length = 80)
    private String district;

    @Column(length = 120)
    private String street;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guardian_user_id")
    private UUID guardianUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_position", length = 30)
    private StudentPosition studentPosition = StudentPosition.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum Gender { MALE, FEMALE }

    public enum StudentPosition { NONE, PREFECT, LIBRARY_PREFECT, LAB_PREFECT }

    public enum Status { ACTIVE, INACTIVE, GRADUATED, SUSPENDED }
}
