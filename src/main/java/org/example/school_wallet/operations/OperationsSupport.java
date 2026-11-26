package org.example.school_wallet.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.audit.AuditService;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.time.TimeValues;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Component;

/** Shared parsing, scoping, audit and notification helpers for operations modules. */
@Component
public class OperationsSupport {

    private final StudentRepository students;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ResourceAccessPolicy accessPolicy;

    public OperationsSupport(
            StudentRepository students,
            AuditService audit,
            NotificationService notifications,
            ResourceAccessPolicy accessPolicy) {
        this.students = students;
        this.audit = audit;
        this.notifications = notifications;
        this.accessPolicy = accessPolicy;
    }

    public String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public BigDecimal amount(Object value, BigDecimal fallback) {
        if (value == null || text(value).isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(text(value));
        } catch (NumberFormatException ex) {
            throw Errors.badRequest("INVALID_AMOUNT", "Invalid amount: " + value);
        }
    }

    public int integer(Object value, int fallback) {
        if (value == null || text(value).isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ex) {
            throw Errors.badRequest("INVALID_QUANTITY", "Invalid quantity: " + value);
        }
    }

    public UUID uuid(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw Errors.badRequest("INVALID_ID", "Invalid identifier: " + raw);
        }
    }

    public Instant instant(Object value, Instant fallback) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            try {
                return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (RuntimeException ex) {
                throw Errors.badRequest("INVALID_DATE", "Invalid date: " + raw);
            }
        }
    }

    public String now() {
        return TimeValues.now();
    }

    public String timestamp(Instant instant) {
        return TimeValues.text(instant);
    }

    public String actor() {
        return CurrentUser.get()
                .map(AuthenticatedUser::fullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("system");
    }

    public Student student(UUID studentId) {
        if (studentId == null) {
            throw Errors.badRequest("STUDENT_REQUIRED", "studentId is required");
        }
        return students.findById(studentId)
                .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
    }

    /** Applies the same parent/student row scope used by generic CRUD. */
    public void requireVisibleStudent(UUID studentId) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty() || !accessPolicy.isScoped(current.get())) {
            return;
        }
        UUID userId = current.get().id();
        Student student = student(studentId);
        if (!userId.equals(student.getUserId()) && !userId.equals(student.getGuardianUserId())) {
            throw Errors.forbidden("NOT_YOUR_RECORD", "This record does not belong to your account");
        }
    }

    public void audit(String action, String entityType, UUID entityId, Map<String, Object> details) {
        audit.record(action, entityType, entityId == null ? "SYSTEM" : entityId.toString(), details);
    }

    public void notifyGuardian(
            Student student,
            String subject,
            String body,
            String resource,
            UUID relatedId) {
        if (student == null) {
            return;
        }
        notifications.send(
                NotificationOutbox.Channel.SMS,
                student.getGuardianPhone(),
                subject,
                body,
                resource,
                relatedId);
        notifications.send(
                NotificationOutbox.Channel.IN_APP,
                student.getUserId() == null ? student.getStudentCode() : student.getUserId().toString(),
                subject,
                body,
                resource,
                relatedId);
    }
}
