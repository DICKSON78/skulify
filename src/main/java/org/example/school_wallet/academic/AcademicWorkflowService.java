package org.example.school_wallet.academic;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Higher-level school operations built on top of the flat CRUD records. */
@Service
@Transactional
public class AcademicWorkflowService {

    private final AttendanceRecordRepository attendance;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final EnrolmentRepository enrolments;
    private final TeacherAssignmentRepository assignments;
    private final TimetableEntryRepository timetable;
    private final NotificationService notifications;
    private final ResourceAccessPolicy accessPolicy;

    public AcademicWorkflowService(
            AttendanceRecordRepository attendance,
            StudentRepository students,
            TeacherRepository teachers,
            EnrolmentRepository enrolments,
            TeacherAssignmentRepository assignments,
            TimetableEntryRepository timetable,
            NotificationService notifications,
            ResourceAccessPolicy accessPolicy) {
        this.attendance = attendance;
        this.students = students;
        this.teachers = teachers;
        this.enrolments = enrolments;
        this.assignments = assignments;
        this.timetable = timetable;
        this.notifications = notifications;
        this.accessPolicy = accessPolicy;
    }

    public List<AttendanceRecord> markAttendance(
            UUID classId, String attendanceDate, List<Map<String, Object>> entries) {
        // Bulk marking is a class-teacher action; subject teachers use single records.
        requireAssignedClass(classId, true);
        if (attendanceDate == null || attendanceDate.isBlank()) {
            throw Errors.badRequest("INVALID_DATE", "Attendance date is required");
        }
        if (entries == null || entries.isEmpty()) {
            throw Errors.badRequest("EMPTY_ATTENDANCE", "No attendance entries were submitted");
        }

        List<AttendanceRecord> saved = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            UUID studentId = uuid(entry.get("studentId"));
            if (studentId == null) {
                throw Errors.badRequest("INVALID_STUDENT", "Every entry needs a valid studentId");
            }
            requireEnrolled(studentId, classId);
            AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(studentId, attendanceDate)
                    .orElseGet(AttendanceRecord::new);
            record.setStudentId(studentId);
            record.setAttendanceDate(attendanceDate);
            record.setClassId(classId);
            record.setStatus(
                    AttendanceRecord.Status.valueOf(
                            text(entry.get("status")).isEmpty() ? "PRESENT" : text(entry.get("status")).toUpperCase()));
            record.setCheckIn(text(entry.get("checkIn")));
            record.setRemarks(text(entry.get("remarks")));
            record.setRecordedBy(
                    CurrentUser.get().map(user -> user.fullName()).orElse("system"));
            record.setTeacherId(CurrentUser.get().map(user -> user.id()).orElse(null));
            saved.add(attendance.save(record));

            if (record.getStatus() == AttendanceRecord.Status.ABSENT) {
                students.findById(studentId).ifPresent(student ->
                        notifications.send(
                                NotificationOutbox.Channel.SMS,
                                student.getGuardianPhone(),
                                "Absence alert",
                                student.getFirstName() + " was marked absent on " + attendanceDate,
                                "attendance",
                                record.getId()));
            }
        }
        return saved;
    }

    /** Single attendance records inherit the student's active class when omitted. */
    public void validateAttendanceWrite(AttendanceRecord record) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty() || accessPolicy.isPrivileged(current.get())) {
            return;
        }
        UUID classId = record.getClassId();
        if (classId == null && record.getStudentId() != null) {
            classId = enrolments.findByStudentIdAndStatus(record.getStudentId(), Enrolment.Status.ACTIVE)
                    .stream()
                    .findFirst()
                    .map(Enrolment::getClassId)
                    .orElse(null);
            record.setClassId(classId);
        }
        if (classId == null) {
            throw Errors.forbidden("NOT_ASSIGNED", "No active class found for this student");
        }
        if (record.getStudentId() != null) {
            requireEnrolled(record.getStudentId(), classId);
        }
        requireAssignedClass(classId, false);
    }

    /** Subject teachers may only record grades for subjects they are assigned to. */
    public void requireGradeWrite(UUID subjectId) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty() || accessPolicy.isPrivileged(current.get())) {
            return;
        }
        if (subjectId == null) {
            throw Errors.badRequest("INVALID_SUBJECT", "A subject is required for a grade record");
        }
        boolean assigned = activeAssignments().stream()
                .anyMatch(row -> subjectId.equals(row.getSubjectId()));
        if (!assigned) {
            throw Errors.forbidden("NOT_ASSIGNED_SUBJECT", "You are not assigned to this subject");
        }
    }

    /** Login ids and linked teacher record ids both count when checking assignments. */
    private Set<UUID> currentTeacherIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty()) {
            return ids;
        }
        UUID userId = current.get().id();
        ids.add(userId);
        teachers.findAll().stream()
                .filter(teacher -> userId.equals(teacher.getUserId()))
                .map(Teacher::getId)
                .forEach(ids::add);
        return ids;
    }

    private List<TeacherAssignment> activeAssignments() {
        Set<UUID> teacherIds = currentTeacherIds();
        if (teacherIds.isEmpty()) {
            return List.of();
        }
        return assignments.findAll().stream()
                .filter(row -> row.getStatus() == TeacherAssignment.Status.ACTIVE)
                .filter(row -> teacherIds.contains(row.getTeacherId()))
                .toList();
    }

    private void requireEnrolled(UUID studentId, UUID classId) {
        boolean enrolled = enrolments.findByStudentIdAndStatus(studentId, Enrolment.Status.ACTIVE).stream()
                .anyMatch(row -> classId.equals(row.getClassId()));
        if (!enrolled) {
            throw Errors.badRequest("NOT_ENROLLED", "Student is not actively enrolled in this class");
        }
    }

    public Enrolment enrol(Map<String, Object> body) {
        UUID studentId = uuid(body.get("studentId"));
        UUID classId = uuid(body.get("classId"));
        UUID academicYearId = uuid(body.get("academicYearId"));
        if (studentId == null || classId == null || academicYearId == null) {
            throw Errors.badRequest("INVALID_ENROLMENT", "studentId, classId and academicYearId are required");
        }
        boolean already = enrolments.findByStudentIdAndStatus(studentId, Enrolment.Status.ACTIVE).stream()
                .anyMatch(row -> academicYearId.equals(row.getAcademicYearId()));
        if (already) {
            throw Errors.conflict("ALREADY_ENROLLED", "Student already has an active enrolment for this year");
        }

        Enrolment enrolment = new Enrolment();
        enrolment.setStudentId(studentId);
        enrolment.setClassId(classId);
        enrolment.setAcademicYearId(academicYearId);
        enrolment.setEnrolmentDate(text(body.get("enrolmentDate")));
        enrolment.setStatus(Enrolment.Status.ACTIVE);
        return enrolments.save(enrolment);
    }

    public TeacherAssignment assignTeacher(Map<String, Object> body) {
        UUID teacherId = uuid(body.get("teacherId"));
        UUID classId = uuid(body.get("classId"));
        UUID subjectId = uuid(body.get("subjectId"));
        if (teacherId == null || (classId == null && subjectId == null)) {
            throw Errors.badRequest(
                    "INVALID_ASSIGNMENT", "teacherId and at least one of classId/subjectId are required");
        }
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacherId(teacherId);
        assignment.setClassId(classId);
        assignment.setSubjectId(subjectId);
        assignment.setAcademicYearId(uuid(body.get("academicYearId")));
        assignment.setScope(
                TeacherAssignment.Scope.valueOf(
                        text(body.get("scope")).isEmpty()
                                ? (subjectId == null ? "CLASS_TEACHER" : "SUBJECT_TEACHER")
                                : text(body.get("scope")).toUpperCase()));
        assignment.setStatus(TeacherAssignment.Status.ACTIVE);
        return assignments.save(assignment);
    }

    public void validateTimetable(TimetableEntry entry, UUID excludeId) {
        LocalTime start = parseTime(entry.getStartTime());
        LocalTime end = parseTime(entry.getEndTime());
        if (!end.isAfter(start)) {
            throw Errors.badRequest("INVALID_TIME_RANGE", "End time must be after start time");
        }
        timetable.findAll().stream()
                .filter(row -> excludeId == null || !excludeId.equals(row.getId()))
                .filter(row -> row.getDayOfWeek() == entry.getDayOfWeek())
                .filter(row -> overlaps(start, end, parseTime(row.getStartTime()), parseTime(row.getEndTime())))
                .filter(row -> same(row.getClassId(), entry.getClassId())
                        || same(row.getTeacherId(), entry.getTeacherId())
                        || same(row.getRoom(), entry.getRoom()))
                .findFirst()
                .ifPresent(row -> {
                    throw Errors.conflict(
                            "TIMETABLE_CONFLICT", "Class, teacher or room is already booked at that time");
                });
    }

    private void requireAssignedClass(UUID classId, boolean requireClassTeacher) {
        if (CurrentUser.get().isEmpty()) {
            throw Errors.unauthorized("UNAUTHORIZED", "Authentication is required");
        }
        AuthenticatedUser user = CurrentUser.require();
        if (accessPolicy.isPrivileged(user)) {
            return;
        }
        Set<UUID> teacherIds = currentTeacherIds();
        boolean assigned = assignments.findAll().stream()
                .filter(row -> row.getStatus() == TeacherAssignment.Status.ACTIVE)
                .filter(row -> teacherIds.contains(row.getTeacherId()))
                .filter(row -> classId.equals(row.getClassId()))
                .anyMatch(row -> !requireClassTeacher
                        || row.getScope() == TeacherAssignment.Scope.CLASS_TEACHER
                        || row.getScope() == TeacherAssignment.Scope.HEAD_OF_DEPARTMENT);
        if (!assigned) {
            throw Errors.forbidden(
                    "NOT_ASSIGNED", "You are not assigned to this class");
        }
    }

    private boolean overlaps(LocalTime start, LocalTime end, LocalTime otherStart, LocalTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    private boolean same(Object first, Object second) {
        return first != null && first.equals(second);
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw Errors.badRequest("INVALID_TIME", "Times must use HH:mm format");
        }
    }

    private UUID uuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
