package org.example.school_wallet.testing;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Teacher-facing online test preparation and attendance reporting. */
@RestController
@RequestMapping("/api/v1/tests")
public class OnlineTestController {

    private final OnlineTestService service;

    public OnlineTestController(OnlineTestService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @GetMapping
    public java.util.List<Map<String, Object>> list() {
        return service.list();
    }

    @GetMapping("/{id}/link")
    public Map<String, Object> link(@PathVariable UUID id) {
        return Map.of("link", service.link(id));
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return service.setStatus(id, String.valueOf(body.get("status")));
    }

    @GetMapping("/{id}/results")
    public Map<String, Object> results(@PathVariable UUID id) {
        return service.results(id);
    }

    /** Convenience for the UI: publish straight from the test card. */
    @PostMapping("/{id}/publish")
    public Map<String, Object> publish(@PathVariable UUID id) {
        return service.setStatus(id, "PUBLISHED");
    }

    @PostMapping("/{id}/close")
    public Map<String, Object> close(@PathVariable UUID id) {
        return service.setStatus(id, "CLOSED");
    }

    /** Kept so a teacher can refresh a link without changing the test. */
    @GetMapping(value = "/{id}/code", produces = "text/plain")
    public String code(@PathVariable UUID id, @RequestParam(required = false) String ignored) {
        return service.link(id);
    }
}
