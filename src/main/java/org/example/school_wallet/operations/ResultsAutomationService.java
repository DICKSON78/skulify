package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.AttendanceRecord;
import org.example.school_wallet.academic.Enrolment;
import org.example.school_wallet.academic.FeeRecord;
import org.example.school_wallet.academic.GradeRecord;
import org.example.school_wallet.academic.SchoolClass;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.Subject;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.time.TimeValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns a filled marks spreadsheet into graded students, class rankings and
 * parent letters without any extra clicks from the teacher.
 */
@Service
@Transactional
public class ResultsAutomationService {

    private static final double WEAK_THRESHOLD = 60.0;
    private static final double LOW_ATTENDANCE = 85.0;

    private final EntityManager em;
    private final AcademicAutomationService automation;
    private final OperationsSupport support;
    private final ParentLetterRepository letters;

    public ResultsAutomationService(
            EntityManager em,
            AcademicAutomationService automation,
            OperationsSupport support,
            ParentLetterRepository letters) {
        this.em = em;
        this.automation = automation;
        this.support = support;
        this.letters = letters;
    }

    /** Excel-compatible UTF-8 CSV template prefilled with the class register. */
    public String marksTemplate(UUID classId, String examType, String term) {
        SchoolClass schoolClass = classId == null ? null : em.find(SchoolClass.class, classId);
        if (schoolClass == null) {
            throw Errors.badRequest("CLASS_REQUIRED", "Choose an existing class");
        }
        StringBuilder csv = new StringBuilder("\uFEFFstudentCode,studentName,score,remarks\n");
        for (Enrolment enrolment : activeEnrolments(classId)) {
            Student student = em.find(Student.class, enrolment.getStudentId());
            if (student == null) {
                continue;
            }
            csv.append(csv(student.getStudentCode()))
                    .append(',').append(csv(name(student)))
                    .append(",,").append(csv(examType == null ? "" : examType))
                    .append('\n');
        }
        return csv.toString();
    }

    /** Imports the filled template, grades it, ranks the class and writes letters. */
    public Map<String, Object> importMarks(
            MultipartFile file,
            UUID classId,
            UUID subjectId,
            String examType,
            String term,
            String maxScore,
            boolean notify,
            boolean autoResults) {

        if (file == null || file.isEmpty()) {
            throw Errors.badRequest("FILE_REQUIRED", "Upload the filled marks template");
        }
        List<CsvRow> rows = parse(file);
        if (rows.isEmpty()) {
            throw Errors.badRequest("NO_MARKS", "The template does not contain any marks");
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (CsvRow row : rows) {
            Student student = em.createQuery(
                            "select s from Student s where s.studentCode = :code", Student.class)
                    .setParameter("code", row.studentCode())
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> Errors.badRequest("UNKNOWN_STUDENT",
                            "Unknown registration number: " + row.studentCode()));
            boolean enrolled = activeEnrolments(classId).stream()
                    .anyMatch(enrolment -> enrolment.getStudentId().equals(student.getId()));
            if (!enrolled) {
                throw Errors.badRequest("NOT_ENROLLED",
                        row.studentCode() + " is not enrolled in this class");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("studentId", student.getId());
            entry.put("score", row.score());
            entry.put("remarks", row.remarks());
            entries.add(entry);
        }

        Map<String, Object> upload = new LinkedHashMap<>();
        upload.put("classId", classId);
        upload.put("subjectId", subjectId);
        upload.put("examType", examType);
        upload.put("term", term);
        upload.put("maxScore", maxScore);
        upload.put("notify", String.valueOf(notify));
        upload.put("entries", entries);
        List<GradeRecord> grades = automation.uploadMarks(upload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grades", grades.size());
        result.put("ranked", 0);
        result.put("letters", 0);
        result.put("students", List.of());
        if (!autoResults) {
            return result;
        }

        Exam exam = findOrCreateExam(classId, examType, term);
        result.put("examId", exam.getId());
        List<ReportCard> cards = automation.generateReportCards(exam.getId(), classId);
        cards.forEach(card -> automation.publishReportCard(card.getId()));
        List<ParentLetter> generated = generateParentLetters(exam.getId(), classId, cards);

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (ReportCard card : cards) {
            Student student = em.find(Student.class, card.getStudentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", card.getPosition());
            row.put("name", student == null ? "" : name(student));
            row.put("registrationNo", student == null ? "" : student.getStudentCode());
            row.put("average", card.getAverage());
            row.put("grade", card.getGrade());
            ranking.add(row);
        }
        ranking.sort(Comparator.comparingInt(row -> (Integer) row.get("position")));
        result.put("ranked", cards.size());
        result.put("letters", generated.size());
        result.put("lettersDetail", generated.stream().map(this::letterView).toList());
        result.put("students", ranking);
        result.put("top", ranking.isEmpty() ? null : ranking.get(0));
        result.put("bottom", ranking.isEmpty() ? null : ranking.get(ranking.size() - 1));
        support.audit("MARKS_IMPORTED", "GRADES", null, Map.of(
                "classId", String.valueOf(classId),
                "grades", grades.size(),
                "cards", cards.size(),
                "letters", generated.size()));
        return result;
    }

    /** Regenerates letters for every result card already published for an exam. */
    public List<ParentLetter> generateLetters(UUID examId, UUID classId) {
        String query = "select r from ReportCard r where r.examId = :examId";
        if (classId != null) {
            query += " and r.classId = :classId";
        }
        var typed = em.createQuery(query, ReportCard.class).setParameter("examId", examId);
        if (classId != null) {
            typed.setParameter("classId", classId);
        }
        return generateParentLetters(examId, classId, typed.getResultList());
    }

    /** Builds or refreshes one letter per published report card. */
    public List<ParentLetter> generateParentLetters(UUID examId, UUID classId, List<ReportCard> cards) {
        Exam exam = em.find(Exam.class, examId);
        String examType = exam == null ? "EXAM" : exam.getExamType();
        String term = exam == null ? "TERM" : exam.getTerm();
        String className = classId == null ? "" : classNames().getOrDefault(classId, "");
        Map<UUID, String> subjects = subjectNames();
        List<ParentLetter> saved = new ArrayList<>();

        for (ReportCard card : cards) {
            Student student = em.find(Student.class, card.getStudentId());
            if (student == null) {
                continue;
            }
            List<GradeRecord> grades = em.createQuery(
                            """
                            select g from GradeRecord g
                            where g.studentId = :studentId and g.examType = :examType
                              and (:term is null or g.term = :term)
                            """,
                            GradeRecord.class)
                    .setParameter("studentId", student.getId())
                    .setParameter("examType", examType)
                    .setParameter("term", term)
                    .getResultList();
            Map<String, Object> performance = performance(grades, subjects);
            double attendanceRate = attendanceRate(student.getId());
            BigDecimal outstanding = outstanding(student.getId());

            @SuppressWarnings("unchecked")
            List<String> weak = (List<String>) performance.get("weakSubjects");
            String strongest = String.valueOf(performance.getOrDefault("strongestSubject", "N/A"));
            List<String> requirements = new ArrayList<>();
            if (!weak.isEmpty()) {
                requirements.add("Remedial support and extra practice in: " + String.join(", ", weak));
            }
            if (attendanceRate < LOW_ATTENDANCE) {
                requirements.add("Improve attendance (current rate " + attendanceRate + "%); "
                        + "a guardian meeting is requested.");
            }
            if (card.getPosition() != null && card.getClassSize() != null
                    && card.getClassSize() > 3 && card.getPosition() > card.getClassSize() - 3) {
                requirements.add("Attend a parent-teacher consultation about next-term learning support.");
            }
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                requirements.add("Settle outstanding fee balance of " + outstanding + " before the next term.");
            }
            String focus = weak.isEmpty()
                    ? strongest : strongest + " and " + String.join(", ", weak);
            requirements.add("Report to class " + className + " on the first day of the next semester "
                    + "with books and materials for " + focus + ".");

            String letter = "Dear " + (student.getGuardianName() == null
                            ? "Guardian" : student.getGuardianName()) + ",\n\n"
                    + "Re: " + (exam == null ? "Term results" : exam.getName()) + " for "
                    + name(student) + " (" + student.getStudentCode() + ").\n\n"
                    + "We are pleased to share the end-of-term report. Your child obtained an average of "
                    + card.getAverage() + " (grade " + card.getGrade() + ") and ranked "
                    + card.getPosition() + " out of " + card.getClassSize() + " in " + className + ".\n\n"
                    + "Strongest subject: " + strongest + ".\n"
                    + "Subjects needing attention: " + (weak.isEmpty() ? "none" : String.join(", ", weak)) + ".\n"
                    + "Attendance: " + attendanceRate + "%.\n"
                    + "Outstanding fees: " + outstanding + ".\n\n"
                    + "NEXT-SEMESTER REQUIREMENTS\n"
                    + String.join("\n", requirements.stream().map(item -> "- " + item).toList())
                    + "\n\nYours sincerely,\nSchool Administration";

            ParentLetter record = letters.findByReportCardId(card.getId()).orElse(null);
            boolean created = record == null;
            if (created) {
                record = new ParentLetter();
                record.setReference(reference("PL"));
                record.setReportCardId(card.getId());
                record.setStudentId(student.getId());
            }
            record.setClassId(classId);
            record.setTerm(term);
            record.setTitle((exam == null ? "Term" : exam.getName()) + " parent letter");
            record.setContent(letter);
            record.setRequirements(String.join("\n", requirements));
            record.setStatus("GENERATED");
            record.setGeneratedAt(TimeValues.now());
            if (created) {
                em.persist(record);
            } else {
                em.merge(record);
            }
            saved.add(record);
            support.notifyGuardian(student, "Parent letter ready",
                    record.getTitle() + " is now available in the parent portal.",
                    "parent-letters", record.getId());
        }
        em.flush();
        return saved;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Compact per-student letter summary for the teacher's upload result screen. */
    private Map<String, Object> letterView(ParentLetter letter) {
        Student student = em.find(Student.class, letter.getStudentId());
        String content = letter.getContent() == null ? "" : letter.getContent();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reference", letter.getReference());
        row.put("studentId", letter.getStudentId());
        row.put("name", student == null ? "" : name(student));
        row.put("registrationNo", student == null ? "" : student.getStudentCode());
        row.put("strongest", line(content, "Strongest subject: "));
        row.put("weak", line(content, "Subjects needing attention: "));
        row.put("requirements", letter.getRequirements());
        return row;
    }

    private String line(String content, String prefix) {
        int start = content.indexOf(prefix);
        if (start < 0) {
            return "N/A";
        }
        int from = start + prefix.length();
        int end = content.indexOf('\n', from);
        String value = end < 0 ? content.substring(from) : content.substring(from, end);
        return value.isBlank() ? "N/A" : value;
    }

    private List<Enrolment> activeEnrolments(UUID classId) {
        return em.createQuery(
                        "select e from Enrolment e where e.classId = :classId and e.status = 'ACTIVE'",
                        Enrolment.class)
                .setParameter("classId", classId)
                .getResultList();
    }

    private Map<String, Object> performance(List<GradeRecord> grades, Map<UUID, String> subjects) {
        List<String> weak = new ArrayList<>();
        String strongest = "N/A";
        double best = -1;
        for (GradeRecord grade : grades) {
            double score = grade.getScore() == null ? 0 : grade.getScore();
            double max = grade.getMaxScore() == null || grade.getMaxScore() == 0
                    ? 100 : grade.getMaxScore();
            double percentage = score * 100 / max;
            String subject = subjects.getOrDefault(grade.getSubjectId(), "Subject");
            if (percentage < WEAK_THRESHOLD) {
                weak.add(subject + " (" + Math.round(percentage) + "%)");
            }
            if (percentage > best) {
                best = percentage;
                strongest = subject;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weakSubjects", weak);
        result.put("strongestSubject", strongest);
        return result;
    }

    private double attendanceRate(UUID studentId) {
        List<AttendanceRecord> records = em.createQuery(
                        "select a from AttendanceRecord a where a.studentId = :studentId",
                        AttendanceRecord.class)
                .setParameter("studentId", studentId)
                .getResultList();
        if (records.isEmpty()) {
            return 100;
        }
        long present = records.stream()
                .filter(record -> record.getStatus() != null
                        && "PRESENT".equals(record.getStatus().name()))
                .count();
        return Math.round(present * 1000.0 / records.size()) / 10.0;
    }

    private BigDecimal outstanding(UUID studentId) {
        List<FeeRecord> fees = em.createQuery(
                        "select f from FeeRecord f where f.studentId = :studentId", FeeRecord.class)
                .setParameter("studentId", studentId)
                .getResultList();
        BigDecimal total = BigDecimal.ZERO;
        for (FeeRecord fee : fees) {
            if ("CANCELLED".equals(fee.getStatus())) {
                continue;
            }
            BigDecimal amount = fee.getAmount() == null ? BigDecimal.ZERO : fee.getAmount();
            BigDecimal paid = fee.getPaidAmount() == null ? BigDecimal.ZERO : fee.getPaidAmount();
            total = total.add(amount.subtract(paid));
        }
        return total.setScale(2);
    }

    private Exam findOrCreateExam(UUID classId, String examType, String term) {
        String code = "AUTO-" + UUID.nameUUIDFromBytes(
                        (classId + "|" + examType + "|" + term).getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 12);
        Exam exam = em.createQuery("select e from Exam e where e.code = :code", Exam.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (exam == null) {
            SchoolClass schoolClass = em.find(SchoolClass.class, classId);
            exam = new Exam();
            exam.setCode(code);
            exam.setName((examType == null ? "Assessment" : examType) + " - "
                    + (schoolClass == null ? "Class" : schoolClass.getName()));
            exam.setExamType(examType == null || examType.isBlank() ? "TEST" : examType);
            exam.setTerm(term);
            exam.setStartDate(TimeValues.now().substring(0, 10));
            exam.setEndDate(TimeValues.now().substring(0, 10));
            exam.setStatus("COMPLETED");
            exam.setRemarks("Generated automatically from an uploaded marks template");
            em.persist(exam);
            em.flush();
        }
        return exam;
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

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String name(Student student) {
        return (student.getFirstName() + " " + student.getLastName()).trim();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? "\"" + escaped + "\"" : escaped;
    }

    private List<CsvRow> parse(MultipartFile file) {
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw Errors.badRequest("UNREADABLE_FILE", "The uploaded file could not be read");
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        List<CsvRow> rows = new ArrayList<>();
        boolean header = true;
        for (String line : content.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(line);
            if (header && cells.get(0).equalsIgnoreCase("studentCode")) {
                header = false;
                continue;
            }
            header = false;
            if (cells.size() < 3 || cells.get(0).isBlank() || cells.get(2).isBlank()) {
                continue;
            }
            String studentCode = cells.get(0).trim();
            String score = cells.get(2).trim();
            try {
                Double.parseDouble(score);
            } catch (NumberFormatException ex) {
                throw Errors.badRequest("INVALID_SCORE", "Invalid score for " + studentCode);
            }
            rows.add(new CsvRow(studentCode, score, cells.size() > 3 ? cells.get(3).trim() : ""));
        }
        return rows;
    }

    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private record CsvRow(String studentCode, String score, String remarks) {
    }
}
