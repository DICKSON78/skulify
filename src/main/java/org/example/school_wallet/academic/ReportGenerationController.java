package org.example.school_wallet.academic;

import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportGenerationController {

    private final ReportService service;
    private final ResourceAccessPolicy accessPolicy;

    public ReportGenerationController(ReportService service, ResourceAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/{id}/generate")
    public ResponseEntity<byte[]> generate(@PathVariable UUID id) {
        accessPolicy.require("reports", Action.READ);
        ReportService.GeneratedReport report = service.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.filename() + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(report.content());
    }
}
