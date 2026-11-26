package org.example.school_wallet.academic;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Live dashboard numbers, respecting parent/student row scoping. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final AttendanceRecordRepository attendance;
    private final FeeRecordRepository fees;
    private final FeePaymentRepository payments;
    private final EnrolmentRepository enrolments;
    private final ResourceAccessPolicy accessPolicy;

    public DashboardService(
            StudentRepository students,
            SchoolClassRepository classes,
            AttendanceRecordRepository attendance,
            FeeRecordRepository fees,
            FeePaymentRepository payments,
            EnrolmentRepository enrolments,
            ResourceAccessPolicy accessPolicy) {
        this.students = students;
        this.classes = classes;
        this.attendance = attendance;
        this.fees = fees;
        this.payments = payments;
        this.enrolments = enrolments;
        this.accessPolicy = accessPolicy;
    }

    public Map<String, Object> summary() {
        boolean scoped = isScoped();
        List<Student> visibleStudents = visibleStudents(scoped);
        Set<UUID> visibleStudentIds = visibleStudents.stream()
                .map(Student::getId)
                .collect(Collectors.toSet());

        long activeStudents = visibleStudents.stream()
                .filter(student -> student.getStatus() == Student.Status.ACTIVE)
                .count();
        long activeClasses = scoped
                ? classes.findAll().stream()
                        .filter(schoolClass -> schoolClass.getStatus() == SchoolClass.Status.ACTIVE)
                        .filter(schoolClass -> enrolments.findAll().stream()
                                .anyMatch(row -> row.getStatus() == Enrolment.Status.ACTIVE
                                        && schoolClass.getId().equals(row.getClassId())
                                        && visibleStudentIds.contains(row.getStudentId())))
                        .count()
                : classes.findAll().stream()
                        .filter(schoolClass -> schoolClass.getStatus() == SchoolClass.Status.ACTIVE)
                        .count();

        List<AttendanceRecord> attendanceRecords = forStudents(
                attendance.findAll(), AttendanceRecord::getStudentId, visibleStudentIds, scoped);
        long present = attendanceRecords.stream()
                .filter(row -> row.getStatus() == AttendanceRecord.Status.PRESENT)
                .count();
        double attendanceRate = attendanceRecords.isEmpty()
                ? 0
                : Math.round((double) present / attendanceRecords.size() * 1000) / 10.0;

        List<FeeRecord> feeRecords = forStudents(
                fees.findAll(), FeeRecord::getStudentId, visibleStudentIds, scoped);
        BigDecimal billed = feeRecords.stream()
                .map(FeeRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = feeRecords.stream()
                .map(FeeRecord::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Enrolment> activeEnrolments = forStudents(
                enrolments.findAll(), Enrolment::getStudentId, visibleStudentIds, scoped).stream()
                .filter(row -> row.getStatus() == Enrolment.Status.ACTIVE)
                .toList();

        List<FeePayment> visiblePayments = forStudents(
                payments.findAll(), FeePayment::getStudentId, visibleStudentIds, scoped);
        List<Map<String, Object>> recentPayments = visiblePayments.stream()
                .sorted(Comparator.comparing(FeePayment::getCreatedAt).reversed())
                .limit(5)
                .map(payment -> Map.<String, Object>of(
                        "reference",
                        payment.getProviderReference() == null || payment.getProviderReference().isBlank()
                                ? payment.getId().toString().substring(0, 8)
                                : payment.getProviderReference(),
                        "amount", payment.getAmount(),
                        "status", "Paid"))
                .toList();

        return Map.of(
                "students", visibleStudents.size(),
                "activeStudents", activeStudents,
                "classes", activeClasses,
                "attendanceRate", attendanceRate,
                "billed", billed,
                "collected", collected,
                "outstanding", billed.subtract(collected),
                "activeEnrolments", activeEnrolments.size(),
                "recentPayments", recentPayments);
    }

    public List<Map<String, Object>> recentStudents() {
        return visibleStudents(isScoped()).stream()
                .sorted(Comparator.comparing(Student::getCreatedAt).reversed())
                .limit(5)
                .map(student -> Map.<String, Object>of(
                        "name", student.getFirstName() + " " + student.getLastName(),
                        "class", student.getClassName() == null ? "—" : student.getClassName(),
                        "status", student.getStatus().name()))
                .toList();
    }

    private boolean isScoped() {
        return CurrentUser.get().filter(accessPolicy::isScoped).isPresent();
    }

    private List<Student> visibleStudents(boolean scoped) {
        if (!scoped) {
            return students.findAll();
        }
        UUID userId = CurrentUser.require().id();
        return students.findAll().stream()
                .filter(student -> userId.equals(student.getUserId())
                        || userId.equals(student.getGuardianUserId()))
                .toList();
    }

    private <T> List<T> forStudents(
            List<T> rows,
            Function<T, UUID> studentId,
            Set<UUID> visibleStudentIds,
            boolean scoped) {
        if (!scoped) {
            return rows;
        }
        return rows.stream()
                .filter(row -> visibleStudentIds.contains(studentId.apply(row)))
                .toList();
    }
}
