package org.example.school_wallet.academic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SemesterAttendanceTest {

    @Test
    void weekCompletionUsesTheWholeClassRosterAndTracksTheFocusedChild() {
        UUID classId = UUID.randomUUID();
        UUID studentOne = UUID.randomUUID();
        UUID studentTwo = UUID.randomUUID();
        Term term = term();
        List<AttendanceRecord> records = List.of(
                record(classId, studentOne, "2026-01-01", AttendanceRecord.Status.PRESENT),
                record(classId, studentTwo, "2026-01-01", AttendanceRecord.Status.ABSENT),
                record(classId, studentOne, "2026-01-08", AttendanceRecord.Status.PRESENT));

        List<Map<String, Object>> weeks = new java.util.ArrayList<>();
        for (int number = 1; number <= SemesterAttendance.weekCount(term); number++) {
            weeks.add(SemesterAttendance.weekSummary(
                    term, number, Set.of(studentOne, studentTwo), records, studentOne));
        }

        assertEquals(1L, SemesterAttendance.completedWeeks(weeks));
        assertEquals(16, SemesterAttendance.publicWeeks(weeks).size());
        assertFalse(SemesterAttendance.publicWeeks(weeks).get(0).containsKey("statuses"));
        assertEquals("PRESENT", weeks.get(0).get("status"));
        assertEquals("PRESENT", weeks.get(1).get("status"));

        Map<String, Object> child = SemesterAttendance.studentSummary(weeks);
        assertEquals(2, child.get("marked"));
        assertEquals(2, child.get("present"));
        assertEquals(100.0, child.get("rate"));
    }

    @Test
    void semesterProgressIsCompletedWeeksOverConfiguredWeeks() {
        UUID student = UUID.randomUUID();
        Term term = term();
        assertEquals(16, SemesterAttendance.weekCount(term));
        assertEquals("2026-01-01", SemesterAttendance.weekStartDate(term, 1));
        assertEquals("2026-01-07", SemesterAttendance.weekEndDate(term, 1));
        assertEquals("2026-01-08", SemesterAttendance.weekStartDate(term, 2));

        List<Map<String, Object>> weeks = new java.util.ArrayList<>();
        for (int number = 1; number <= 16; number++) {
            weeks.add(SemesterAttendance.weekSummary(
                    term, number, Set.of(student), List.of(), student));
        }

        assertEquals(0L, SemesterAttendance.completedWeeks(weeks));
        assertEquals(0.0, SemesterAttendance.round(
                SemesterAttendance.completedWeeks(weeks) * 100.0 / 16));
    }

    private Term term() {
        Term term = new Term();
        term.setName("Term 1");
        term.setTermOrder(1);
        term.setStartDate("2026-01-01");
        term.setEndDate("2026-04-30");
        term.setNumberOfWeeks(16);
        term.setStatus(Term.Status.ACTIVE);
        return term;
    }

    private AttendanceRecord record(
            UUID classId, UUID studentId, String date, AttendanceRecord.Status status) {
        AttendanceRecord record = new AttendanceRecord();
        record.setClassId(classId);
        record.setStudentId(studentId);
        record.setAttendanceDate(date);
        record.setStatus(status);
        return record;
    }
}
