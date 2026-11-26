package org.example.school_wallet.operations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.GradeRecord;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Domain actions for timetables, marks, duties, exams and results. */
@RestController
@RequestMapping("/api/v1/academics")
public class AcademicAutomationController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AcademicAutomationService service;
    private final ResultsAutomationService results;
    private final ResourceAccessPolicy policy;

    public AcademicAutomationController(
            AcademicAutomationService service,
            ResultsAutomationService results,
            ResourceAccessPolicy policy) {
        this.service = service;
        this.results = results;
        this.policy = policy;
    }

    @PostMapping("/timetable/generate")
    public Map<String, Object> generateTimetable(@RequestBody Map<String, Object> body) {
        policy.require("timetable", Action.CREATE);
        return service.generateTimetable(body);
    }

    @PostMapping("/marks/bulk")
    public List<GradeRecord> uploadMarks(@RequestBody Map<String, Object> body) {
        policy.require("marks-upload", Action.CREATE);
        return service.uploadMarks(body);
    }

    /** Excel-compatible template prefilled with the class register. */
    @GetMapping(value = "/marks/template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> marksTemplate(
            @RequestParam UUID classId,
            @RequestParam(required = false, defaultValue = "TEST") String examType,
            @RequestParam(required = false, defaultValue = "TERM 1") String term) {
        policy.require("marks-upload", Action.CREATE);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"marks-template.csv\"")
                .contentType(CSV)
                .body(results.marksTemplate(classId, examType, term));
    }

    /** Upload the filled template: marks, ranking, report cards and letters in one pass. */
    @PostMapping(value = "/marks/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importMarks(
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID classId,
            @RequestParam UUID subjectId,
            @RequestParam(required = false, defaultValue = "TEST") String examType,
            @RequestParam(required = false, defaultValue = "TERM 1") String term,
            @RequestParam(required = false, defaultValue = "100") String maxScore,
            @RequestParam(required = false, defaultValue = "true") boolean notify,
            @RequestParam(required = false, defaultValue = "true") boolean autoResults) {
        policy.require("marks-upload", Action.CREATE);
        return results.importMarks(
                file, classId, subjectId, examType, term, maxScore, notify, autoResults);
    }

    @PostMapping("/duty/generate")
    public Map<String, Object> generateDuty(@RequestBody Map<String, Object> body) {
        policy.require("duty-assignments", Action.CREATE);
        return service.generateDutyRoster(body);
    }

    @PostMapping("/duty/{id}/cancel")
    public DutyAssignment cancelDuty(@PathVariable UUID id) {
        policy.require("duty-assignments", Action.UPDATE);
        return service.cancelDuty(id);
    }

    @PostMapping("/leaves/{id}/decision")
    public StaffLeave decideLeave(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("staff-leaves", Action.UPDATE);
        return service.decideLeave(id, body);
    }

    @PostMapping("/exams/{id}/schedule")
    public List<ExamSession> schedule(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        policy.require("exam-sessions", Action.CREATE);
        return service.generateExamSchedule(id, body);
    }

    @PostMapping("/results/generate")
    public List<ReportCard> generateResults(@RequestBody Map<String, Object> body) {
        policy.require("report-cards", Action.CREATE);
        return service.generateReportCards(
                UUID.fromString(String.valueOf(body.get("examId"))),
                body.get("classId") == null ? null : UUID.fromString(String.valueOf(body.get("classId"))));
    }

    @PostMapping("/results/{id}/publish")
    public ReportCard publish(@PathVariable UUID id) {
        policy.require("report-cards", Action.UPDATE);
        return service.publishReportCard(id);
    }

    /** Regenerates parent letters from already published report cards. */
    @PostMapping("/results/letters/generate")
    public List<ParentLetter> generateLetters(@RequestBody Map<String, Object> body) {
        policy.require("results-generate", Action.CREATE);
        return results.generateLetters(
                UUID.fromString(String.valueOf(body.get("examId"))),
                body.get("classId") == null ? null : UUID.fromString(String.valueOf(body.get("classId"))));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        policy.require("exams", Action.READ);
        return service.summary();
    }

    @PostMapping("/automation/daily")
    public Map<String, Object> runDaily() {
        policy.require("academic-automation", Action.CREATE);
        return service.runDaily();
    }
}
