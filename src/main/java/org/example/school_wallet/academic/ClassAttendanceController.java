package org.example.school_wallet.academic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.error.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Class-teacher attendance register with week-based progress. */
@RestController
@RequestMapping("/api/v1/class-attendance")
public class ClassAttendanceController {

    private final ClassAttendanceService service;

    public ClassAttendanceController(ClassAttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> overview(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID termId,
            @RequestParam(required = false) Integer week) {
        return service.overview(classId, termId, week);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        UUID classId = uuid(body.get("classId"));
        UUID termId = uuid(body.get("termId"));
        Integer week = parseWeek(body.get("week"));
        Object rawEntries = body.get("entries");
        if (!(rawEntries instanceof List<?> list)) {
            throw Errors.badRequest("INVALID_ENTRIES", "Attendance entries are required");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) list;
        return service.save(classId, termId, week, entries);
    }

    private Integer parseWeek(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw Errors.badRequest("INVALID_WEEK", "Week must be a number");
        }
    }

    private UUID uuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw Errors.badRequest("INVALID_ID", "A valid class and term are required");
        }
    }
}
