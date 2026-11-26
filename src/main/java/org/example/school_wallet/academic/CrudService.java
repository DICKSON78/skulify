package org.example.school_wallet.academic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.common.api.PageResponse;
import org.example.school_wallet.common.audit.AuditService;
import org.example.school_wallet.common.domain.TenantOwnedEntity;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.operations.Book;
import org.example.school_wallet.operations.BookCopy;
import org.example.school_wallet.operations.BookLoan;
import org.example.school_wallet.operations.BookReservation;
import org.example.school_wallet.operations.Department;
import org.example.school_wallet.operations.DutyAssignment;
import org.example.school_wallet.operations.Exam;
import org.example.school_wallet.operations.ExamSession;
import org.example.school_wallet.operations.FeeCategory;
import org.example.school_wallet.operations.FeePlan;
import org.example.school_wallet.operations.InventoryItem;
import org.example.school_wallet.operations.Invoice;
import org.example.school_wallet.operations.InvoiceItem;
import org.example.school_wallet.operations.LabSession;
import org.example.school_wallet.operations.ParentLetter;
import org.example.school_wallet.operations.PocketCard;
import org.example.school_wallet.operations.CashWithdrawalRequest;
import org.example.school_wallet.operations.PocketTransaction;
import org.example.school_wallet.operations.Period;
import org.example.school_wallet.operations.PurchaseRequest;
import org.example.school_wallet.operations.ReportCard;
import org.example.school_wallet.operations.SchoolDocument;
import org.example.school_wallet.operations.SchoolDocumentStorage;
import org.example.school_wallet.operations.SchoolEvent;
import org.example.school_wallet.operations.SchoolTask;
import org.example.school_wallet.operations.SpendingLimit;
import org.example.school_wallet.operations.StaffAttendance;
import org.example.school_wallet.operations.StaffLeave;
import org.example.school_wallet.operations.StockMovement;
import org.example.school_wallet.operations.StoreItem;
import org.example.school_wallet.operations.StoreTerminal;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.example.school_wallet.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared CRUD engine for the school modules. Hibernate's tenant discriminator
 * keeps every row inside the current school, and this service adds role-based
 * row scoping for parents and students.
 */
@Service
@Transactional
public class CrudService {

    private static final Map<String, Class<? extends TenantOwnedEntity>> TYPES = Map.ofEntries(
            Map.entry("students", Student.class),
            Map.entry("teachers", Teacher.class),
            Map.entry("classes", SchoolClass.class),
            Map.entry("subjects", Subject.class),
            Map.entry("attendance", AttendanceRecord.class),
            Map.entry("grades", GradeRecord.class),
            Map.entry("fees", FeeRecord.class),
            Map.entry("timetable", TimetableEntry.class),
            Map.entry("reports", ReportDefinition.class),
            Map.entry("settings", AppSetting.class),
            Map.entry("academic-years", AcademicYear.class),
            Map.entry("terms", Term.class),
            Map.entry("enrolments", Enrolment.class),
            Map.entry("teacher-assignments", TeacherAssignment.class),
            Map.entry("payments", FeePayment.class),
            Map.entry("notifications", NotificationOutbox.class),
            Map.entry("fee-categories", FeeCategory.class),
            Map.entry("fee-plans", FeePlan.class),
            Map.entry("invoices", Invoice.class),
            Map.entry("invoice-items", InvoiceItem.class),
            Map.entry("pocket-cards", PocketCard.class),
            Map.entry("spending-limits", SpendingLimit.class),
            Map.entry("pocket-transactions", PocketTransaction.class),
            Map.entry("cash-requests", CashWithdrawalRequest.class),
            Map.entry("store-items", StoreItem.class),
            Map.entry("store-terminals", StoreTerminal.class),
            Map.entry("inventory-items", InventoryItem.class),
            Map.entry("stock-movements", StockMovement.class),
            Map.entry("purchase-requests", PurchaseRequest.class),
            Map.entry("departments", Department.class),
            Map.entry("staff-attendance", StaffAttendance.class),
            Map.entry("school-tasks", SchoolTask.class),
            Map.entry("books", Book.class),
            Map.entry("book-copies", BookCopy.class),
            Map.entry("book-loans", BookLoan.class),
            Map.entry("book-reservations", BookReservation.class),
            Map.entry("lab-sessions", LabSession.class),
            Map.entry("periods", Period.class),
            Map.entry("exams", Exam.class),
            Map.entry("exam-sessions", ExamSession.class),
            Map.entry("duty-assignments", DutyAssignment.class),
            Map.entry("report-cards", ReportCard.class),
            Map.entry("staff-leaves", StaffLeave.class),
            Map.entry("school-events", SchoolEvent.class),
            Map.entry("parent-letters", ParentLetter.class),
            Map.entry("school-documents", SchoolDocument.class));

    private static final Set<String> READ_ONLY_FIELDS = Set.of(
            "id", "tenantId", "version", "createdAt", "updatedAt", "createdBy", "updatedBy");

    /** On learning records the teacher is stamped by the server, never by the client. */
    private static final Set<String> LEARNING_RESOURCES = Set.of("attendance", "grades");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final ResourceAccessPolicy accessPolicy;
    private final AcademicWorkflowService workflow;
    private final GradingService grading;
    private final StudentRepository students;
    private final NotificationService notifications;
    private final SchoolDocumentStorage documentStorage;

    public CrudService(
            EntityManager entityManager,
            ObjectMapper objectMapper,
            AuditService auditService,
            ResourceAccessPolicy accessPolicy,
            AcademicWorkflowService workflow,
            GradingService grading,
            StudentRepository students,
            NotificationService notifications,
            SchoolDocumentStorage documentStorage) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.accessPolicy = accessPolicy;
        this.workflow = workflow;
        this.grading = grading;
        this.students = students;
        this.notifications = notifications;
        this.documentStorage = documentStorage;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String resource, int page, int size, String query) {
        return listTyped(type(resource), page, size, query);
    }

    private <E extends TenantOwnedEntity> PageResponse<Map<String, Object>> listTyped(
            Class<E> type, int page, int size, String query) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Optional<AuthenticatedUser> current = CurrentUser.get();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<E> countRoot = countQuery.from(type);
        countQuery.select(cb.count(countRoot));
        Predicate countWhere = filters(cb, countRoot, countQuery, query, current, type);
        if (countWhere != null) {
            countQuery.where(countWhere);
        }
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<E> dataQuery = cb.createQuery(type);
        Root<E> root = dataQuery.from(type);
        dataQuery.select(root);
        Predicate where = filters(cb, root, dataQuery, query, current, type);
        if (where != null) {
            dataQuery.where(where);
        }
        dataQuery.orderBy(cb.desc(root.get("createdAt")));
        TypedQuery<E> typed = entityManager.createQuery(dataQuery)
                .setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize);
        List<Map<String, Object>> content = typed.getResultList().stream().map(this::toMap).toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String resource, UUID id) {
        TenantOwnedEntity entity = find(resource, id);
        requireVisible(entity);
        return toMap(entity);
    }

    public Map<String, Object> create(String resource, Map<String, Object> body) {
        try {
            TenantOwnedEntity entity = objectMapper.treeToValue(clean(body, resource), type(resource));
            decorate(entity, null);
            if (entity instanceof StudentLinked) {
                requireVisible(entity);
            }
            entityManager.persist(entity);
            entityManager.flush();
            audit("CREATED", resource, entity.getId());
            if (entity instanceof StaffAttendance attendance) {
                notifyStaffAttendance(attendance,
                        hasText(attendance.getCheckIn()), hasText(attendance.getCheckOut()));
            }
            return toMap(entity);
        } catch (org.example.school_wallet.common.error.ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw Errors.badRequest("INVALID_REQUEST", "The submitted data is not valid");
        }
    }

    public Map<String, Object> update(String resource, UUID id, Map<String, Object> body) {
        TenantOwnedEntity entity = find(resource, id);
        requireVisible(entity);
        String previousCheckIn = entity instanceof StaffAttendance attendance ? attendance.getCheckIn() : null;
        String previousCheckOut = entity instanceof StaffAttendance attendance ? attendance.getCheckOut() : null;
        try {
            String json = objectMapper.writeValueAsString(clean(body, resource));
            objectMapper.readerForUpdating(entity).readValue(json);
            decorate(entity, id);
            entityManager.merge(entity);
            entityManager.flush();
            audit("UPDATED", resource, id);
            if (entity instanceof StaffAttendance attendance) {
                notifyStaffAttendance(attendance,
                        hasText(attendance.getCheckIn())
                                && !Objects.equals(previousCheckIn, attendance.getCheckIn()),
                        hasText(attendance.getCheckOut())
                                && !Objects.equals(previousCheckOut, attendance.getCheckOut()));
            }
            return toMap(entity);
        } catch (org.example.school_wallet.common.error.ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw Errors.badRequest("INVALID_REQUEST", "The submitted data is not valid");
        }
    }

    public void delete(String resource, UUID id) {
        TenantOwnedEntity entity = find(resource, id);
        requireVisible(entity);
        audit("DELETED", resource, id);
        if (entity instanceof SchoolDocument document) {
            documentStorage.delete(document.getFilePath());
        }
        entityManager.remove(entity);
    }

    /** Sends check-in/out alerts to the school's HR and leadership inboxes. */
    private void notifyStaffAttendance(
            StaffAttendance attendance, boolean checkInChanged, boolean checkOutChanged) {
        String staff = staffName(attendance.getStaffId());
        if (checkInChanged) {
            sendStaffNotice(attendance, "Staff check-in",
                    staff + " checked in at " + attendance.getCheckIn()
                            + " on " + attendance.getAttendanceDate() + ".");
        }
        if (checkOutChanged) {
            sendStaffNotice(attendance, "Staff check-out",
                    staff + " checked out at " + attendance.getCheckOut()
                            + " on " + attendance.getAttendanceDate() + ".");
        }
    }

    private void sendStaffNotice(StaffAttendance attendance, String subject, String body) {
        for (String recipient : List.of("HR_MANAGER", "HEAD_MASTER", "TENANT_ADMIN")) {
            notifications.send(NotificationOutbox.Channel.IN_APP, recipient, subject, body,
                    "staff-attendance", attendance.getId());
        }
    }

    private String staffName(UUID staffId) {
        if (staffId == null) {
            return "A staff member";
        }
        Teacher teacher = entityManager.find(Teacher.class, staffId);
        return teacher == null
                ? "A staff member"
                : (teacher.getFirstName() + " " + teacher.getLastName()).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TenantOwnedEntity find(String resource, UUID id) {
        TenantOwnedEntity entity = entityManager.find(type(resource), id);
        if (entity == null) {
            throw Errors.notFound(resource.toUpperCase() + "_NOT_FOUND", "Record not found");
        }
        return entity;
    }

    private Class<? extends TenantOwnedEntity> type(String resource) {
        Class<? extends TenantOwnedEntity> type = TYPES.get(resource);
        if (type == null) {
            throw Errors.notFound("UNKNOWN_RESOURCE", "Unknown resource: " + resource);
        }
        return type;
    }

    private JsonNode clean(Map<String, Object> body, String resource) {
        ObjectNode node = objectMapper.valueToTree(body);
        READ_ONLY_FIELDS.forEach(node::remove);
        if (LEARNING_RESOURCES.contains(resource)) {
            node.remove("teacherId");
        }
        return node;
    }

    private Map<String, Object> toMap(Object entity) {
        return objectMapper.convertValue(entity, MAP_TYPE);
    }

    private Predicate filters(
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<?> query,
            String search,
            Optional<AuthenticatedUser> current,
            Class<?> type) {
        List<Predicate> parts = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            parts.add(searchPredicate(cb, root, search));
        }
        if (current.isPresent()) {
            Predicate scoped = scopedPredicate(cb, root, query, type, current.get());
            if (scoped != null) {
                parts.add(scoped);
            }
        }
        return parts.isEmpty() ? null : cb.and(parts.toArray(Predicate[]::new));
    }

    private Predicate searchPredicate(CriteriaBuilder cb, Root<?> root, String query) {
        List<Predicate> parts = new ArrayList<>();
        String pattern = "%" + query.toLowerCase() + "%";
        root.getModel().getAttributes().stream()
                .filter(attribute -> attribute.getJavaType() == String.class)
                .forEach(attribute -> parts.add(
                        cb.like(cb.lower(root.get(attribute.getName()).as(String.class)), pattern)));
        return cb.or(parts.toArray(Predicate[]::new));
    }

    private Predicate scopedPredicate(
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<?> query,
            Class<?> type,
            AuthenticatedUser user) {
        if (!accessPolicy.isScoped(user)) {
            return null;
        }
        boolean studentRole = user.roles().contains(Role.STUDENT);

        if (Student.class.equals(type)) {
            return cb.or(
                    cb.equal(root.get("userId"), user.id()),
                    cb.equal(root.get("guardianUserId"), user.id()));
        }
        if (!StudentLinked.class.isAssignableFrom(type)) {
            return cb.disjunction();
        }

        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<Student> studentRoot = subquery.from(Student.class);
        Predicate owner = studentRole
                ? cb.equal(studentRoot.get("userId"), user.id())
                : cb.equal(studentRoot.get("guardianUserId"), user.id());
        subquery.select(studentRoot.get("id")).where(owner);
        return root.get("studentId").in(subquery);
    }

    private void requireVisible(TenantOwnedEntity entity) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty() || !accessPolicy.isScoped(current.get())) {
            return;
        }
        AuthenticatedUser user = current.get();
        boolean visible;
        if (entity instanceof Student student) {
            visible = user.id().equals(student.getUserId())
                    || user.id().equals(student.getGuardianUserId());
        } else if (entity instanceof StudentLinked linked) {
            Student student = students.findById(linked.getStudentId()).orElse(null);
            visible = student != null
                    && (user.id().equals(student.getUserId())
                        || user.id().equals(student.getGuardianUserId()));
        } else {
            visible = false;
        }
        if (!visible) {
            throw Errors.forbidden("NOT_YOUR_RECORD", "This record does not belong to your account");
        }
    }

    /** Stamps actor-owned fields and applies domain validation before writes. */
    private void decorate(TenantOwnedEntity entity, UUID id) {
        Optional<AuthenticatedUser> current = CurrentUser.get();

        if (entity instanceof AttendanceRecord attendance) {
            current.filter(accessPolicy::isTeacher)
                    .filter(user -> !accessPolicy.isAdmin(user))
                    .ifPresent(user -> {
                        attendance.setTeacherId(user.id());
                        workflow.validateAttendanceWrite(attendance);
                    });
        }
        if (entity instanceof GradeRecord grade) {
            current.filter(accessPolicy::isTeacher)
                    .filter(user -> !accessPolicy.isAdmin(user))
                    .ifPresent(user -> {
                        workflow.requireGradeWrite(grade.getSubjectId());
                        grade.setTeacherId(user.id());
                    });
            if (grade.getGrade() == null || grade.getGrade().isBlank()) {
                grade.setGrade(grading.grade(grade.getScore(), grade.getMaxScore()));
            }
        }
        if (entity instanceof FeeRecord fee) {
            fee.setStatus(deriveFeeStatus(fee));
        }
        if (entity instanceof TimetableEntry entry) {
            workflow.validateTimetable(entry, id);
        }
    }

    private FeeRecord.Status deriveFeeStatus(FeeRecord fee) {
        BigDecimal amount = fee.getAmount() == null ? BigDecimal.ZERO : fee.getAmount();
        BigDecimal paid = fee.getPaidAmount() == null ? BigDecimal.ZERO : fee.getPaidAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || paid.compareTo(amount) >= 0) {
            return FeeRecord.Status.PAID;
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return FeeRecord.Status.PARTIAL;
        }
        return FeeRecord.Status.PENDING;
    }

    private void audit(String action, String resource, UUID id) {
        auditService.record(resource.toUpperCase() + "_" + action, resource.toUpperCase(), id.toString(), Map.of());
    }
}
