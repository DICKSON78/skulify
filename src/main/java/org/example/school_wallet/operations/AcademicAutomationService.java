package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.academic.AcademicWorkflowService;
import org.example.school_wallet.academic.Enrolment;
import org.example.school_wallet.academic.GradeRecord;
import org.example.school_wallet.academic.GradingService;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.academic.SchoolClass;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.Subject;
import org.example.school_wallet.academic.Teacher;
import org.example.school_wallet.academic.TeacherAssignment;
import org.example.school_wallet.academic.TimetableEntry;
import org.example.school_wallet.common.error.ApiException;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.time.TimeValues;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the flat academic records into real school workflows: generated lesson
 * timetables, bulk marks entry, teacher duty rosters, exam scheduling, result
 * cards and the daily jobs that keep them moving without manual follow-up.
 */
@Service
@Transactional
public class AcademicAutomationService {

    private static final List<String> DEFAULT_DAYS =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
    private static final List<String> DEFAULT_DUTIES =
            List.of("ASSEMBLY", "DISCIPLINE", "LUNCH", "DISMISSAL");
    private static final Map<String, String[]> DUTY_TIMES = Map.of(
            "ASSEMBLY", new String[] {"07:00", "07:30"},
            "DISCIPLINE", new String[] {"10:00", "10:30"},
            "LUNCH", new String[] {"12:30", "13:00"},
            "DISMISSAL", new String[] {"15:30", "16:00"});

    private final EntityManager em;
    private final OperationsSupport support;
    private final AcademicWorkflowService workflow;
    private final GradingService grading;
    private final NotificationService notifications;

    public AcademicAutomationService(
            EntityManager em,
            OperationsSupport support,
            AcademicWorkflowService workflow,
            GradingService grading,
            NotificationService notifications) {
        this.em = em;
        this.support = support;
        this.workflow = workflow;
        this.grading = grading;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------
    // Timetable generation
    // ------------------------------------------------------------------

    /** Builds a clash-free weekly timetable from periods, subjects and teacher assignments. */
    public Map<String, Object> generateTimetable(Map<String, Object> body) {
        UUID classId = support.uuid(body.get("classId"));
        SchoolClass schoolClass = find(SchoolClass.class, classId);
        List<Period> periods = periods(body);
        if (periods.isEmpty()) {
            throw Errors.badRequest("NO_PERIODS", "Define at least one active period first");
        }
        List<Subject> subjects = em.createQuery(
                        "select s from Subject s where s.status = 'ACTIVE' order by s.code", Subject.class)
                .getResultList();
        if (subjects.isEmpty()) {
            throw Errors.badRequest("NO_SUBJECTS", "Define at least one active subject first");
        }
        List<TeacherAssignment> assignments = em.createQuery(
                        """
                        select a from TeacherAssignment a
                        where a.status = 'ACTIVE' and a.classId = :classId
                        """,
                        TeacherAssignment.class)
                .setParameter("classId", classId)
                .getResultList();

        String room = support.text(body.get("room"));
        if (room.isBlank()) {
            room = support.text(schoolClass.getRoom());
        }
        Set<String> days = days(body);
        if (Boolean.parseBoolean(support.text(body.get("replace")))) {
            em.createQuery("select t from TimetableEntry t where t.classId = :classId", TimetableEntry.class)
                    .setParameter("classId", classId)
                    .getResultList()
                    .forEach(em::remove);
            em.flush();
        }

        int generated = 0;
        int skipped = 0;
        for (String day : days) {
            for (int index = 0; index < periods.size(); index++) {
                Period period = periods.get(index);
                Subject subject = subjects.get(index % subjects.size());
                TimetableEntry entry = new TimetableEntry();
                entry.setClassId(classId);
                entry.setSubjectId(subject.getId());
                entry.setTeacherId(teacherFor(assignments, subject.getId(), schoolClass.getClassTeacherId()));
                entry.setDayOfWeek(TimetableEntry.DayOfWeek.valueOf(day));
                entry.setStartTime(period.getStartTime());
                entry.setEndTime(period.getEndTime());
                entry.setRoom(room);
                try {
                    workflow.validateTimetable(entry, null);
                    em.persist(entry);
                    em.flush();
                    generated++;
                } catch (ApiException conflict) {
                    // A class, teacher or room is already booked: leave the existing entry alone.
                    skipped++;
                }
            }
        }
        support.audit("TIMETABLE_GENERATED", "TIMETABLE", classId,
                Map.of("generated", generated, "skipped", skipped));
        return Map.of(
                "classId", classId.toString(),
                "generated", generated,
                "skipped", skipped,
                "periods", periods.size());
    }

    // ------------------------------------------------------------------
    // Marks upload
    // ------------------------------------------------------------------

    /** Upserts marks for a whole class. A teacher must be assigned to the subject. */
    public List<GradeRecord> uploadMarks(Map<String, Object> body) {
        UUID classId = support.uuid(body.get("classId"));
        UUID subjectId = support.uuid(body.get("subjectId"));
        workflow.requireGradeWrite(subjectId);

        String examType = support.text(body.get("examType"));
        if (examType.isBlank()) {
            examType = "TEST";
        }
        String term = support.text(body.get("term"));
        double maxScore = number(body.get("maxScore"), 100);
        UUID teacherId = CurrentUser.get().map(AuthenticatedUser::id).orElse(null);

        if (!(body.get("entries") instanceof List<?> rows) || rows.isEmpty()) {
            throw Errors.badRequest("NO_MARKS", "Submit at least one marks entry");
        }
        List<GradeRecord> saved = new ArrayList<>();
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row)) {
                continue;
            }
            UUID studentId = support.uuid(row.get("studentId"));
            if (!enrolled(studentId, classId)) {
                throw Errors.badRequest("NOT_ENROLLED", "A student in the upload is not enrolled in this class");
            }
            double score = number(row.get("score"), 0);
            if (score < 0 || score > maxScore) {
                throw Errors.badRequest("INVALID_SCORE", "Score must be between 0 and the maximum score");
            }
            GradeRecord grade = findGrade(studentId, subjectId, examType, term);
            boolean created = grade == null;
            if (created) {
                grade = new GradeRecord();
                grade.setStudentId(studentId);
                grade.setSubjectId(subjectId);
                grade.setExamType(examType);
                grade.setTerm(term);
            }
            grade.setScore(score);
            grade.setMaxScore(maxScore);
            grade.setGrade(grading.grade(score, maxScore));
            grade.setRemarks(support.text(row.get("remarks")));
            if (teacherId != null) {
                grade.setTeacherId(teacherId);
            }
            if (created) {
                em.persist(grade);
            } else {
                em.merge(grade);
            }
            saved.add(grade);

            if (Boolean.parseBoolean(support.text(body.get("notify")))) {
                Student student = em.find(Student.class, studentId);
                support.notifyGuardian(student, "New marks published",
                        examType + ": " + score + " / " + maxScore + " (" + grade.getGrade() + ")",
                        "grades", grade.getId());
            }
        }
        em.flush();
        support.audit("MARKS_UPLOADED", "GRADES", null,
                Map.of("count", saved.size(), "examType", examType, "classId", classId.toString()));
        return saved;
    }

    // ------------------------------------------------------------------
    // Teacher duty roster
    // ------------------------------------------------------------------

    /** Rotates available teachers (excluding those on approved leave) across duties. */
    public Map<String, Object> generateDutyRoster(Map<String, Object> body) {
        LocalDate start = parseDate(body.get("startDate"), LocalDate.now());
        LocalDate end = parseDate(body.get("endDate"), start.plusDays(30));
        if (end.isBefore(start)) {
            throw Errors.badRequest("INVALID_RANGE", "endDate must not be before startDate");
        }
        List<String> dutyTypes = strings(body.get("dutyTypes"), DEFAULT_DUTIES);
        Set<String> days = days(body);
        String term = support.text(body.get("term"));
        int perDay = support.integer(body.get("perDay"), dutyTypes.size());

        List<Teacher> teachers = activeTeachers();
        if (teachers.isEmpty()) {
            throw Errors.badRequest("NO_TEACHERS", "No active teachers are available for duty");
        }
        int generated = 0;
        int skipped = 0;
        int rotation = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek().name())) {
                continue;
            }
            Set<UUID> onLeave = teachersOnLeave(date);
            List<Teacher> available = onLeave.isEmpty()
                    ? teachers
                    : teachers.stream()
                            .filter(teacher -> !onLeave.contains(teacher.getId()))
                            .toList();
            if (available.isEmpty()) {
                skipped += perDay;
                continue;
            }
            for (int slot = 0; slot < perDay; slot++) {
                Teacher teacher = available.get(rotation++ % available.size());
                String dutyType = dutyTypes.get(slot % dutyTypes.size());
                if (hasDuty(teacher.getId(), date.toString(), dutyType)) {
                    skipped++;
                    continue;
                }
                String[] times = DUTY_TIMES.getOrDefault(dutyType, new String[] {"08:00", "09:00"});
                DutyAssignment assignment = new DutyAssignment();
                assignment.setTeacherId(teacher.getId());
                assignment.setDutyType(dutyType);
                assignment.setDutyDate(date.toString());
                assignment.setDayOfWeek(date.getDayOfWeek().name());
                assignment.setStartTime(times[0]);
                assignment.setEndTime(times[1]);
                assignment.setLocation(support.text(body.get("location")));
                assignment.setTerm(term);
                assignment.setStatus("SCHEDULED");
                em.persist(assignment);
                generated++;
            }
        }
        em.flush();
        support.audit("DUTY_ROSTER_GENERATED", "DUTY_ASSIGNMENTS", null,
                Map.of("generated", generated, "skipped", skipped));
        return Map.of("generated", generated, "skipped", skipped, "teachers", teachers.size());
    }

    public DutyAssignment cancelDuty(UUID dutyId) {
        DutyAssignment assignment = find(DutyAssignment.class, dutyId);
        assignment.setStatus("CANCELLED");
        em.merge(assignment);
        support.audit("DUTY_CANCELLED", "DUTY_ASSIGNMENTS", dutyId, Map.of());
        return assignment;
    }

    public StaffLeave decideLeave(UUID leaveId, Map<String, Object> body) {
        StaffLeave leave = find(StaffLeave.class, leaveId);
        String decision = support.text(body.get("decision")).toUpperCase();
        if (decision.isBlank()) {
            decision = Boolean.TRUE.equals(body.get("approved")) ? "APPROVE" : "REJECT";
        }
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw Errors.badRequest("INVALID_DECISION", "decision must be APPROVE or REJECT");
        }
        leave.setStatus(decision.equals("APPROVE") ? "APPROVED" : "REJECTED");
        leave.setApprovedBy(support.actor());
        leave.setApprovedAt(support.now());
        em.merge(leave);

        if (decision.equals("APPROVE")) {
            SchoolTask task = new SchoolTask();
            task.setTitle("Arrange duty cover during leave");
            task.setDescription("Staff leave " + leave.getStartDate() + " to " + leave.getEndDate()
                    + " was approved. Review the duty roster and reassign affected duties.");
            task.setStatus("OPEN");
            task.setPriority("HIGH");
            task.setSourceResource("staff-leaves");
            task.setSourceId(leave.getId());
            em.persist(task);
            notifyTeacher(leave.getTeacherId(), "Leave approved",
                    "Your leave from " + leave.getStartDate() + " to " + leave.getEndDate() + " was approved.",
                    "staff-leaves", leave.getId());
        }
        support.audit("STAFF_LEAVE_DECIDED", "STAFF_LEAVES", leaveId, Map.of("decision", decision));
        return leave;
    }

    // ------------------------------------------------------------------
    // Exams and results
    // ------------------------------------------------------------------

    /** Schedules every class/subject paper of an exam into clash-free slots. */
    public List<ExamSession> generateExamSchedule(UUID examId, Map<String, Object> body) {
        Exam exam = find(Exam.class, examId);
        List<SchoolClass> classes = selectedClasses(body);
        List<Subject> subjects = selectedSubjects(body);
        if (classes.isEmpty() || subjects.isEmpty()) {
            throw Errors.badRequest("NO_SCHEDULE_INPUT", "Classes and subjects are required");
        }
        LocalDate cursor = parseDate(body.get("startDate"), parseDate(exam.getStartDate(), LocalDate.now()));
        Set<String> days = days(body);
        LocalTime time = parseTime(body.get("startTime"), LocalTime.of(8, 0));
        int minutes = support.integer(body.get("periodMinutes"), 120);
        int breakMinutes = support.integer(body.get("breakMinutes"), 15);
        if (minutes <= 0) {
            throw Errors.badRequest("INVALID_DURATION", "periodMinutes must be greater than zero");
        }

        List<ExamSession> sessions = new ArrayList<>();
        for (SchoolClass schoolClass : classes) {
            for (Subject subject : subjects) {
                cursor = nextAllowedDay(cursor, days);
                String date = cursor.toString();
                while (hasSession(schoolClass.getId(), date, time, time.plusMinutes(minutes))) {
                    time = time.plusMinutes(minutes + breakMinutes);
                    if (time.plusMinutes(minutes).isAfter(LocalTime.of(17, 0))) {
                        cursor = nextAllowedDay(cursor.plusDays(1), days);
                        time = parseTime(body.get("startTime"), LocalTime.of(8, 0));
                        date = cursor.toString();
                    }
                }
                ExamSession session = new ExamSession();
                session.setExamId(exam.getId());
                session.setClassId(schoolClass.getId());
                session.setSubjectId(subject.getId());
                session.setTeacherId(invigilator(schoolClass.getId(), subject.getId()));
                session.setExamDate(date);
                session.setStartTime(time.toString());
                session.setEndTime(time.plusMinutes(minutes).toString());
                session.setRoom(schoolClass.getRoom());
                session.setStatus("SCHEDULED");
                em.persist(session);
                sessions.add(session);
                time = time.plusMinutes(minutes + breakMinutes);
            }
        }
        exam.setStatus("SCHEDULED");
        em.merge(exam);
        em.flush();
        support.audit("EXAM_SCHEDULED", "EXAMS", exam.getId(), Map.of("sessions", sessions.size()));
        return sessions;
    }

    /** Computes ranked result cards from the marks uploaded for an exam. */
    public List<ReportCard> generateReportCards(UUID examId, UUID classId) {
        Exam exam = find(Exam.class, examId);
        String enrolmentQuery = "select e from Enrolment e where e.status = 'ACTIVE'";
        if (classId != null) {
            enrolmentQuery += " and e.classId = :classId";
        }
        var enrolmentTyped = em.createQuery(enrolmentQuery, Enrolment.class);
        if (classId != null) {
            enrolmentTyped.setParameter("classId", classId);
        }
        List<Enrolment> enrolments = enrolmentTyped.getResultList();
        if (enrolments.isEmpty()) {
            throw Errors.badRequest("NO_ENROLMENTS", "No active enrolments found for result generation");
        }
        String where = "select g from GradeRecord g where g.examType = :examType";
        if (exam.getTerm() != null && !exam.getTerm().isBlank()) {
            where += " and g.term = :term";
        }
        var gradeQuery = em.createQuery(where, GradeRecord.class).setParameter("examType", exam.getExamType());
        if (exam.getTerm() != null && !exam.getTerm().isBlank()) {
            gradeQuery.setParameter("term", exam.getTerm());
        }
        Map<UUID, List<GradeRecord>> byStudent = new HashMap<>();
        for (GradeRecord grade : gradeQuery.getResultList()) {
            byStudent.computeIfAbsent(grade.getStudentId(), ignored -> new ArrayList<>()).add(grade);
        }

        Map<UUID, Double> averages = new LinkedHashMap<>();
        for (Enrolment enrolment : enrolments) {
            List<GradeRecord> grades = byStudent.get(enrolment.getStudentId());
            if (grades != null && !grades.isEmpty()) {
                averages.put(enrolment.getStudentId(), grading.averageScore(grades));
            }
        }
        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(averages.entrySet());
        ranked.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> entry) -> entry.getValue()).reversed());

        List<ReportCard> cards = new ArrayList<>();
        int position = 0;
        for (Map.Entry<UUID, Double> row : ranked) {
            position++;
            UUID studentId = row.getKey();
            List<GradeRecord> grades = byStudent.get(studentId);
            double total = grades.stream().mapToDouble(grade -> grade.getScore() == null ? 0 : grade.getScore()).sum();
            double max = grades.stream().mapToDouble(grade -> grade.getMaxScore() == null ? 100 : grade.getMaxScore()).sum();
            ReportCard card = findReportCard(studentId, exam.getId());
            boolean created = card == null;
            if (created) {
                card = new ReportCard();
                card.setReference(reference("RC"));
                card.setStudentId(studentId);
                card.setExamId(exam.getId());
            }
            Enrolment enrolment = enrolments.stream()
                    .filter(item -> studentId.equals(item.getStudentId()))
                    .findFirst()
                    .orElse(null);
            card.setClassId(classId);
            if (enrolment != null) {
                card.setClassId(enrolment.getClassId());
                card.setAcademicYearId(enrolment.getAcademicYearId());
            }
            card.setTerm(exam.getTerm());
            card.setTotalScore(BigDecimal.valueOf(Math.round(total * 100) / 100.0));
            card.setMaxScore(BigDecimal.valueOf(Math.round(max * 100) / 100.0));
            card.setAverage(BigDecimal.valueOf(Math.round(row.getValue() * 10) / 10.0));
            card.setGrade(grading.grade(row.getValue(), 100.0));
            card.setPosition(position);
            card.setClassSize(ranked.size());
            card.setStatus("DRAFT");
            card.setPublishedAt(null);
            if (created) {
                em.persist(card);
            } else {
                em.merge(card);
            }
            cards.add(card);
        }
        em.flush();
        support.audit("RESULT_CARDS_GENERATED", "REPORT_CARDS", exam.getId(),
                Map.of("count", cards.size()));
        return cards;
    }

    public ReportCard publishReportCard(UUID cardId) {
        ReportCard card = find(ReportCard.class, cardId);
        card.setStatus("PUBLISHED");
        card.setPublishedAt(TimeValues.now());
        em.merge(card);
        Student student = em.find(Student.class, card.getStudentId());
        Exam exam = card.getExamId() == null ? null : em.find(Exam.class, card.getExamId());
        String examName = exam == null ? "term results" : exam.getName();
        support.notifyGuardian(student, "Results published",
                examName + ": grade " + support.text(card.getGrade()) + ", position "
                        + support.text(card.getPosition()) + " of " + support.text(card.getClassSize()),
                "report-cards", card.getId());
        support.audit("RESULT_CARD_PUBLISHED", "REPORT_CARDS", cardId, Map.of());
        return card;
    }

    // ------------------------------------------------------------------
    // Daily automation jobs
    // ------------------------------------------------------------------

    /** Publishes draft result cards once their exam has ended. */
    public int publishDueResults() {
        List<ReportCard> drafts = em.createQuery(
                        "select r from ReportCard r where r.status = 'DRAFT'", ReportCard.class)
                .getResultList();
        int count = 0;
        for (ReportCard card : drafts) {
            if (card.getExamId() == null) {
                continue;
            }
            Exam exam = em.find(Exam.class, card.getExamId());
            LocalDate end = exam == null ? null : dateOf(exam.getEndDate());
            if (end != null && !end.isAfter(LocalDate.now())) {
                publishReportCard(card.getId());
                count++;
            }
        }
        return count;
    }

    /** Reminds teachers about the duties they have tomorrow. */
    public int dutyReminders() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        List<DutyAssignment> duties = em.createQuery(
                        """
                        select d from DutyAssignment d
                        where d.status = 'SCHEDULED' and d.dutyDate = :date
                        """,
                        DutyAssignment.class)
                .setParameter("date", tomorrow)
                .getResultList();
        for (DutyAssignment duty : duties) {
            notifyTeacher(duty.getTeacherId(), "Duty reminder",
                    duty.getDutyType() + " duty tomorrow at " + support.text(duty.getStartTime())
                            + (support.text(duty.getLocation()).isBlank() ? "" : " · " + duty.getLocation()),
                    "duty-assignments", duty.getId());
        }
        return duties.size();
    }

    /** Alerts guardians about exam papers scheduled for tomorrow. */
    public int examReminders() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        List<ExamSession> sessions = em.createQuery(
                        """
                        select s from ExamSession s
                        where s.status = 'SCHEDULED' and s.examDate = :date
                        """,
                        ExamSession.class)
                .setParameter("date", tomorrow)
                .getResultList();
        for (ExamSession session : sessions) {
            Subject subject = em.find(Subject.class, session.getSubjectId());
            String subjectName = subject == null ? "Exam" : subject.getName();
            List<Enrolment> enrolled = em.createQuery(
                            """
                            select e from Enrolment e
                            where e.status = 'ACTIVE' and e.classId = :classId
                            """,
                            Enrolment.class)
                    .setParameter("classId", session.getClassId())
                    .getResultList();
            for (Enrolment enrolment : enrolled) {
                Student student = em.find(Student.class, enrolment.getStudentId());
                support.notifyGuardian(student, "Exam tomorrow",
                        subjectName + " at " + session.getStartTime() + " in "
                                + support.text(session.getRoom()),
                        "exam-sessions", session.getId());
            }
        }
        return sessions.size();
    }

    /** Escalates overdue work automatically and notifies the assigned staff member. */
    public int escalateOverdueTasks() {
        String today = LocalDate.now().toString();
        List<SchoolTask> overdue = em.createQuery(
                        """
                        select t from SchoolTask t
                        where t.status not in ('COMPLETED','CANCELLED')
                          and t.dueDate is not null and t.dueDate < :today
                        """,
                        SchoolTask.class)
                .setParameter("today", today)
                .getResultList();
        for (SchoolTask task : overdue) {
            task.setPriority("URGENT");
            em.merge(task);
            if (task.getAssignedTo() != null) {
                notifyTeacher(task.getAssignedTo(), "Overdue school task",
                        task.getTitle() + " was due on " + task.getDueDate(),
                        "school-tasks", task.getId());
            }
        }
        return overdue.size();
    }

    /** Prepares the daily staff roll call so nothing is recorded by hand twice. */
    public int prepareStaffAttendance() {
        String today = LocalDate.now().toString();
        int count = 0;
        for (Teacher teacher : activeTeachers()) {
            Long existing = em.createQuery(
                            """
                            select count(a) from StaffAttendance a
                            where a.staffId = :staffId and a.attendanceDate = :today
                            """,
                            Long.class)
                    .setParameter("staffId", teacher.getId())
                    .setParameter("today", today)
                    .getSingleResult();
            if (existing != null && existing > 0) {
                continue;
            }
            StaffAttendance attendance = new StaffAttendance();
            attendance.setStaffId(teacher.getId());
            attendance.setAttendanceDate(today);
            attendance.setStatus("PENDING");
            attendance.setRecordedBy("SYSTEM");
            attendance.setRemarks("Auto-generated roll call - confirm attendance");
            em.persist(attendance);
            count++;
        }
        return count;
    }

    public Map<String, Object> runDaily() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultsPublished", publishDueResults());
        result.put("dutyReminders", dutyReminders());
        result.put("examReminders", examReminders());
        result.put("tasksEscalated", escalateOverdueTasks());
        result.put("attendancePrepared", prepareStaffAttendance());
        support.audit("DAILY_AUTOMATION_RUN", "SCHOOL", null, result);
        return result;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<>();
        result.put("periods", count("Period"));
        result.put("exams", count("Exam"));
        result.put("examSessions", count("ExamSession"));
        result.put("duties", count("DutyAssignment"));
        result.put("reportCards", count("ReportCard"));
        result.put("draftResults", em.createQuery(
                        "select count(r) from ReportCard r where r.status = 'DRAFT'", Long.class)
                .getSingleResult());
        result.put("upcomingDuties", em.createQuery(
                        """
                        select count(d) from DutyAssignment d
                        where d.status = 'SCHEDULED' and d.dutyDate >= :today
                        """,
                        Long.class)
                .setParameter("today", LocalDate.now().toString())
                .getSingleResult());
        result.put("staffOnLeave", em.createQuery(
                        """
                        select count(l) from StaffLeave l
                        where l.status = 'APPROVED' and l.endDate >= :today
                        """,
                        Long.class)
                .setParameter("today", LocalDate.now().toString())
                .getSingleResult());
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<Period> periods(Map<String, Object> body) {
        if (body.get("periodIds") instanceof List<?> ids && !ids.isEmpty()) {
            List<UUID> parsed = ids.stream().map(support::uuid).toList();
            return em.createQuery(
                            "select p from Period p where p.id in :ids order by p.sortOrder, p.startTime",
                            Period.class)
                    .setParameter("ids", parsed)
                    .getResultList();
        }
        return em.createQuery(
                        "select p from Period p where p.active = true order by p.sortOrder, p.startTime",
                        Period.class)
                .getResultList();
    }

    private List<SchoolClass> selectedClasses(Map<String, Object> body) {
        if (body.get("classIds") instanceof List<?> ids && !ids.isEmpty()) {
            return em.createQuery("select c from SchoolClass c where c.id in :ids", SchoolClass.class)
                    .setParameter("ids", ids.stream().map(support::uuid).toList())
                    .getResultList();
        }
        return em.createQuery(
                        "select c from SchoolClass c where c.status = 'ACTIVE' order by c.code", SchoolClass.class)
                .getResultList();
    }

    private List<Subject> selectedSubjects(Map<String, Object> body) {
        if (body.get("subjectIds") instanceof List<?> ids && !ids.isEmpty()) {
            return em.createQuery("select s from Subject s where s.id in :ids", Subject.class)
                    .setParameter("ids", ids.stream().map(support::uuid).toList())
                    .getResultList();
        }
        return em.createQuery(
                        "select s from Subject s where s.status = 'ACTIVE' order by s.code", Subject.class)
                .getResultList();
    }

    private UUID teacherFor(List<TeacherAssignment> assignments, UUID subjectId, UUID classTeacherId) {
        return assignments.stream()
                .filter(row -> subjectId.equals(row.getSubjectId()))
                .map(TeacherAssignment::getTeacherId)
                .findFirst()
                .orElse(classTeacherId);
    }

    private UUID invigilator(UUID classId, UUID subjectId) {
        return em.createQuery(
                        """
                        select a.teacherId from TeacherAssignment a
                        where a.status = 'ACTIVE' and a.classId = :classId and a.subjectId = :subjectId
                        """,
                        UUID.class)
                .setParameter("classId", classId)
                .setParameter("subjectId", subjectId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private boolean enrolled(UUID studentId, UUID classId) {
        if (studentId == null || classId == null) {
            return false;
        }
        Long count = em.createQuery(
                        """
                        select count(e) from Enrolment e
                        where e.studentId = :studentId and e.classId = :classId and e.status = 'ACTIVE'
                        """,
                        Long.class)
                .setParameter("studentId", studentId)
                .setParameter("classId", classId)
                .getSingleResult();
        return count != null && count > 0;
    }

    private GradeRecord findGrade(UUID studentId, UUID subjectId, String examType, String term) {
        String query = "select g from GradeRecord g where g.studentId = :studentId"
                + " and g.subjectId = :subjectId and g.examType = :examType";
        if (term != null && !term.isBlank()) {
            query += " and g.term = :term";
        }
        var typed = em.createQuery(query, GradeRecord.class)
                .setParameter("studentId", studentId)
                .setParameter("subjectId", subjectId)
                .setParameter("examType", examType);
        if (term != null && !term.isBlank()) {
            typed.setParameter("term", term);
        }
        return typed.getResultStream().findFirst().orElse(null);
    }

    private ReportCard findReportCard(UUID studentId, UUID examId) {
        return em.createQuery(
                        "select r from ReportCard r where r.studentId = :studentId and r.examId = :examId",
                        ReportCard.class)
                .setParameter("studentId", studentId)
                .setParameter("examId", examId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private boolean hasSession(UUID classId, String date, LocalTime start, LocalTime end) {
        Long count = em.createQuery(
                        """
                        select count(s) from ExamSession s
                        where s.classId = :classId and s.examDate = :date
                          and s.startTime < :end and s.endTime > :start
                        """,
                        Long.class)
                .setParameter("classId", classId)
                .setParameter("date", date)
                .setParameter("start", start.toString())
                .setParameter("end", end.toString())
                .getSingleResult();
        return count != null && count > 0;
    }

    private boolean hasDuty(UUID teacherId, String date, String dutyType) {
        Long count = em.createQuery(
                        """
                        select count(d) from DutyAssignment d
                        where d.teacherId = :teacherId and d.dutyDate = :date and d.dutyType = :dutyType
                          and d.status != 'CANCELLED'
                        """,
                        Long.class)
                .setParameter("teacherId", teacherId)
                .setParameter("date", date)
                .setParameter("dutyType", dutyType)
                .getSingleResult();
        return count != null && count > 0;
    }

    private List<Teacher> activeTeachers() {
        return em.createQuery(
                        "select t from Teacher t where t.status = 'ACTIVE' order by t.employeeCode", Teacher.class)
                .getResultList();
    }

    private Set<UUID> teachersOnLeave(LocalDate date) {
        List<StaffLeave> leaves = em.createQuery(
                        """
                        select l from StaffLeave l
                        where l.status = 'APPROVED' and l.startDate <= :date and l.endDate >= :date
                        """,
                        StaffLeave.class)
                .setParameter("date", date.toString())
                .getResultList();
        Set<UUID> ids = new LinkedHashSet<>();
        leaves.forEach(leave -> ids.add(leave.getTeacherId()));
        return ids;
    }

    private void notifyTeacher(UUID teacherId, String subject, String body, String resource, UUID relatedId) {
        if (teacherId == null) {
            return;
        }
        Teacher teacher = em.find(Teacher.class, teacherId);
        if (teacher == null) {
            return;
        }
        if (teacher.getUserId() != null) {
            notifications.send(NotificationOutbox.Channel.IN_APP,
                    teacher.getUserId().toString(), subject, body, resource, relatedId);
        }
        if (teacher.getPhone() != null && !teacher.getPhone().isBlank()) {
            notifications.send(NotificationOutbox.Channel.SMS,
                    teacher.getPhone(), subject, body, resource, relatedId);
        }
    }

    private Set<String> days(Map<String, Object> body) {
        List<String> raw = strings(body.get("days"), DEFAULT_DAYS);
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            String day = value.trim().toUpperCase();
            try {
                TimetableEntry.DayOfWeek.valueOf(day);
                result.add(day);
            } catch (IllegalArgumentException ignored) {
                // Unknown days are ignored so a partially filled form cannot break generation.
            }
        }
        if (result.isEmpty()) {
            result.addAll(DEFAULT_DAYS);
        }
        return result;
    }

    private LocalDate nextAllowedDay(LocalDate date, Set<String> days) {
        LocalDate cursor = date;
        for (int attempt = 0; attempt < 14 && !days.contains(cursor.getDayOfWeek().name()); attempt++) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private List<String> strings(Object value, List<String> fallback) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> result.add(support.text(item)));
        } else if (!support.text(value).isBlank()) {
            for (String part : support.text(value).split(",")) {
                result.add(part.trim());
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    private LocalDate parseDate(Object value, LocalDate fallback) {
        String raw = support.text(value);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (RuntimeException ex) {
            throw Errors.badRequest("INVALID_DATE", "Invalid date: " + raw);
        }
    }

    private LocalTime parseTime(Object value, LocalTime fallback) {
        String raw = support.text(value);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw);
        } catch (RuntimeException ex) {
            throw Errors.badRequest("INVALID_TIME", "Times must use HH:mm format");
        }
    }

    private LocalDate dateOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private double number(Object value, double fallback) {
        String raw = support.text(value);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            throw Errors.badRequest("INVALID_NUMBER", "Invalid number: " + raw);
        }
    }

    private long count(String entity) {
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private <T> T find(Class<T> type, UUID id) {
        T entity = id == null ? null : em.find(type, id);
        if (entity == null) {
            throw Errors.notFound(type.getSimpleName().toUpperCase() + "_NOT_FOUND", "Record not found");
        }
        return entity;
    }
}
