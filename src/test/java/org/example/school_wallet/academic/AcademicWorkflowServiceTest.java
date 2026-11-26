package org.example.school_wallet.academic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.common.error.ApiException;
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
class AcademicWorkflowServiceTest {

    @Mock
    private AttendanceRecordRepository attendance;

    @Mock
    private StudentRepository students;

    @Mock
    private TeacherRepository teachers;

    @Mock
    private EnrolmentRepository enrolments;

    @Mock
    private TeacherAssignmentRepository assignments;

    @Mock
    private TimetableEntryRepository timetable;

    @Mock
    private NotificationService notifications;

    private AcademicWorkflowService service;
    private UUID userId;
    private UUID teacherRecordId;
    private UUID classId;
    private UUID otherClassId;
    private UUID subjectId;
    private UUID otherSubjectId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("22222222-2222-2222-2222-222222222223");
        teacherRecordId = UUID.randomUUID();
        classId = UUID.randomUUID();
        otherClassId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        otherSubjectId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        service = new AcademicWorkflowService(
                attendance,
                students,
                teachers,
                enrolments,
                assignments,
                timetable,
                notifications,
                new ResourceAccessPolicy());

        AuthenticatedUser user = new AuthenticatedUser(
                userId, UUID.randomUUID(), "teacher", "Teacher", Set.of(Role.TEACHER), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bulkAttendanceAcceptsAssignmentStoredAsTeacherRecordId() {
        Teacher teacher = teacherLinkedToUser();
        when(teachers.findAll()).thenReturn(List.of(teacher));
        when(assignments.findAll()).thenReturn(List.of(assignment(classId, subjectId)));
        when(enrolments.findByStudentIdAndStatus(studentId, Enrolment.Status.ACTIVE))
                .thenReturn(List.of(enrolment(classId)));
        when(attendance.findByStudentIdAndAttendanceDate(studentId, "2026-11-20"))
                .thenReturn(Optional.empty());
        when(attendance.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AttendanceRecord> saved = service.markAttendance(
                classId,
                "2026-11-20",
                List.of(Map.of("studentId", studentId.toString(), "status", "PRESENT")));

        assertEquals(1, saved.size());
        assertEquals(userId, saved.get(0).getTeacherId());
        assertEquals(classId, saved.get(0).getClassId());
    }

    @Test
    void bulkAttendanceRejectsUnassignedClass() {
        when(teachers.findAll()).thenReturn(List.of(teacherLinkedToUser()));
        when(assignments.findAll()).thenReturn(List.of(assignment(classId, subjectId)));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service.markAttendance(
                        otherClassId,
                        "2026-11-20",
                        List.of(Map.of("studentId", studentId.toString(), "status", "PRESENT"))));

        assertEquals("NOT_ASSIGNED", error.getCode());
    }

    @Test
    void singleAttendanceInheritsClassAndChecksAssignment() {
        AttendanceRecord record = new AttendanceRecord();
        record.setStudentId(studentId);
        record.setStatus(AttendanceRecord.Status.PRESENT);

        when(teachers.findAll()).thenReturn(List.of(teacherLinkedToUser()));
        when(assignments.findAll()).thenReturn(List.of(assignment(classId, subjectId)));
        when(enrolments.findByStudentIdAndStatus(studentId, Enrolment.Status.ACTIVE))
                .thenReturn(List.of(enrolment(classId)));

        service.validateAttendanceWrite(record);

        assertEquals(classId, record.getClassId());
    }

    @Test
    void gradeWriteRequiresAssignedSubject() {
        when(teachers.findAll()).thenReturn(List.of(teacherLinkedToUser()));
        when(assignments.findAll()).thenReturn(List.of(assignment(classId, subjectId)));

        assertDoesNotThrow(() -> service.requireGradeWrite(subjectId));
        ApiException error = assertThrows(ApiException.class, () -> service.requireGradeWrite(otherSubjectId));

        assertEquals("NOT_ASSIGNED_SUBJECT", error.getCode());
    }

    private Teacher teacherLinkedToUser() {
        Teacher teacher = new Teacher();
        teacher.setId(teacherRecordId);
        teacher.setUserId(userId);
        return teacher;
    }

    private TeacherAssignment assignment(UUID assignedClassId, UUID assignedSubjectId) {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacherId(teacherRecordId);
        assignment.setClassId(assignedClassId);
        assignment.setSubjectId(assignedSubjectId);
        assignment.setScope(TeacherAssignment.Scope.CLASS_TEACHER);
        assignment.setStatus(TeacherAssignment.Status.ACTIVE);
        return assignment;
    }

    private Enrolment enrolment(UUID enrolledClassId) {
        Enrolment row = new Enrolment();
        row.setStudentId(studentId);
        row.setClassId(enrolledClassId);
        row.setStatus(Enrolment.Status.ACTIVE);
        return row;
    }
}
