package org.example.school_wallet.academic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.time.TimeValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates downloadable CSV reports from live module data. */
@Service
@Transactional
public class ReportService {

    private final ReportDefinitionRepository reports;
    private final AttendanceRecordRepository attendance;
    private final FeeRecordRepository fees;
    private final StudentRepository students;
    private final GradeRecordRepository grades;

    public ReportService(
            ReportDefinitionRepository reports,
            AttendanceRecordRepository attendance,
            FeeRecordRepository fees,
            StudentRepository students,
            GradeRecordRepository grades) {
        this.reports = reports;
        this.attendance = attendance;
        this.fees = fees;
        this.students = students;
        this.grades = grades;
    }

    public GeneratedReport generate(UUID reportId) {
        ReportDefinition definition = reports.findById(reportId)
                .orElseThrow(() -> Errors.notFound("REPORT_NOT_FOUND", "Report definition not found"));

        String csv = switch (definition.getReportType().toUpperCase()) {
            case "ATTENDANCE" -> attendanceCsv();
            case "FINANCE", "FEES" -> feeCsv();
            case "GRADES", "EXAMS" -> gradeCsv();
            default -> studentCsv();
        };

        definition.setLastGenerated(TimeValues.now());
        reports.save(definition);
        String safeName = definition.getName().replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
        return new GeneratedReport(safeName + ".csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private String attendanceCsv() {
        StringBuilder out = new StringBuilder("Student,Date,Status,Check in,Remarks\n");
        for (AttendanceRecord row : attendance.findAll()) {
            out.append(row.getStudentId()).append(',')
                    .append(escape(row.getAttendanceDate())).append(',')
                    .append(row.getStatus()).append(',')
                    .append(escape(row.getCheckIn())).append(',')
                    .append(escape(row.getRemarks())).append('\n');
        }
        return out.toString();
    }

    private String feeCsv() {
        StringBuilder out = new StringBuilder("Reference,Student,Fee type,Amount,Paid,Balance,Status,Due date\n");
        for (FeeRecord row : fees.findAll()) {
            java.math.BigDecimal balance = row.getAmount() == null
                    ? java.math.BigDecimal.ZERO
                    : row.getAmount().subtract(row.getPaidAmount() == null ? java.math.BigDecimal.ZERO : row.getPaidAmount());
            out.append(escape(row.getReference())).append(',')
                    .append(row.getStudentId()).append(',')
                    .append(escape(row.getFeeType())).append(',')
                    .append(row.getAmount()).append(',')
                    .append(row.getPaidAmount()).append(',')
                    .append(balance).append(',')
                    .append(row.getStatus()).append(',')
                    .append(escape(row.getDueDate())).append('\n');
        }
        return out.toString();
    }

    private String gradeCsv() {
        StringBuilder out = new StringBuilder("Student,Subject,Exam,Term,Score,Max,Grade\n");
        for (GradeRecord row : grades.findAll()) {
            out.append(row.getStudentId()).append(',')
                    .append(row.getSubjectId()).append(',')
                    .append(escape(row.getExamType())).append(',')
                    .append(escape(row.getTerm())).append(',')
                    .append(row.getScore()).append(',')
                    .append(row.getMaxScore()).append(',')
                    .append(escape(row.getGrade())).append('\n');
        }
        return out.toString();
    }

    private String studentCsv() {
        StringBuilder out = new StringBuilder("Code,First name,Last name,Class,Guardian,Status\n");
        for (Student row : students.findAll()) {
            out.append(escape(row.getStudentCode())).append(',')
                    .append(escape(row.getFirstName())).append(',')
                    .append(escape(row.getLastName())).append(',')
                    .append(escape(row.getClassName())).append(',')
                    .append(escape(row.getGuardianName())).append(',')
                    .append(row.getStatus()).append('\n');
        }
        return out.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record GeneratedReport(String filename, byte[] content) {
    }
}
