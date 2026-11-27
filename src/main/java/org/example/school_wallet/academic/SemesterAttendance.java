package org.example.school_wallet.academic;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.school_wallet.common.error.Errors;

/**
 * Shared week rules for the class register and the guardian view.
 *
 * <p>A week is complete only when every actively enrolled student in the class
 * has a status for that week. Semester progress is completed weeks divided by
 * the term's configured week count.
 */
public final class SemesterAttendance {

    private SemesterAttendance() {
    }

    public static int weekCount(Term term) {
        return Math.max(1, term.getNumberOfWeeks());
    }

    public static String weekStartDate(Term term, int week) {
        return parseDate(term.getStartDate()).plusWeeks(week - 1L).toString();
    }

    public static String weekEndDate(Term term, int week) {
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

    public static Map<String, Object> weekSummary(
            Term term,
            int number,
            Set<UUID> rosterIds,
            List<AttendanceRecord> classRecords,
            UUID focusStudentId) {
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
        byStudent.forEach((studentId, row) -> statuses.put(
                studentId.toString(),
                row.getStatus() == null ? "" : row.getStatus().name()));

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
        summary.put("status", focusStudentId == null ? "" : statuses.getOrDefault(focusStudentId.toString(), ""));
        summary.put("statuses", statuses);
        return summary;
    }

    public static long completedWeeks(List<Map<String, Object>> weeks) {
        return weeks.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("complete")))
                .count();
    }

    /** Attendance record for one child across the semester weeks. */
    public static Map<String, Object> studentSummary(List<Map<String, Object>> weeks) {
        int marked = 0;
        int present = 0;
        int absent = 0;
        int excused = 0;
        int late = 0;
        for (Map<String, Object> week : weeks) {
            String status = text(week.get("status"));
            if (status.isEmpty()) {
                continue;
            }
            marked++;
            switch (status) {
                case "PRESENT" -> present++;
                case "ABSENT" -> absent++;
                case "EXCUSED" -> excused++;
                case "LATE" -> late++;
                default -> {
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("marked", marked);
        summary.put("present", present);
        summary.put("absent", absent);
        summary.put("excused", excused);
        summary.put("late", late);
        summary.put("rate", marked == 0 ? 0 : round(present * 100.0 / marked));
        return summary;
    }

    /** Public week rows keep the child's status but hide the full class status map. */
    public static List<Map<String, Object>> publicWeeks(List<Map<String, Object>> weeks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> week : weeks) {
            Map<String, Object> copy = new LinkedHashMap<>(week);
            copy.remove("statuses");
            result.add(copy);
        }
        return result;
    }

    public static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw Errors.badRequest("INVALID_TERM_DATE", "Term needs a valid start date");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
