package org.example.school_wallet.academic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class AcademicWorkflowController {

    private final AcademicWorkflowService service;
    private final ResourceAccessPolicy accessPolicy;

    public AcademicWorkflowController(
            AcademicWorkflowService service, ResourceAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping("/attendance/bulk")
    public List<AttendanceRecord> markAttendance(
            @RequestParam UUID classId,
            @RequestParam String date,
            @RequestBody List<Map<String, Object>> entries) {
        accessPolicy.require("attendance", Action.UPDATE);
        return service.markAttendance(classId, date, entries);
    }

    @PostMapping("/enrolments")
    public Enrolment enrol(@RequestBody Map<String, Object> body) {
        accessPolicy.require("enrolments", Action.CREATE);
        return service.enrol(body);
    }

    @PostMapping("/teacher-assignments")
    public TeacherAssignment assign(@RequestBody Map<String, Object> body) {
        accessPolicy.require("teacher-assignments", Action.CREATE);
        return service.assignTeacher(body);
    }
}
