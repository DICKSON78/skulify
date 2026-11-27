package org.example.school_wallet.academic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ClassAttendanceServiceTest {

    @Mock private SchoolClassRepository classes;
    @Mock private StudentRepository students;
    @Mock private EnrolmentRepository enrolments;
    @Mock private TermRepository terms;
    @Mock private AttendanceRecordRepository attendance;
    @Mock private TeacherAssignmentRepository assignments;
    @Mock private AcademicWorkflowService workflow;

    private ClassAttendanceService service;
    private UUID classId;
    private UUID termId;
    private UUID yearId;
    private UUID studentOneId;
    private UUID studentTwoId;

    @BeforeEach
    void setUp() {
        classId = UUID.randomUUID();
        termId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        studentOneId = UUID.randomUUID();
        studentTwoId = UUID.randomUUID();

        service = new ClassAttendanceService(
                classes,
                students,
                enrolments,
                terms,
                attendance,
                assignments,
                workflow,
                new ResourceAccessPolicy());

        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "admin", "Admin",
                Set.of(Role.TENANT_ADMIN), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, Set.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void progressUsesCompletedSemesterWeeksNotClickedStudents() {
        stubContext();
        when(attendance.findByClassId(classId)).thenReturn(List.of(
                record(studentOneId, "2026-01-01", AttendanceRecord.Status.PRESENT),
                record(studentTwoId, "2026-01-01", AttendanceRecord.Status.ABSENT),
                record(studentOneId, "2026-01-08", AttendanceRecord.Status.PRESENT)));

        Map<String, Object> overview = service.overview(classId, termId, 2);

        assertEquals(2, overview.get("totalStudents"));
        assertEquals(1L, overview.get("completedWeeks"));
        assertEquals(25.0, overview.get("semesterProgress"));
        assertEquals(1, overview.get("markedThisWeek"));
        assertEquals(50.0, overview.get("weekProgress"));

        @SuppressWarnings("unchecked")
        Map<String, String> statuses = (Map<String, String>) overview.get("statuses");
        assertEquals("PRESENT", statuses.get(studentOneId.toString()));
        assertTrue(!statuses.containsKey(studentTwoId.toString()));
    }

    @Test
    void saveWritesToSelectedWeekStartDateAndReturnsFreshOverview() {
        stubContext();
        when(attendance.findByClassId(classId)).thenReturn(List.of());
        when(workflow.markAttendance(eq(classId), eq("2026-01-08"), any()))
                .thenReturn(List.of(record(studentOneId, "2026-01-08", AttendanceRecord.Status.PRESENT)));

        Map<String, Object> overview = service.save(
                classId,
                termId,
                2,
                List.of(Map.of("studentId", studentOneId.toString(), "status", "PRESENT")));

        verify(workflow).markAttendance(eq(classId), eq("2026-01-08"), any());
        assertEquals(2, overview.get("week"));
        assertEquals(0L, overview.get("completedWeeks"));
    }

    private void stubContext() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName("Form 1A");
        schoolClass.setCode("DEMO-F1A");

        Term term = new Term();
        term.setId(termId);
        term.setAcademicYearId(yearId);
        term.setName("Term 1");
        term.setTermOrder(1);
        term.setStartDate("2026-01-01");
        term.setEndDate("2026-04-30");
        term.setNumberOfWeeks(4);
        term.setStatus(Term.Status.ACTIVE);

        Student first = student(studentOneId, "DEMO-STU-001", "Asha", "Mwalimu");
        Student second = student(studentTwoId, "DEMO-STU-002", "Juma", "Mwalimu");

        Enrolment firstEnrolment = new Enrolment();
        firstEnrolment.setStudentId(studentOneId);
        firstEnrolment.setClassId(classId);
        firstEnrolment.setAcademicYearId(yearId);
        firstEnrolment.setStatus(Enrolment.Status.ACTIVE);

        Enrolment secondEnrolment = new Enrolment();
        secondEnrolment.setStudentId(studentTwoId);
        secondEnrolment.setClassId(classId);
        secondEnrolment.setAcademicYearId(yearId);
        secondEnrolment.setStatus(Enrolment.Status.ACTIVE);

        when(classes.findAll()).thenReturn(List.of(schoolClass));
        when(terms.findById(termId)).thenReturn(Optional.of(term));
        when(terms.findAll()).thenReturn(List.of(term));
        when(enrolments.findByClassIdAndStatus(classId, Enrolment.Status.ACTIVE))
                .thenReturn(List.of(firstEnrolment, secondEnrolment));
        when(students.findAllById(any())).thenReturn(List.of(first, second));
    }

    private Student student(UUID id, String code, String firstName, String lastName) {
        Student student = new Student();
        student.setId(id);
        student.setStudentCode(code);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setStatus(Student.Status.ACTIVE);
        return student;
    }

    private AttendanceRecord record(UUID studentId, String date, AttendanceRecord.Status status) {
        AttendanceRecord record = new AttendanceRecord();
        record.setStudentId(studentId);
        record.setClassId(classId);
        record.setAttendanceDate(date);
        record.setStatus(status);
        return record;
    }
}
