package org.example.school_wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.security.ResourceAccessPolicy.Action;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ResourceAccessPolicyTest {

    private final ResourceAccessPolicy policy = new ResourceAccessPolicy();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherCanWriteLearningRecordsButNotFees() {
        authenticate(Role.TEACHER);

        assertTrue(policy.can("attendance", Action.UPDATE));
        assertTrue(policy.can("grades", Action.CREATE));
        assertTrue(policy.can("students", Action.READ));
        assertFalse(policy.can("fees", Action.READ));
        assertFalse(policy.can("settings", Action.UPDATE));
    }

    @Test
    void parentAndStudentAreReadOnlyAndScoped() {
        authenticate(Role.PARENT);
        assertTrue(policy.can("students", Action.READ));
        assertFalse(policy.can("students", Action.UPDATE));
        assertTrue(policy.isScoped(CurrentUser.require()));

        authenticate(Role.STUDENT);
        assertTrue(policy.can("grades", Action.READ));
        assertFalse(policy.can("grades", Action.DELETE));
    }

    @Test
    void accountantManagesFeesOnly() {
        authenticate(Role.ACCOUNTANT);

        assertTrue(policy.can("fees", Action.DELETE));
        assertTrue(policy.can("payments", Action.CREATE));
        assertTrue(policy.can("students", Action.READ));
        assertFalse(policy.can("grades", Action.READ));
    }

    @Test
    void adminCanManageEverything() {
        authenticate(Role.TENANT_ADMIN);

        assertTrue(policy.can("settings", Action.DELETE));
        assertTrue(policy.can("teacher-assignments", Action.CREATE));
        assertTrue(policy.can("reports", Action.READ));
        assertTrue(policy.can("store-pos", Action.CREATE));
        assertTrue(policy.can("lab-sessions", Action.DELETE));
    }

    @Test
    void librarianOwnsLibraryAndLabButNotFees() {
        authenticate(Role.LIBRARIAN);

        assertTrue(policy.can("books", Action.CREATE));
        assertTrue(policy.can("book-loans", Action.UPDATE));
        assertTrue(policy.can("lab-sessions", Action.DELETE));
        assertTrue(policy.can("students", Action.READ));
        assertFalse(policy.can("fees", Action.READ));
    }

    @Test
    void storeStaffOwnInventoryAndCashierOwnsPos() {
        authenticate(Role.STORE_KEEPER);
        assertTrue(policy.can("inventory-items", Action.CREATE));
        assertTrue(policy.can("purchase-requests", Action.UPDATE));
        assertTrue(policy.can("store-items", Action.DELETE));
        assertFalse(policy.can("store-pos", Action.CREATE));

        authenticate(Role.CASHIER);
        assertTrue(policy.can("store-pos", Action.CREATE));
        assertTrue(policy.can("pocket-topups", Action.CREATE));
        assertTrue(policy.can("store-items", Action.READ));
        assertFalse(policy.can("inventory-items", Action.DELETE));
    }

    @Test
    void guardianCanTopUpReserveAndBookLabButCannotIssueInvoices() {
        authenticate(Role.PARENT);
        assertTrue(policy.can("pocket-topups", Action.CREATE));
        assertTrue(policy.can("spending-limits", Action.UPDATE));
        assertTrue(policy.can("book-reservations", Action.CREATE));
        assertTrue(policy.can("lab-sessions", Action.CREATE));
        assertTrue(policy.can("invoices", Action.READ));
        assertFalse(policy.can("invoices", Action.CREATE));
        assertFalse(policy.can("store-pos", Action.CREATE));
    }

    private void authenticate(Role... roles) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "tester", "Tester", Set.of(roles), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Set.of()));
    }
}
