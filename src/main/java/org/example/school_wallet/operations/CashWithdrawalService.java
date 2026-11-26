package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.NotificationOutbox;
import org.example.school_wallet.academic.NotificationService;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.academic.Teacher;
import org.example.school_wallet.academic.TeacherRepository;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher cash requests approved by a guardian and debited from the child's Haba card. */
@Service
@Transactional
public class CashWithdrawalService {

    private final EntityManager em;
    private final OperationsSupport support;
    private final PocketMoneyService pocket;
    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final NotificationService notifications;
    private final ResourceAccessPolicy access;

    public CashWithdrawalService(
            EntityManager em,
            OperationsSupport support,
            PocketMoneyService pocket,
            TeacherRepository teachers,
            StudentRepository students,
            NotificationService notifications,
            ResourceAccessPolicy access) {
        this.em = em;
        this.support = support;
        this.pocket = pocket;
        this.teachers = teachers;
        this.students = students;
        this.notifications = notifications;
        this.access = access;
    }

    public CashWithdrawalRequest create(Map<String, Object> body) {
        AuthenticatedUser user = currentUser();
        Teacher teacher = currentTeacher(user);
        if (teacher == null && !access.isPrivileged(user)) {
            throw Errors.forbidden("TEACHER_REQUIRED", "Only a teacher can request school cash");
        }
        if (teacher == null) {
            UUID teacherId = support.uuid(body.get("teacherId"));
            teacher = teacherId == null
                    ? null
                    : teachers.findById(teacherId).orElse(null);
            if (teacher == null) {
                throw Errors.badRequest("TEACHER_REQUIRED", "A teacher must be linked to this request");
            }
        }

        Student student = support.student(support.uuid(body.get("studentId")));
        PocketCard card = pocket.activeCard(student.getId());
        if (card == null) {
            throw Errors.notFound("CARD_NOT_FOUND", "The student has no active Haba card");
        }
        BigDecimal amount = support.amount(body.get("amount"), BigDecimal.ZERO);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw Errors.badRequest("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        if (amount.compareTo(card.getBalance()) > 0) {
            throw Errors.conflict("INSUFFICIENT_BALANCE", "Insufficient pocket money balance");
        }

        CashWithdrawalRequest request = new CashWithdrawalRequest();
        request.setStudentId(student.getId());
        request.setTeacherId(teacher.getId());
        request.setAmount(amount);
        request.setReason(defaultText(body.get("reason"), "Cash needed for school activities"));
        request.setStatus(CashWithdrawalRequest.Status.PENDING);
        request.setRequestedAt(support.now());
        request.setPayoutReference("CASH-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        request.setPhoneNumber(teacher.getPhone() == null || teacher.getPhone().isBlank()
                ? "0700000000"
                : teacher.getPhone());
        em.persist(request);

        support.audit("CASH_REQUEST_CREATED", "CASH_WITHDRAWAL_REQUESTS", request.getId(),
                Map.of("amount", amount, "studentCode", student.getStudentCode()));
        support.notifyGuardian(
                student,
                "Teacher cash request",
                teacherName(teacher) + " requested " + amount + " TZS cash for "
                        + student.getFirstName() + ". Open Skulify to approve or decline.",
                "cash-requests",
                request.getId());
        return request;
    }

    public List<CashWithdrawalRequest> list() {
        List<CashWithdrawalRequest> rows = em.createQuery(
                        "select r from CashWithdrawalRequest r order by r.createdAt desc",
                        CashWithdrawalRequest.class)
                .getResultList();
        AuthenticatedUser user = currentUser();
        if (access.isGuardian(user)) {
            return rows.stream().filter(row -> isVisibleToGuardian(row, user)).toList();
        }
        if (access.isTeacher(user) && !access.isPrivileged(user)) {
            Teacher teacher = currentTeacher(user);
            String teacherId = teacher == null ? "" : teacher.getId().toString();
            return rows.stream()
                    .filter(row -> teacherId.equals(row.getTeacherId().toString()))
                    .toList();
        }
        return rows;
    }

    public CashWithdrawalRequest decide(UUID id, String decision, String note) {
        CashWithdrawalRequest request = find(id);
        AuthenticatedUser user = currentUser();
        if (access.isTeacher(user) && !access.isPrivileged(user)) {
            throw Errors.forbidden("APPROVAL_REQUIRED", "A guardian or school admin must approve cash");
        }
        if (access.isGuardian(user)) {
            support.requireVisibleStudent(request.getStudentId());
        }
        if (request.getStatus() != CashWithdrawalRequest.Status.PENDING) {
            throw Errors.badRequest("ALREADY_DECIDED", "This cash request was already decided");
        }
        boolean approve = "APPROVE".equalsIgnoreCase(defaultText(decision, ""));
        if (approve) {
            Student student = support.student(request.getStudentId());
            PocketCard card = pocket.activeCard(student.getId());
            if (card == null) {
                throw Errors.notFound("CARD_NOT_FOUND", "The student has no active Haba card");
            }
            pocket.cashOut(
                    card.getId(),
                    request.getAmount(),
                    "Cash released to teacher: " + defaultText(note, "school cash request"));
            request.setStatus(CashWithdrawalRequest.Status.APPROVED);
            request.setPayoutReference("PAY-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
            request.setDecidedAt(support.now());
            request.setApprovedBy(user.id());
            request.setNote(defaultText(note, "Approved by guardian"));
            notifyTeacher(request,
                    "Cash request approved",
                    request.getAmount() + " TZS is on your phone. Withdraw at agent using "
                            + request.getPayoutReference() + ".");
            support.notifyGuardian(
                    student,
                    "Cash request approved",
                    request.getAmount() + " TZS was released to the teacher from the child's card.",
                    "cash-requests",
                    request.getId());
        } else {
            request.setStatus(CashWithdrawalRequest.Status.DECLINED);
            request.setDecidedAt(support.now());
            request.setApprovedBy(user.id());
            request.setNote(defaultText(note, "Declined by guardian"));
            notifyTeacher(request,
                    "Cash request declined",
                    "The guardian declined the " + request.getAmount() + " TZS cash request.");
        }
        em.merge(request);
        support.audit(
                approve ? "CASH_REQUEST_APPROVED" : "CASH_REQUEST_DECLINED",
                "CASH_WITHDRAWAL_REQUESTS",
                request.getId(),
                Map.of("amount", request.getAmount()));
        return request;
    }

    public CashWithdrawalRequest payout(UUID id, String agentCode, String note) {
        CashWithdrawalRequest request = find(id);
        AuthenticatedUser user = currentUser();
        if (access.isGuardian(user)) {
            throw Errors.forbidden("TEACHER_CONFIRMATION", "Only the teacher can confirm cash collection");
        }
        if (access.isTeacher(user) && !access.isPrivileged(user)) {
            Teacher teacher = currentTeacher(user);
            if (teacher == null || !teacher.getId().equals(request.getTeacherId())) {
                throw Errors.forbidden("NOT_YOUR_RECORD", "This cash request is not yours");
            }
        }
        if (request.getStatus() != CashWithdrawalRequest.Status.APPROVED) {
            throw Errors.badRequest("NOT_READY_FOR_PAYOUT", "Only approved cash can be withdrawn");
        }
        request.setStatus(CashWithdrawalRequest.Status.PAID);
        request.setAgentCode(defaultText(agentCode, "AGENT-COUNTER"));
        request.setNote(defaultText(note, "Cash collected at agent"));
        em.merge(request);
        support.audit("CASH_REQUEST_PAID", "CASH_WITHDRAWAL_REQUESTS", request.getId(),
                Map.of("agentCode", request.getAgentCode()));
        Student student = support.student(request.getStudentId());
        support.notifyGuardian(
                student,
                "Cash collected",
                "The teacher confirmed collection of " + request.getAmount() + " TZS at "
                        + request.getAgentCode() + ".",
                "cash-requests",
                request.getId());
        return request;
    }

    private CashWithdrawalRequest find(UUID id) {
        CashWithdrawalRequest request = em.find(CashWithdrawalRequest.class, id);
        if (request == null) {
            throw Errors.notFound("CASH_REQUEST_NOT_FOUND", "Cash request not found");
        }
        return request;
    }

    private AuthenticatedUser currentUser() {
        return CurrentUser.get()
                .orElseThrow(() -> Errors.forbidden("UNAUTHENTICATED", "Sign in to continue"));
    }

    private Teacher currentTeacher(AuthenticatedUser user) {
        return teachers.findByUserId(user.id()).orElse(null);
    }

    private boolean isVisibleToGuardian(CashWithdrawalRequest request, AuthenticatedUser user) {
        return students.findById(request.getStudentId())
                .map(student -> user.id().equals(student.getUserId())
                        || user.id().equals(student.getGuardianUserId()))
                .orElse(false);
    }

    private void notifyTeacher(CashWithdrawalRequest request, String subject, String body) {
        Teacher teacher = teachers.findById(request.getTeacherId()).orElse(null);
        if (teacher == null) {
            return;
        }
        if (teacher.getUserId() != null) {
            notifications.send(NotificationOutbox.Channel.IN_APP,
                    teacher.getUserId().toString(), subject, body, "cash-requests", request.getId());
        }
        notifications.send(NotificationOutbox.Channel.SMS,
                request.getPhoneNumber(), subject, body, "cash-requests", request.getId());
    }

    private String teacherName(Teacher teacher) {
        return (teacher.getFirstName() + " " + teacher.getLastName()).trim();
    }

    private String defaultText(Object value, String fallback) {
        String text = support.text(value);
        return text.isEmpty() ? fallback : text;
    }
}
