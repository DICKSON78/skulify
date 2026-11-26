package org.example.school_wallet.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.academic.Enrolment;
import org.example.school_wallet.academic.GradeRecord;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.SchoolClass;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.Subject;
import org.example.school_wallet.academic.Teacher;
import org.example.school_wallet.academic.TeacherAssignment;
import org.example.school_wallet.common.error.ApiException;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.tenant.SchoolTenants;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end coverage for timetable, marks, duty, exam and result automation. */
@SpringBootTest
@Transactional
class AcademicAutomationIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private AcademicAutomationService service;

    private final UUID tenantId = SchoolTenants.DEMO_SCHOOL_ID;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        login(Set.of(Role.TENANT_ADMIN), UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void timetableGenerationUsesPeriodsAndDoesNotDuplicateEntries() {
        SchoolClass schoolClass = clazz("F1A");
        Subject subject = subject("MATH");
        Teacher teacher = teacher("T1", UUID.randomUUID());
        assignment(teacher.getId(), schoolClass.getId(), subject.getId());
        period("P1", "08:00", "08:45", 1);
        period("P2", "08:50", "09:35", 2);

        Map<String, Object> body = Map.of(
                "classId", schoolClass.getId().toString(),
                "days", List.of("MONDAY", "TUESDAY"));

        Map<String, Object> first = service.generateTimetable(body);
        assertEquals(4, first.get("generated"));
        assertEquals(0, first.get("skipped"));

        Map<String, Object> second = service.generateTimetable(body);
        assertEquals(0, second.get("generated"));
        assertEquals(4, second.get("skipped"));

        Map<String, Object> replaced = Map.of(
                "classId", schoolClass.getId().toString(),
                "days", List.of("MONDAY", "TUESDAY"),
                "replace", "true");
        assertEquals(4, service.generateTimetable(replaced).get("generated"));

        List<?> rows = em.createQuery(
                        "select t from TimetableEntry t where t.classId = :classId",
                        org.example.school_wallet.academic.TimetableEntry.class)
                .setParameter("classId", schoolClass.getId())
                .getResultList();
        assertEquals(4, rows.size());
    }

    @Test
    void teacherMarksUploadComputesGradesAndRequiresSubjectAssignment() {
        SchoolClass schoolClass = clazz("F1B");
        Subject assigned = subject("PHY");
        Subject other = subject("CHEM");
        UUID teacherUserId = UUID.randomUUID();
        Teacher teacher = teacher("T2", teacherUserId);
        assignment(teacher.getId(), schoolClass.getId(), assigned.getId());
        Student first = student("S1");
        Student second = student("S2");
        enrol(first.getId(), schoolClass.getId());
        enrol(second.getId(), schoolClass.getId());
        login(Set.of(Role.TEACHER), teacherUserId);

        List<GradeRecord> grades = service.uploadMarks(Map.of(
                "classId", schoolClass.getId().toString(),
                "subjectId", assigned.getId().toString(),
                "examType", "MID_TERM",
                "term", "TERM_1",
                "maxScore", "100",
                "entries", List.of(
                        Map.of("studentId", first.getId().toString(), "score", "86"),
                        Map.of("studentId", second.getId().toString(), "score", "42"))));

        assertEquals(2, grades.size());
        assertEquals("A", grades.get(0).getGrade());
        assertEquals("E", grades.get(1).getGrade());
        assertEquals(teacherUserId, grades.get(0).getTeacherId());

        ApiException error = assertThrows(ApiException.class, () -> service.uploadMarks(Map.of(
                "classId", schoolClass.getId().toString(),
                "subjectId", other.getId().toString(),
                "entries", List.of(Map.of("studentId", first.getId().toString(), "score", "50")))));
        assertEquals("NOT_ASSIGNED_SUBJECT", error.getCode());
    }

    @Test
    void dutyRosterSkipsTeachersOnApprovedLeave() {
        Teacher available = teacher("T3", UUID.randomUUID());
        Teacher onLeave = teacher("T4", UUID.randomUUID());
        StaffLeave leave = new StaffLeave();
        leave.setTeacherId(onLeave.getId());
        leave.setLeaveType("SICK");
        leave.setStartDate(LocalDate.now().toString());
        leave.setEndDate(LocalDate.now().plusDays(10).toString());
        em.persist(leave);
        em.flush();
        service.decideLeave(leave.getId(), Map.of("decision", "APPROVE"));

        Map<String, Object> result = service.generateDutyRoster(Map.of(
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(6).toString(),
                "days", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                "dutyTypes", List.of("ASSEMBLY"),
                "perDay", "1"));
        assertTrue((int) result.get("generated") > 0);

        List<DutyAssignment> duties = em.createQuery(
                        "select d from DutyAssignment d where d.dutyType = 'ASSEMBLY'", DutyAssignment.class)
                .getResultList();
        assertTrue(duties.stream().allMatch(duty -> !onLeave.getId().equals(duty.getTeacherId())));

        DutyAssignment cancelled = service.cancelDuty(duties.get(0).getId());
        assertEquals("CANCELLED", cancelled.getStatus());
    }

    @Test
    void examScheduleAndResultsCanBeGeneratedAndPublished() {
        SchoolClass schoolClass = clazz("F2A");
        Subject subject = subject("BIO");
        Teacher teacher = teacher("T5", UUID.randomUUID());
        assignment(teacher.getId(), schoolClass.getId(), subject.getId());
        Student student = student("S3");
        enrol(student.getId(), schoolClass.getId());

        Exam exam = exam("EX-1", "FINAL", "TERM_1");
        List<ExamSession> sessions = service.generateExamSchedule(exam.getId(), Map.of(
                "startDate", LocalDate.now().plusDays(2).toString(),
                "classIds", List.of(schoolClass.getId().toString()),
                "subjectIds", List.of(subject.getId().toString()),
                "periodMinutes", "90"));
        assertEquals(1, sessions.size());
        assertEquals("SCHEDULED", em.find(Exam.class, exam.getId()).getStatus());

        service.uploadMarks(Map.of(
                "classId", schoolClass.getId().toString(),
                "subjectId", subject.getId().toString(),
                "examType", "FINAL",
                "term", "TERM_1",
                "entries", List.of(Map.of("studentId", student.getId().toString(), "score", "72"))));

        List<ReportCard> cards = service.generateReportCards(exam.getId(), schoolClass.getId());
        assertEquals(1, cards.size());
        assertEquals("DRAFT", cards.get(0).getStatus());
        assertNotNull(cards.get(0).getReference());

        int published = service.publishDueResults();
        assertEquals(1, published);
        assertEquals("PUBLISHED", em.find(ReportCard.class, cards.get(0).getId()).getStatus());

        long notifications = em.createQuery(
                        "select count(n) from NotificationOutbox n where n.relatedResource = 'report-cards'",
                        Long.class)
                .getSingleResult();
        assertTrue(notifications > 0);
    }

    @Test
    void dailyAutomationIsRepeatable() {
        Teacher teacher = teacher("T6", UUID.randomUUID());
        Exam exam = exam("EX-2", "TEST", "TERM_2");

        Map<String, Object> first = service.runDaily();
        assertTrue((int) first.get("attendancePrepared") > 0);
        assertEquals(0, first.get("resultsPublished"));

        StaffAttendance created = em.createQuery(
                        "select a from StaffAttendance a where a.staffId = :staffId",
                        StaffAttendance.class)
                .setParameter("staffId", teacher.getId())
                .getResultStream()
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", created.getStatus());

        Map<String, Object> second = service.runDaily();
        assertEquals(0, second.get("attendancePrepared"));
        assertNotNull(em.find(Exam.class, exam.getId()));
    }

    private void login(Set<Role> roles, UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId, tenantId, "tester", "Test User", roles, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }

    private SchoolClass clazz(String code) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setCode(code + suffix());
        schoolClass.setName("Class " + code);
        schoolClass.setRoom("Room " + code);
        schoolClass.setStatus(SchoolClass.Status.ACTIVE);
        em.persist(schoolClass);
        em.flush();
        return schoolClass;
    }

    private Subject subject(String code) {
        Subject subject = new Subject();
        subject.setCode(code + suffix());
        subject.setName(code + " subject");
        subject.setStatus(Subject.Status.ACTIVE);
        em.persist(subject);
        em.flush();
        return subject;
    }

    private Teacher teacher(String code, UUID userId) {
        Teacher teacher = new Teacher();
        teacher.setEmployeeCode(code + suffix());
        teacher.setFirstName("Test");
        teacher.setLastName(code);
        teacher.setUserId(userId);
        teacher.setPhone("+255700000012");
        teacher.setStatus(Teacher.Status.ACTIVE);
        em.persist(teacher);
        em.flush();
        return teacher;
    }

    private TeacherAssignment assignment(UUID teacherId, UUID classId, UUID subjectId) {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacherId(teacherId);
        assignment.setClassId(classId);
        assignment.setSubjectId(subjectId);
        assignment.setScope(TeacherAssignment.Scope.SUBJECT_TEACHER);
        assignment.setStatus(TeacherAssignment.Status.ACTIVE);
        em.persist(assignment);
        em.flush();
        return assignment;
    }

    private Student student(String code) {
        Student student = new Student();
        student.setStudentCode(code + suffix());
        student.setFirstName("Test");
        student.setLastName(code);
        student.setGuardianPhone("+255700000013");
        student.setStatus(Student.Status.ACTIVE);
        em.persist(student);
        em.flush();
        return student;
    }

    private Enrolment enrol(UUID studentId, UUID classId) {
        Enrolment enrolment = new Enrolment();
        enrolment.setStudentId(studentId);
        enrolment.setClassId(classId);
        enrolment.setAcademicYearId(UUID.randomUUID());
        enrolment.setStatus(Enrolment.Status.ACTIVE);
        em.persist(enrolment);
        em.flush();
        return enrolment;
    }

    private Period period(String code, String start, String end, int order) {
        Period period = new Period();
        period.setCode(code + suffix());
        period.setName(code);
        period.setStartTime(start);
        period.setEndTime(end);
        period.setSortOrder(order);
        em.persist(period);
        em.flush();
        return period;
    }

    private Exam exam(String code, String type, String term) {
        Exam exam = new Exam();
        exam.setCode(code + suffix());
        exam.setName(code + " exam");
        exam.setExamType(type);
        exam.setTerm(term);
        exam.setStartDate(LocalDate.now().minusDays(1).toString());
        exam.setEndDate(LocalDate.now().toString());
        exam.setStatus("PLANNED");
        em.persist(exam);
        em.flush();
        return exam;
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
