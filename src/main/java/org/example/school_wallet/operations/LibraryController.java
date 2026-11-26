package org.example.school_wallet.operations;

import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {

    private final LibraryService service;
    private final ResourceAccessPolicy policy;

    public LibraryController(LibraryService service, ResourceAccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping("/loans")
    public BookLoan checkout(@RequestBody Map<String, Object> body) {
        policy.require("book-loans", ResourceAccessPolicy.Action.CREATE);
        return service.checkout(body);
    }

    @PostMapping("/loans/{id}/return")
    public BookLoan returnLoan(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        policy.require("book-loans", ResourceAccessPolicy.Action.UPDATE);
        return service.returnLoan(id, body == null ? Map.of() : body);
    }

    @PostMapping("/loans/{id}/renew")
    public BookLoan renew(@PathVariable UUID id) {
        policy.require("book-loans", ResourceAccessPolicy.Action.UPDATE);
        return service.renew(id);
    }

    @PostMapping("/reservations")
    public BookReservation reserve(@RequestBody Map<String, Object> body) {
        policy.require("book-reservations", ResourceAccessPolicy.Action.CREATE);
        return service.reserve(body);
    }

    @PostMapping("/lab-sessions")
    public LabSession bookLab(@RequestBody Map<String, Object> body) {
        policy.require("lab-sessions", ResourceAccessPolicy.Action.CREATE);
        return service.bookLab(body);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        policy.require("books", ResourceAccessPolicy.Action.READ);
        return service.summary();
    }
}
