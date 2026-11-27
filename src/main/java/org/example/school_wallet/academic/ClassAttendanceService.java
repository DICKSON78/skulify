package org.example.school_wallet.academic;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class-teacher attendance register.
 *
 * <p>Progress is not driven by radio clicks. A semester week is complete only
 * when every actively enrolled student in the class has a status for that week,
 * and the overall percentage is completed weeks divided by the term's week count.
 */
@Service
@Transactional(readOnly = true)
public class ClassAttendanceService {

    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final EnrolmentRepository enrolments;
    private final TermRepository terms;
    private final AttendanceRecordRepository attendance;
    private final TeacherAssignmentRepository assignments;
    private final AcademicWorkflowService workflow;
    private final ResourceAccessPolicy accessPolicy;

    public ClassAttendanceService(
            SchoolClassRepository classes,
            StudentRepository students,
            EnrolmentRepository enrolments,
            TermRepository terms,
            AttendanceRecordRepository attendance,
            TeacherAssignmentRepository assignments,
            AcademicWorkflowService workflow,
            ResourceAccessPolicy accessPolicy) {
        this.classes = classes;
        this.students = students;
        this.enrolments = enrolments;
        this.terms = terms;
        this.attendance = attendance;
        this.assignments = assignments;
        this.workflow = workflow;
        this.accessPolicy = accessPolicy;
    }

    public Map<String, Object> overview(UUID requestedClassId, UUID requestedTermId, Integer requestedWeek) {
        accessPolicy.require("attendance", Action.READ);

        List<SchoolClass> accessible = accessibleClasses();
        SchoolClass schoolClass = resolveClass(accessible, requestedClassId);
        Term term = resolveTerm(requestedTermId);
        int weekCount = weekCount(term);
        int week = clamp(requestedWeek == null ? 1 : requestedWeek, 1, weekCount);

        List<Student> roster = roster(schoolClass.getId(), term);
        Set<UUID> rosterIds = roster.stream().map(Student::getId).collect(Collectors.toSet());
        List<AttendanceRecord> classRecords = attendance.findByClassId(schoolClass.getId());

        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int number = 1; number <= weekCount; number++) {
            weeks.add(weekSummary(term, number, rosterIds, classRecords));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> selectedWeek =
                (Map<String, Object>) weeks.get(week - 1);

        long completedWeeks = weeks.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("complete")))
                .count();
        int markedThisWeek = Integer.parseInt(String.valueOf(selectedWeek.get("marked")));
        int totalStudents = roster.size();
        double semesterProgress = totalStudents == 0
                ? 0
                : round(completedWeeks * 100.0 / weekCount);
        double weekProgress = totalStudents == 0
                ? 0
                : round(markedThisWeek * 100.0 / totalStudents);

        @SuppressWarnings("unchecked")
        Map<String, String> statuses =
                (Map<String, String>) selectedWeek.get("statuses");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("classes", classOptions(accessible));
        response.put("classId", schoolClass.getId());
        response.put("className", schoolClass.getName());
        response.put("terms", termOptions());
        response.put("termId", term.getId());
        response.put("termName", term.getName());
        response.put("numberOfWeeks", weekCount);
        response.put("week", week);
        response.put("weekStart", selectedWeek.get("startDate"));
        response.put("weekEnd", selectedWeek.get("endDate"));
        response.put("students", studentRows(roster));
        response.put("statuses", statuses);
        response.put("weeks", weeks.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.remove("statuses");
            return copy;
        }).toList());
        response.put("totalStudents", totalStudents);
        response.put("completedWeeks", completedWeeks);
        response.put("semesterProgress", semesterProgress);
        response.put("markedThisWeek", markedThisWeek);
        response.put("weekProgress", weekProgress);
        return response;
    }

    @Transactional
    public Map<String, Object> save(
            UUID classId, UUID termId, Integer week, List<Map<String, Object>> entries) {
        accessPolicy.require("attendance", Action.UPDATE);
        if (classId == null || termId == null) {
            throw Errors.badRequest("MISSING_CONTEXT", "Class and term are required");
        }
        Term term = terms.findById(termId)
                .orElseThrow(() -> Errors.notFound("TERM_NOT_FOUND", "Term not found"));
        int safeWeek = clamp(week == null ? 1 : week, 1, weekCount(term));
        String attendanceDate = weekStartDate(term, safeWeek);

        workflow.markAttendance(classId, attendanceDate, entries);
        return overview(classId, termId, safeWeek);
    }

    private List<SchoolClass> accessibleClasses() {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty()) {
            throw Errors.unauthorized("UNAUTHORIZED", "Authentication is required");
        }
        List<SchoolClass> all = classes.findAll();
        if (accessPolicy.isPrivileged(current.get())) {
            return all;
        }

        Set<UUID> teacherIds = workflow.currentTeacherIds();
        Set<UUID> classIds = assignments.findAll().stream()
                .filter(row -> row.getStatus() == TeacherAssignment.Status.ACTIVE)
                .filter(row -> row.getClassId() != null && teacherIds.contains(row.getTeacherId()))
                .filter(row -> row.getScope() == TeacherAssignment.Scope.CLASS_TEACHER
                        || row.getScope() == TeacherAssignment.Scope.HEAD_OF_DEPARTMENT)
                .map(TeacherAssignment::getClassId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        all.stream()
                .filter(row -> row.getClassTeacherId() != null && teacherIds.contains(row.getClassTeacherId()))
                .map(SchoolClass::getId)
                .forEach(classIds::add);

        return all.stream().filter(row -> classIds.contains(row.getId())).toList();
    }

    private SchoolClass resolveClass(List<SchoolClass> accessible, UUID requestedClassId) {
        if (accessible.isEmpty()) {
            throw Errors.badRequest(
                    "NO_ASSIGNED_CLASS", "You are not assigned to a class as class teacher");
        }
        if (requestedClassId == null) {
            return accessible.get(0);
        }
        return accessible.stream()
                .filter(row -> requestedClassId.equals(row.getId()))
                .findFirst()
                .orElseThrow(() -> Errors.forbidden(
                        "NOT_ASSIGNED", "You are not assigned to this class"));
    }

    private Term resolveTerm(UUID requestedTermId) {
        if (requestedTermId != null) {
            return terms.findById(requestedTermId)
                    .orElseThrow(() -> Errors.notFound("TERM_NOT_FOUND", "Term not found"));
        }
        return terms.findAll().stream()
                .filter(row -> row.getStatus() == Term.Status.ACTIVE)
                .min(java.util.Comparator.comparingInt(Term::getTermOrder))
                .orElseGet(() -> terms.findAll().stream()
                        .min(java.util.Comparator.comparingInt(Term::getTermOrder))
                        .orElseThrow(() -> Errors.badRequest(
                                "NO_TERM_CONFIGURED", "Add a term before marking attendance")));
    }

    private List<Student> roster(UUID classId, Term term) {
        LinkedHashSet<UUID> studentIds = enrolments.findByClassIdAndStatus(
                        classId, Enrolment.Status.ACTIVE)
                .stream()
                .filter(row -> term.getAcademicYearId() == null
                        || term.getAcademicYearId().equals(row.getAcademicYearId()))
                .map(Enrolment::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (studentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Student> byId = students.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, row -> row));
        return studentIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<String, Object> weekSummary(
            Term term, int number, Set<UUID> rosterIds, List<AttendanceRecord> classRecords) {
        String start = weekStartDate(term, number);
        String end = weekEndDate(term, number);
        Map<UUID, AttendanceRecord> byStudent = classRecords.stream()
                .filter(row -> row.getAttendanceDate() != null)
                .filter(row -> row.getAttendanceDate().compareTo(start) >= 0)
                .filter(row -> row.getAttendanceDate().compareTo(end) <= 0)
                .filter(row -> rosterIds.contains(row.getStudentId()))
                .collect(Collectors.toMap(
                        AttendanceRecord::getStudentId,
                        row -> row,
                        (first, second) -> first,
                        LinkedHashMap::new));

        long present = byStudent.values().stream()
                .filter(row -> row.getStatus() == AttendanceRecord.Status.PRESENT).count();
        long absent = byStudent.values().stream()
                .filter(row -> row.getStatus() == AttendanceRecord.Status.ABSENT).count();
        long excused = byStudent.values().stream()
                .filter(row -> row.getStatus() == AttendanceRecord.Status.EXCUSED).count();
        long late = byStudent.values().stream()
                .filter(row -> row.getStatus() == AttendanceRecord.Status.LATE).count();
        int marked = byStudent.size();
        int total = rosterIds.size();
        Map<String, String> statuses = new LinkedHashMap<>();
        byStudent.forEach((studentId, row) -> statuses.put(studentId.toString(), row.getStatus().name()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("number", number);
        summary.put("startDate", start);
        summary.put("endDate", end);
        summary.put("marked", marked);
        summary.put("total", total);
        summary.put("present", present);
        summary.put("absent", absent);
        summary.put("excused", excused);
        summary.put("late", late);
        summary.put("percent", total == 0 ? 0 : round(marked * 100.0 / total));
        summary.put("complete", total > 0 && marked == total);
        summary.put("statuses", statuses);
        return summary;
    }

    private List<Map<String, Object>> classOptions(List<SchoolClass> classes) {
        return classes.stream().map(row -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", row.getId());
            option.put("name", row.getName());
            option.put("code", row.getCode());
            return option;
        }).toList();
    }

    private List<Map<String, Object>> termOptions() {
        return terms.findAll().stream().map(row -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", row.getId());
            option.put("name", row.getName());
            option.put("termOrder", row.getTermOrder());
            option.put("startDate", row.getStartDate());
            option.put("endDate", row.getEndDate());
            option.put("numberOfWeeks", weekCount(row));
            option.put("status", row.getStatus().name());
            return option;
        }).toList();
    }

    private List<Map<String, Object>> studentRows(List<Student> roster) {
        return roster.stream().map(row -> {
            Map<String, Object> student = new LinkedHashMap<>();
            student.put("id", row.getId());
            student.put("studentCode", row.getStudentCode());
            student.put("firstName", row.getFirstName());
            student.put("lastName", row.getLastName());
            student.put("name", text(row.getFirstName()) + " " + text(row.getLastName()));
            student.put("status", row.getStatus() == null ? "" : row.getStatus().name());
            return student;
        }).toList();
    }

    private int weekCount(Term term) {
        return Math.max(1, term.getNumberOfWeeks());
    }

    private String weekStartDate(Term term, int week) {
        return parseDate(term.getStartDate()).plusWeeks(week - 1L).toString();
    }

    private String weekEndDate(Term term, int week) {
        LocalDate start = parseDate(term.getStartDate()).plusWeeks(week - 1L);
        LocalDate end = start.plusDays(6);
        if (term.getEndDate() != null && !term.getEndDate().isBlank()) {
            try {
                LocalDate termEnd = LocalDate.parse(term.getEndDate());
                if (termEnd.isBefore(end)) {
                    end = termEnd;
                }
            } catch (DateTimeParseException ignored) {
                // A malformed end date must not break the register; use the full week.
            }
        }
        return end.toString();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw Errors.badRequest("INVALID_TERM_DATE", "Term needs a valid start date");
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
