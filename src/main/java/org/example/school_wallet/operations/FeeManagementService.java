package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.FeePayment;
import org.example.school_wallet.academic.FeePaymentRepository;
import org.example.school_wallet.academic.FeeRecord;
import org.example.school_wallet.academic.FeeRecordRepository;
import org.example.school_wallet.academic.FeeService;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.common.error.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Complete fee lifecycle: plans, invoices, payments, reversals and overdue reminders. */
@Service
@Transactional
public class FeeManagementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final EntityManager em;
    private final FeeRecordRepository fees;
    private final FeePaymentRepository payments;
    private final FeeService feeService;
    private final OperationsSupport support;

    public FeeManagementService(
            EntityManager em,
            FeeRecordRepository fees,
            FeePaymentRepository payments,
            FeeService feeService,
            OperationsSupport support) {
        this.em = em;
        this.fees = fees;
        this.payments = payments;
        this.feeService = feeService;
        this.support = support;
    }

    public Invoice issueInvoice(Map<String, Object> body) {
        UUID planId = support.uuid(body.get("feePlanId"));
        if (planId == null) {
            throw Errors.badRequest("PLAN_REQUIRED", "feePlanId is required");
        }
        FeePlan plan = find(FeePlan.class, planId);
        UUID studentId = support.uuid(body.get("studentId"));
        support.requireVisibleStudent(studentId);
        Student student = support.student(studentId);

        BigDecimal total = support.amount(body.get("totalAmount"), plan.getAmount());
        if (total == null || total.compareTo(ZERO) <= 0) {
            throw Errors.badRequest("INVALID_AMOUNT", "Invoice amount must be greater than zero");
        }
        String invoiceNo = support.text(body.get("invoiceNo"));
        if (invoiceNo.isEmpty()) {
            invoiceNo = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (findInvoiceByNo(invoiceNo) != null) {
            throw Errors.conflict("DUPLICATE_INVOICE", "Invoice number already exists");
        }

        String today = LocalDate.now().toString();
        String issueDate = support.text(body.get("issueDate"));
        String dueDate = support.text(body.get("dueDate"));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setStudentId(studentId);
        invoice.setFeePlanId(planId);
        invoice.setTermId(plan.getTermId());
        invoice.setIssueDate(issueDate.isEmpty() ? today : issueDate);
        invoice.setDueDate(dueDate.isEmpty() ? plan.getDueDate() : dueDate);
        invoice.setTotalAmount(total);
        invoice.setPaidAmount(ZERO);
        invoice.setStatus(Invoice.Status.ISSUED);
        invoice.setRemarks(support.text(body.get("remarks")));
        em.persist(invoice);

        InvoiceItem item = new InvoiceItem();
        item.setInvoiceId(invoice.getId());
        item.setStudentId(studentId);
        item.setCategoryId(plan.getCategoryId());
        item.setDescription(support.text(body.get("description")).isEmpty()
                ? plan.getName()
                : support.text(body.get("description")));
        item.setQuantity(1);
        item.setUnitAmount(total);
        item.setAmount(total);
        em.persist(item);

        FeeRecord fee = new FeeRecord();
        fee.setReference(invoiceNo);
        fee.setStudentId(studentId);
        fee.setFeeType(plan.getName());
        fee.setAmount(total);
        fee.setPaidAmount(ZERO);
        fee.setDueDate(invoice.getDueDate());
        fee.setStatus(FeeRecord.Status.PENDING);
        fee.setRemarks("Generated from " + plan.getCode());
        em.persist(fee);

        support.audit("FEE_INVOICE_ISSUED", "INVOICES", invoice.getId(),
                Map.of("studentCode", student.getStudentCode(), "amount", total));
        support.notifyGuardian(
                student,
                "Fee invoice issued",
                "Invoice " + invoiceNo + " of " + total + " TZS is due "
                        + invoice.getDueDate(),
                "invoices",
                invoice.getId());
        return invoice;
    }

    public List<Invoice> generateForClass(UUID planId, String className) {
        FeePlan plan = find(FeePlan.class, planId);
        if (className == null || className.isBlank()) {
            className = em.createQuery("select c.name from SchoolClass c where c.id = :id", String.class)
                    .setParameter("id", plan.getClassId())
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> Errors.badRequest("CLASS_REQUIRED", "Class is required"));
        }
        List<Student> students = em.createQuery(
                        "select s from Student s where s.className = :className and s.status = 'ACTIVE'",
                        Student.class)
                .setParameter("className", className)
                .getResultList();
        students.forEach(student -> issueInvoice(Map.of(
                "feePlanId", planId.toString(),
                "studentId", student.getId().toString())));
        return em.createQuery(
                        "select i from Invoice i where i.feePlanId = :planId order by i.createdAt desc",
                        Invoice.class)
                .setParameter("planId", planId)
                .getResultList();
    }

    public FeePayment recordPayment(UUID feeId, Map<String, Object> body) {
        FeePayment payment = feeService.recordPayment(feeId, body);
        FeeRecord fee = fees.findById(feeId).orElseThrow();
        Invoice invoice = findInvoiceByNo(fee.getReference());
        if (invoice != null) {
            BigDecimal paid = invoice.getPaidAmount() == null ? ZERO : invoice.getPaidAmount();
            invoice.setPaidAmount(paid.add(payment.getAmount()));
            invoice.setStatus(deriveInvoiceStatus(invoice));
            em.merge(invoice);
        }
        support.audit("FEE_PAYMENT_RECORDED", "PAYMENTS", payment.getId(),
                Map.of("feeId", feeId.toString(), "amount", payment.getAmount()));
        return payment;
    }

    public FeePayment reversePayment(UUID paymentId, String reason) {
        FeePayment payment = payments.findById(paymentId)
                .orElseThrow(() -> Errors.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (payment.getStatus() != FeePayment.Status.CONFIRMED) {
            throw Errors.badRequest("PAYMENT_NOT_CONFIRMED", "Only confirmed payments can be reversed");
        }
        payment.setStatus(FeePayment.Status.REVERSED);
        payments.save(payment);

        FeeRecord fee = fees.findById(payment.getFeeId()).orElse(null);
        if (fee != null) {
            BigDecimal paid = fee.getPaidAmount() == null ? ZERO : fee.getPaidAmount();
            fee.setPaidAmount(paid.subtract(payment.getAmount()).max(ZERO));
            fee.setStatus(deriveFeeStatus(fee));
            if (fee.getStatus() != FeeRecord.Status.PAID) {
                fee.setPaidDate(null);
            }
            fees.save(fee);

            Invoice invoice = findInvoiceByNo(fee.getReference());
            if (invoice != null) {
                BigDecimal invoicePaid = invoice.getPaidAmount() == null ? ZERO : invoice.getPaidAmount();
                invoice.setPaidAmount(invoicePaid.subtract(payment.getAmount()).max(ZERO));
                invoice.setStatus(deriveInvoiceStatus(invoice));
                em.merge(invoice);
            }
            Student student = support.student(fee.getStudentId());
            support.notifyGuardian(
                    student,
                    "Fee payment reversed",
                    "Payment of " + payment.getAmount() + " TZS was reversed. Reason: " + reason,
                    "payments",
                    payment.getId());
        }
        support.audit(
                "FEE_PAYMENT_REVERSED",
                "PAYMENTS",
                payment.getId(),
                Map.of("reason", reason == null ? "" : reason));
        return payment;
    }

    public int markOverdueAndRemind() {
        String today = LocalDate.now().toString();
        List<Invoice> overdue = em.createQuery(
                        """
                        select i from Invoice i
                        where i.dueDate is not null and i.dueDate < :today
                          and i.status not in ('PAID','OVERDUE','CANCELLED')
                        """,
                        Invoice.class)
                .setParameter("today", today)
                .getResultList();
        overdue.forEach(invoice -> {
            invoice.setStatus(Invoice.Status.OVERDUE);
            em.merge(invoice);
            FeeRecord fee = fees.findAll().stream()
                    .filter(row -> invoice.getInvoiceNo().equals(row.getReference()))
                    .findFirst()
                    .orElse(null);
            if (fee != null) {
                fee.setStatus(FeeRecord.Status.OVERDUE);
                fees.save(fee);
            }
            support.notifyGuardian(
                    support.student(invoice.getStudentId()),
                    "Overdue fee reminder",
                    "Invoice " + invoice.getInvoiceNo() + " is overdue. Please settle "
                            + invoice.getTotalAmount() + " TZS.",
                    "invoices",
                    invoice.getId());
        });
        return overdue.size();
    }

    public Map<String, Object> summary() {
        List<FeeRecord> all = fees.findAll();
        BigDecimal billed = all.stream()
                .map(FeeRecord::getAmount)
                .filter(amount -> amount != null)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal collected = all.stream()
                .map(FeeRecord::getPaidAmount)
                .filter(amount -> amount != null)
                .reduce(ZERO, BigDecimal::add);
        long overdue = all.stream().filter(row -> row.getStatus() == FeeRecord.Status.OVERDUE).count();
        long invoices = em.createQuery("select count(i) from Invoice i", Long.class).getSingleResult();
        return Map.of(
                "billed", billed,
                "collected", collected,
                "outstanding", billed.subtract(collected),
                "overdue", overdue,
                "invoices", invoices);
    }

    private <T> T find(Class<T> type, UUID id) {
        T entity = em.find(type, id);
        if (entity == null) {
            throw Errors.notFound(type.getSimpleName().toUpperCase() + "_NOT_FOUND", "Record not found");
        }
        return entity;
    }

    private Invoice findInvoiceByNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return null;
        }
        return em.createQuery("select i from Invoice i where i.invoiceNo = :no", Invoice.class)
                .setParameter("no", invoiceNo)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private Invoice.Status deriveInvoiceStatus(Invoice invoice) {
        BigDecimal total = invoice.getTotalAmount() == null ? ZERO : invoice.getTotalAmount();
        BigDecimal paid = invoice.getPaidAmount() == null ? ZERO : invoice.getPaidAmount();
        if (total.compareTo(ZERO) <= 0 || paid.compareTo(total) >= 0) {
            return Invoice.Status.PAID;
        }
        return paid.compareTo(ZERO) > 0 ? Invoice.Status.PARTIAL : Invoice.Status.ISSUED;
    }

    private FeeRecord.Status deriveFeeStatus(FeeRecord fee) {
        BigDecimal total = fee.getAmount() == null ? ZERO : fee.getAmount();
        BigDecimal paid = fee.getPaidAmount() == null ? ZERO : fee.getPaidAmount();
        if (total.compareTo(ZERO) <= 0 || paid.compareTo(total) >= 0) {
            return FeeRecord.Status.PAID;
        }
        return paid.compareTo(ZERO) > 0 ? FeeRecord.Status.PARTIAL : FeeRecord.Status.PENDING;
    }
}
