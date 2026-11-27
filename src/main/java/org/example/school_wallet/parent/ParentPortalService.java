package org.example.school_wallet.parent;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.school_wallet.academic.AttendanceRecord;
import org.example.school_wallet.academic.Enrolment;
import org.example.school_wallet.academic.FeeRecord;
import org.example.school_wallet.academic.GradeRecord;
import org.example.school_wallet.academic.SchoolClass;
import org.example.school_wallet.academic.SemesterAttendance;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.Subject;
import org.example.school_wallet.academic.Term;
import org.example.school_wallet.academic.TimetableEntry;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.operations.ParentLetter;
import org.example.school_wallet.operations.ReportCard;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One aggregated, row-scoped view of everything a guardian needs for a child. */
@Service
@Transactional(readOnly = true)
public class ParentPortalService {

    private final EntityManager em;
    private final ResourceAccessPolicy policy;

    public ParentPortalService(EntityManager em, ResourceAccessPolicy policy) {
        this.em = em;
        this.policy = policy;
    }

    public List<Map<String, Object>> children() {
        AuthenticatedUser user = CurrentUser.require();
        String query = "select s from Student s";
        if (!policy.isPrivileged(user)) {
            query += " where s.guardianUserId = :userId or s.userId = :userId";
        }
        var typed = em.createQuery(query, Student.class);
        if (!policy.isPrivileged(user)) {
            typed.setParameter("userId", user.id());
        }
        return typed.getResultList().stream()
                .map(this::childView)
                .sorted(Comparator.comparing(row -> String.valueOf(row.getOrDefault("name", ""))))
                .toList();
    }

    public Map<String, Object> overview(UUID studentId) {
        Student student = student(studentId);
        Map<UUID, String> subjects = subjectNames();
        Map<UUID, String> classes = classNames();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("student", childView(student));

        List<GradeRecord> grades = em.createQuery(
                        "select g from GradeRecord g where g.studentId = :studentId order by g.term, g.subjectId",
                        GradeRecord.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        result.put("gradesByTerm", gradesByTerm(grades, subjects));
        result.put("grades", grades.stream().map(grade -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", grade.getId());
            row.put("term", grade.getTerm());
            row.put("examType", grade.getExamType());
            row.put("subject", subjects.getOrDefault(grade.getSubjectId(), "Subject"));
            row.put("score", grade.getScore());
            row.put("maxScore", grade.getMaxScore());
            row.put("grade", grade.getGrade());
            row.put("remarks", grade.getRemarks());
            return row;
        }).toList());

        List<AttendanceRecord> attendance = em.createQuery(
                        "select a from AttendanceRecord a where a.studentId = :studentId order by a.attendanceDate desc",
                        AttendanceRecord.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        result.put("attendance", attendanceSummary(attendance));
        result.put("attendanceRecords", attendance.stream().limit(60).map(record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", record.getAttendanceDate());
            row.put("status", record.getStatus());
            row.put("checkIn", record.getCheckIn());
            row.put("remarks", record.getRemarks());
            return row;
        }).toList());

        List<ReportCard> cards = em.createQuery(
                        "select r from ReportCard r where r.studentId = :studentId order by r.term, r.publishedAt desc",
                        ReportCard.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        result.put("reportCards", cards.stream().map(card -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", card.getId());
            row.put("reference", card.getReference());
            row.put("term", card.getTerm());
            row.put("average", card.getAverage());
            row.put("grade", card.getGrade());
            row.put("position", card.getPosition());
            row.put("classSize", card.getClassSize());
            row.put("status", card.getStatus());
            row.put("publishedAt", card.getPublishedAt());
            row.put("className", classes.getOrDefault(card.getClassId(), ""));
            return row;
        }).toList());

        List<ParentLetter> letters = em.createQuery(
                        "select l from ParentLetter l where l.studentId = :studentId order by l.generatedAt desc",
                        ParentLetter.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        result.put("letters", letters.stream().map(letter -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", letter.getId());
            row.put("reference", letter.getReference());
            row.put("title", letter.getTitle());
            row.put("term", letter.getTerm());
            row.put("content", letter.getContent());
            row.put("requirements", letter.getRequirements());
            row.put("status", letter.getStatus());
            row.put("generatedAt", letter.getGeneratedAt());
            return row;
        }).toList());

        List<FeeRecord> fees = em.createQuery(
                        "select f from FeeRecord f where f.studentId = :studentId order by f.dueDate",
                        FeeRecord.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (FeeRecord fee : fees) {
            billed = billed.add(fee.getAmount() == null ? BigDecimal.ZERO : fee.getAmount());
            paid = paid.add(fee.getPaidAmount() == null ? BigDecimal.ZERO : fee.getPaidAmount());
        }
        Map<String, Object> feeSummary = new LinkedHashMap<>();
        feeSummary.put("billed", billed);
        feeSummary.put("paid", paid);
        feeSummary.put("outstanding", billed.subtract(paid));
        result.put("fees", feeSummary);
        result.put("feeRecords", fees.stream().map(fee -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reference", fee.getReference());
            row.put("feeType", fee.getFeeType());
            row.put("amount", fee.getAmount());
            row.put("paidAmount", fee.getPaidAmount());
            row.put("status", fee.getStatus());
            row.put("dueDate", fee.getDueDate());
            return row;
        }).toList());

        List<Enrolment> enrolments = em.createQuery(
                        "select e from Enrolment e where e.studentId = :studentId order by e.enrolmentDate desc",
                        Enrolment.class)
                .setParameter("studentId", student.getId())
                .getResultList();
        result.put("enrolments", enrolments.stream().map(enrolment -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", enrolment.getId());
            row.put("className", classes.getOrDefault(enrolment.getClassId(), ""));
            row.put("status", enrolment.getStatus());
            row.put("enrolmentDate", enrolment.getEnrolmentDate());
            return row;
        }).toList());
        result.put("semesterAttendance", semesterAttendance(student, enrolments, classes));

        UUID classId = currentClassId(enrolments);
        List<TimetableEntry> timetable = classId == null ? List.of() : em.createQuery(
                        "select t from TimetableEntry t where t.classId = :classId order by t.dayOfWeek, t.startTime",
                        TimetableEntry.class)
                .setParameter("classId", classId)
                .getResultList();
        result.put("timetable", timetable.stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", entry.getDayOfWeek());
            row.put("start", entry.getStartTime());
            row.put("end", entry.getEndTime());
            row.put("subject", subjects.getOrDefault(entry.getSubjectId(), ""));
            row.put("room", entry.getRoom());
            return row;
        }).toList());

        return result;
    }

    private Student student(UUID studentId) {
        if (studentId == null) {
            List<Map<String, Object>> children = children();
            if (children.isEmpty()) {
                throw Errors.badRequest("NO_CHILD", "No linked student was found for this account");
            }
            studentId = UUID.fromString(String.valueOf(children.get(0).get("id")));
        }
        Student student = em.find(Student.class, studentId);
        if (student == null) {
            throw Errors.notFound("STUDENT_NOT_FOUND", "Student not found");
        }
        AuthenticatedUser user = CurrentUser.require();
        if (!policy.isPrivileged(user)
                && !user.id().equals(student.getGuardianUserId())
                && !user.id().equals(student.getUserId())) {
            throw Errors.forbidden("NOT_YOUR_RECORD", "This is not your child's record");
        }
        return student;
    }

    private UUID currentClassId(List<Enrolment> enrolments) {
        return enrolments.stream()
                .filter(enrolment -> Enrolment.Status.ACTIVE.equals(enrolment.getStatus()))
                .map(Enrolment::getClassId)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> childView(Student student) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", student.getId());
        row.put("studentCode", student.getStudentCode());
        row.put("name", (student.getFirstName() + " " + student.getLastName()).trim());
        row.put("className", student.getClassName());
        row.put("status", student.getStatus());
        row.put("guardianName", student.getGuardianName());
        row.put("guardianPhone", student.getGuardianPhone());
        row.put("email", student.getEmail());
        row.put("phone", student.getPhone());
        row.put("admissionDate", student.getAdmissionDate());
        return row;
    }

    private Map<UUID, String> subjectNames() {
        Map<UUID, String> result = new LinkedHashMap<>();
        em.createQuery("select s from Subject s", Subject.class).getResultList()
                .forEach(subject -> result.put(subject.getId(), subject.getName()));
        return result;
    }

    private Map<UUID, String> classNames() {
        Map<UUID, String> result = new LinkedHashMap<>();
        em.createQuery("select c from SchoolClass c", SchoolClass.class).getResultList()
                .forEach(schoolClass -> result.put(schoolClass.getId(), schoolClass.getName()));
        return result;
    }

    private List<Map<String, Object>> gradesByTerm(
            List<GradeRecord> grades, Map<UUID, String> subjects) {
        Map<String, List<GradeRecord>> grouped = new LinkedHashMap<>();
        for (GradeRecord grade : grades) {
            grouped.computeIfAbsent(grade.getTerm() == null ? "TERM" : grade.getTerm(),
                    ignored -> new ArrayList<>()).add(grade);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<GradeRecord>> entry : grouped.entrySet()) {
            Map<String, Object> term = new LinkedHashMap<>();
            term.put("term", entry.getKey());
            double total = 0;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (GradeRecord grade : entry.getValue()) {
                total += grade.getScore() == null ? 0 : grade.getScore();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("subject", subjects.getOrDefault(grade.getSubjectId(), "Subject"));
                row.put("examType", grade.getExamType());
                row.put("score", grade.getScore());
                row.put("maxScore", grade.getMaxScore());
                row.put("grade", grade.getGrade());
                row.put("remarks", grade.getRemarks());
                rows.add(row);
            }
            term.put("subjects", rows);
            term.put("average", rows.isEmpty() ? 0 : Math.round(total / rows.size() * 10.0) / 10.0);
            result.add(term);
        }
        return result;
    }

    private Map<String, Object> semesterAttendance(
            Student student, List<Enrolment> enrolments, Map<UUID, String> classes) {
        Map<String, Object> result = new LinkedHashMap<>();
        UUID classId = currentClassId(enrolments);
        Term term = activeTerm();
        int weekCount = term == null ? 0 : SemesterAttendance.weekCount(term);
        String fallbackClass = text(student.getClassName());

        result.put("className", classId == null
                ? fallbackClass
                : classes.getOrDefault(classId, fallbackClass));
        result.put("termName", term == null ? "" : text(term.getName()));
        result.put("numberOfWeeks", weekCount);
        result.put("week", 0);
        result.put("weekStart", "");
        result.put("weekEnd", "");
        result.put("completedWeeks", 0L);
        result.put("semesterProgress", 0.0);
        result.put("markedThisWeek", 0);
        result.put("weekProgress", 0.0);
        result.put("totalStudents", 0);
        result.put("weeks", List.of());
        result.put("student", SemesterAttendance.studentSummary(List.of()));

        if (classId == null || term == null || weekCount == 0) {
            return result;
        }

        Set<UUID> rosterIds = rosterIds(classId, term);
        List<AttendanceRecord> classRecords = classRecords(classId);
        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int number = 1; number <= weekCount; number++) {
            weeks.add(SemesterAttendance.weekSummary(
                    term, number, rosterIds, classRecords, student.getId()));
        }

        long completedWeeks = SemesterAttendance.completedWeeks(weeks);
        // Show the child's latest recorded week first; fall back to the first
        // incomplete register when the child has not been marked yet.
        int selectedWeekNumber = 1;
        boolean focusMarked = false;
        for (Map<String, Object> week : weeks) {
            if (!text(week.get("status")).isEmpty()) {
                selectedWeekNumber = intValue(week.get("number"));
                focusMarked = true;
            }
        }
        if (!focusMarked) {
            for (Map<String, Object> week : weeks) {
                if (!Boolean.TRUE.equals(week.get("complete"))) {
                    selectedWeekNumber = intValue(week.get("number"));
                    break;
                }
            }
        }
        Map<String, Object> selectedWeek =
                weeks.get(Math.min(weeks.size(), Math.max(1, selectedWeekNumber)) - 1);
        int markedThisWeek = intValue(selectedWeek.get("marked"));
        int totalStudents = rosterIds.size();
        double semesterProgress = SemesterAttendance.round(completedWeeks * 100.0 / weekCount);
        double weekProgress = totalStudents == 0
                ? 0
                : SemesterAttendance.round(markedThisWeek * 100.0 / totalStudents);

        result.put("week", selectedWeekNumber);
        result.put("weekStart", text(selectedWeek.get("startDate")));
        result.put("weekEnd", text(selectedWeek.get("endDate")));
        result.put("completedWeeks", completedWeeks);
        result.put("semesterProgress", semesterProgress);
        result.put("markedThisWeek", markedThisWeek);
        result.put("weekProgress", weekProgress);
        result.put("totalStudents", totalStudents);
        result.put("weeks", SemesterAttendance.publicWeeks(weeks));
        result.put("student", SemesterAttendance.studentSummary(weeks));
        return result;
    }

    private Term activeTerm() {
        List<Term> all = em.createQuery("select t from Term t", Term.class).getResultList();
        return all.stream()
                .filter(term -> term.getStatus() == Term.Status.ACTIVE)
                .min(Comparator.comparingInt(Term::getTermOrder))
                .orElseGet(() -> all.stream()
                        .min(Comparator.comparingInt(Term::getTermOrder))
                        .orElse(null));
    }

    private Set<UUID> rosterIds(UUID classId, Term term) {
        List<Enrolment> rows = em.createQuery(
                        "select e from Enrolment e where e.classId = :classId and e.status = :status",
                        Enrolment.class)
                .setParameter("classId", classId)
                .setParameter("status", Enrolment.Status.ACTIVE)
                .getResultList();
        return rows.stream()
                .filter(row -> term.getAcademicYearId() == null
                        || term.getAcademicYearId().equals(row.getAcademicYearId()))
                .map(Enrolment::getStudentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AttendanceRecord> classRecords(UUID classId) {
        return em.createQuery(
                        "select a from AttendanceRecord a where a.classId = :classId",
                        AttendanceRecord.class)
                .setParameter("classId", classId)
                .getResultList();
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Map<String, Object> attendanceSummary(List<AttendanceRecord> records) {
        int present = 0;
        int absent = 0;
        int late = 0;
        int excused = 0;
        for (AttendanceRecord record : records) {
            String status = record.getStatus() == null ? "" : record.getStatus().name();
            switch (status) {
                case "PRESENT" -> present++;
                case "ABSENT" -> absent++;
                case "LATE" -> late++;
                case "EXCUSED" -> excused++;
                default -> {
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", records.size());
        result.put("present", present);
        result.put("absent", absent);
        result.put("late", late);
        result.put("excused", excused);
        result.put("rate", records.isEmpty() ? 0
                : Math.round((present * 100.0 / records.size()) * 10.0) / 10.0);
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
