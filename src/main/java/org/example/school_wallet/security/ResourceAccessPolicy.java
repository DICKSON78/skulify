package org.example.school_wallet.security;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.user.Role;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for module permissions.
 *
 * <p>Roles follow the school hierarchy: administrators manage everything,
 * teachers manage learning records, accountants manage money, librarians and
 * store staff own their operations, and parents / students are data-scoped in
 * {@code CrudService} and the domain services.
 */
@Component
public class ResourceAccessPolicy {

    public enum Action {
        READ,
        CREATE,
        UPDATE,
        DELETE
    }

    private static final Set<String> ACADEMIC = Set.of(
            "students", "teachers", "classes", "subjects", "attendance", "grades", "timetable",
            "periods", "exams", "exam-sessions", "duty-assignments", "report-cards", "school-events");
    private static final Set<String> STRUCTURE = Set.of(
            "academic-years", "terms", "enrolments", "teacher-assignments", "notifications",
            "staff-leaves");
    private static final Set<String> ACADEMIC_PLANNING = Set.of(
            "periods", "exams", "exam-sessions", "duty-assignments", "report-cards",
            "staff-leaves", "school-events");
    private static final Set<String> LEARNING_WRITES = Set.of("attendance", "grades", "lab-sessions");
    /** Pseudo-resources guarded by domain services, never by generic CRUD. */
    private static final Set<String> TEACHER_ACTIONS = Set.of("marks-upload");
    private static final Set<String> ADMIN_ACTIONS = Set.of(
            "timetable-generate", "duty-generate", "exam-schedule", "results-generate",
            "academic-automation", "online-tests", "test-attempts", "parent-letters");
    private static final Set<String> FINANCE = Set.of(
            "fees", "payments", "invoices", "invoice-items", "fee-categories", "fee-plans",
            "pocket-cards", "pocket-transactions", "store-items");
    private static final Set<String> LIBRARY = Set.of(
            "books", "book-copies", "book-loans", "book-reservations", "lab-sessions");
    private static final Set<String> STORE = Set.of(
            "store-items", "store-terminals", "inventory-items", "stock-movements",
            "purchase-requests");
    private static final Set<String> OPERATIONS = Set.of(
            "departments", "staff-attendance", "school-tasks", "notifications");
    private static final Set<String> DOCUMENTS = Set.of("school-documents");
    private static final Set<String> CLASS_MANAGEMENT = Set.of(
            "students", "classes", "attendance", "grades", "timetable", "teacher-assignments");

    /** Records guardians may see. Row scope limits these to their own child. */
    private static final Set<String> GUARDIAN_READ = Set.of(
            "students", "attendance", "grades", "fees", "timetable",
            "periods", "exams", "exam-sessions", "report-cards", "school-events",
            "invoices", "fee-plans", "fee-categories", "pocket-cards", "pocket-transactions",
            "spending-limits", "store-items", "books", "book-copies", "book-loans",
            "book-reservations", "lab-sessions", "parent-letters", "test-attempts");

    /**
     * Guardian writes. These are domain actions (top-up, limits, reservations,
     * lab bookings) and generic CRUD on child-owned records. CrudService stamps
     * studentId for scoped users so a guardian cannot write another child's row.
     */
    private static final Set<String> GUARDIAN_WRITES = Set.of(
            "pocket-topups", "spending-limits", "book-reservations", "lab-sessions");

    public void require(String resource, Action action) {
        if (!can(resource, action)) {
            throw Errors.forbidden(
                    "FORBIDDEN", "Your role cannot " + action.name().toLowerCase() + " " + resource);
        }
    }

    public boolean can(String resource, Action action) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty()) {
            return false;
        }
        AuthenticatedUser user = current.get();

        if (user.roles().contains(Role.SUPER_ADMIN)
                || user.roles().contains(Role.TENANT_ADMIN)) {
            return true;
        }

        if (isAdmin(user)) {
            return adminCan(user, resource, action);
        }

        if (user.roles().contains(Role.ACCOUNTANT)) {
            if (FINANCE.contains(resource)) {
                return true;
            }
            if ("purchase-requests".equals(resource)) {
                return action == Action.UPDATE || action == Action.READ;
            }
            return action == Action.READ
                    && Set.of("students", "reports", "settings", "departments", "school-tasks",
                            "store-terminals", "inventory-items", "stock-movements").contains(resource);
        }

        if (user.roles().contains(Role.LIBRARIAN)) {
            if (LIBRARY.contains(resource)) {
                return true;
            }
            return action == Action.READ
                    && Set.of("students", "teachers", "classes", "subjects", "timetable",
                            "store-items", "departments", "school-tasks").contains(resource);
        }

        if (user.roles().contains(Role.STORE_KEEPER)) {
            if (STORE.contains(resource)) {
                return true;
            }
            if ("school-tasks".equals(resource)) {
                return action == Action.READ || action == Action.UPDATE;
            }
            return action == Action.READ
                    && Set.of("students", "teachers", "classes", "departments",
                            "pocket-transactions", "notifications").contains(resource);
        }

        if (user.roles().contains(Role.CASHIER)) {
            if (Set.of("store-pos", "pocket-topups").contains(resource)) {
                return true;
            }
            if (Set.of("store-items", "store-terminals", "pocket-cards", "pocket-transactions",
                    "invoices", "fees").contains(resource)) {
                return action == Action.READ;
            }
            return action == Action.READ && "school-tasks".equals(resource);
        }

        if (user.roles().contains(Role.HR_MANAGER)) {
            if (OPERATIONS.contains(resource) || DOCUMENTS.contains(resource)
                    || "teachers".equals(resource)) {
                return true;
            }
            return action == Action.READ
                    && Set.of("students", "classes", "subjects", "departments", "school-tasks",
                            "notifications", "reports").contains(resource);
        }

        if (user.roles().contains(Role.DISCIPLINE_MASTER)) {
            if (Set.of("students", "attendance", "school-tasks", "notifications").contains(resource)) {
                return true;
            }
            return action == Action.READ
                    && Set.of("classes", "timetable", "report-cards", "parent-letters",
                            "school-events").contains(resource);
        }

        if (user.roles().contains(Role.ACADEMIC_MASTER)) {
            if (ACADEMIC.contains(resource) || ACADEMIC_PLANNING.contains(resource)
                    || STRUCTURE.contains(resource)) {
                return true;
            }
            return action == Action.READ
                    && Set.of("departments", "school-tasks", "notifications", "library",
                            "books", "book-copies", "book-loans", "book-reservations",
                            "lab-sessions").contains(resource);
        }

        if (isTeacher(user)) {
            // Teachers record their own check-in/check-out, which raises HR/admin alerts.
            if ("staff-attendance".equals(resource)) {
                return action != Action.DELETE;
            }
            // Teachers prepare and run their own tests; attempts and letters are read-only.
            if ("online-tests".equals(resource)) {
                return action != Action.DELETE;
            }
            if (Set.of("test-attempts", "parent-letters").contains(resource)) {
                return action == Action.READ;
            }
            if (action == Action.READ) {
                return ACADEMIC.contains(resource)
                        || STRUCTURE.contains(resource)
                        || Set.of("books", "book-copies", "book-loans", "book-reservations",
                                "store-items", "departments", "school-tasks", "notifications",
                                "staff-leaves")
                                .contains(resource);
            }
            if (user.roles().contains(Role.CLASS_TEACHER) && CLASS_MANAGEMENT.contains(resource)) {
                return true;
            }
            if (LEARNING_WRITES.contains(resource) || TEACHER_ACTIONS.contains(resource)) {
                return true;
            }
            if ("duty-assignments".equals(resource) || "school-tasks".equals(resource)) {
                return action == Action.UPDATE;
            }
            return false;
        }

        if (isGuardian(user)) {
            if (GUARDIAN_WRITES.contains(resource)) {
                return true;
            }
            return action == Action.READ && GUARDIAN_READ.contains(resource);
        }

        return false;
    }

    public boolean isAdmin(AuthenticatedUser user) {
        return user.roles().stream()
                .anyMatch(role -> EnumSet.of(
                        Role.HEAD_MASTER, Role.SECOND_MASTER, Role.HEAD_TEACHER,
                        Role.REGISTRAR).contains(role));
    }

    /** Platform/school admins bypass teacher assignment checks, unlike ad-hoc reviewers. */
    public boolean isPrivileged(AuthenticatedUser user) {
        return user.roles().contains(Role.SUPER_ADMIN)
                || user.roles().contains(Role.TENANT_ADMIN)
                || isAdmin(user);
    }

    public boolean isTeacher(AuthenticatedUser user) {
        return user.roles().stream()
                .anyMatch(role -> EnumSet.of(
                        Role.TEACHER, Role.CLASS_TEACHER, Role.ACADEMIC_MASTER,
                        Role.HEAD_TEACHER).contains(role));
    }

    public boolean isGuardian(AuthenticatedUser user) {
        return user.roles().contains(Role.PARENT) || user.roles().contains(Role.STUDENT);
    }

    public boolean isOperationsStaff(AuthenticatedUser user) {
        return user.roles().stream().anyMatch(role -> EnumSet.of(
                Role.LIBRARIAN, Role.STORE_KEEPER, Role.CASHIER).contains(role));
    }

    /** Parent and student accounts only ever see their own linked records. */
    public boolean isScoped(AuthenticatedUser user) {
        return isGuardian(user);
    }

    private boolean adminCan(AuthenticatedUser user, String resource, Action action) {
        boolean headTeacher = user.roles().contains(Role.HEAD_TEACHER)
                || user.roles().contains(Role.HEAD_MASTER);
        boolean secondMaster = user.roles().contains(Role.SECOND_MASTER)
                && !headTeacher;

        if (ADMIN_ACTIONS.contains(resource) || ACADEMIC_PLANNING.contains(resource)) {
            return true;
        }
        if (ACADEMIC.contains(resource) || STRUCTURE.contains(resource)) {
            return true;
        }
        if (OPERATIONS.contains(resource) || DOCUMENTS.contains(resource)) {
            return true;
        }
        if (LIBRARY.contains(resource)) {
            return true;
        }
        if (FINANCE.contains(resource) || STORE.contains(resource)) {
            return !secondMaster || action == Action.READ;
        }
        if (headTeacher) {
            return Set.of("reports", "settings").contains(resource);
        }
        return action == Action.READ
                && Set.of("reports", "settings").contains(resource);
    }
}
