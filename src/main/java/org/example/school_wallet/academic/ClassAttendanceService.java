package org.example.school_wallet.academic;

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
        int weekCount = SemesterAttendance.weekCount(term);
        int week = clamp(requestedWeek == null ? 1 : requestedWeek, 1, weekCount);

        List<Student> roster = roster(schoolClass.getId(), term);
        Set<UUID> rosterIds = roster.stream().map(Student::getId).collect(Collectors.toSet());
        List<AttendanceRecord> classRecords = attendance.findByClassId(schoolClass.getId());

        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int number = 1; number <= weekCount; number++) {
            weeks.add(SemesterAttendance.weekSummary(term, number, rosterIds, classRecords, null));
        }
        Map<String, Object> selectedWeek = weeks.get(week - 1);

        long completedWeeks = SemesterAttendance.completedWeeks(weeks);
        int markedThisWeek = Integer.parseInt(String.valueOf(selectedWeek.get("marked")));
        int totalStudents = roster.size();
        double semesterProgress = totalStudents == 0
                ? 0
                : SemesterAttendance.round(completedWeeks * 100.0 / weekCount);
        double weekProgress = totalStudents == 0
                ? 0
                : SemesterAttendance.round(markedThisWeek * 100.0 / totalStudents);

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
        response.put("weeks", SemesterAttendance.publicWeeks(weeks));
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
        int safeWeek = clamp(week == null ? 1 : week, 1, SemesterAttendance.weekCount(term));
        String attendanceDate = SemesterAttendance.weekStartDate(term, safeWeek);

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
            option.put("numberOfWeeks", SemesterAttendance.weekCount(row));
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

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
