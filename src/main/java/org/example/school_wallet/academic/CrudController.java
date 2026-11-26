package org.example.school_wallet.academic;

import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.common.api.PageResponse;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for every school module:
 * /api/v1/students, /teachers, /classes, /subjects, /attendance,
 * /grades, /fees, /timetable, /reports and /settings.
 */
@RestController
@RequestMapping("/api/v1")
public class CrudController {

    private final CrudService service;
    private final ResourceAccessPolicy accessPolicy;

    public CrudController(CrudService service, ResourceAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/{resource}")
    public PageResponse<Map<String, Object>> list(
            @PathVariable String resource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {
        accessPolicy.require(resource, Action.READ);
        return service.list(resource, page, size, q);
    }

    @GetMapping("/{resource}/{id}")
    public Map<String, Object> get(@PathVariable String resource, @PathVariable UUID id) {
        accessPolicy.require(resource, Action.READ);
        return service.get(resource, id);
    }

    @PostMapping("/{resource}")
    public Map<String, Object> create(@PathVariable String resource, @RequestBody Map<String, Object> body) {
        accessPolicy.require(resource, Action.CREATE);
        return service.create(resource, body);
    }

    @PutMapping("/{resource}/{id}")
    public Map<String, Object> update(
            @PathVariable String resource, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        accessPolicy.require(resource, Action.UPDATE);
        return service.update(resource, id, body);
    }

    @DeleteMapping("/{resource}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable UUID id) {
        accessPolicy.require(resource, Action.DELETE);
        service.delete(resource, id);
        return ResponseEntity.noContent().build();
    }
}
