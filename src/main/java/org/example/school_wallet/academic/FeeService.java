package org.example.school_wallet.academic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.common.time.TimeValues;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fee ledger: every payment is recorded separately; the fee header is derived. */
@Service
@Transactional
public class FeeService {

    private final FeeRecordRepository fees;
    private final FeePaymentRepository payments;
    private final StudentRepository students;
    private final NotificationService notifications;
    private final ResourceAccessPolicy accessPolicy;

    public FeeService(
            FeeRecordRepository fees,
            FeePaymentRepository payments,
            StudentRepository students,
            NotificationService notifications,
            ResourceAccessPolicy accessPolicy) {
        this.fees = fees;
        this.payments = payments;
        this.students = students;
        this.notifications = notifications;
        this.accessPolicy = accessPolicy;
    }

    public FeePayment recordPayment(UUID feeId, Map<String, Object> body) {
        FeeRecord fee = fees.findById(feeId)
                .orElseThrow(() -> Errors.notFound("FEE_NOT_FOUND", "Fee record not found"));

        BigDecimal amount = parseAmount(body.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw Errors.badRequest("INVALID_AMOUNT", "Payment amount must be greater than zero");
        }

        BigDecimal paid = fee.getPaidAmount() == null ? BigDecimal.ZERO : fee.getPaidAmount();
        BigDecimal outstanding = fee.getAmount().subtract(paid);
        if (amount.compareTo(outstanding) > 0) {
            throw Errors.badRequest(
                    "OVERPAYMENT", "Payment exceeds the outstanding balance of " + outstanding);
        }

        FeePayment payment = new FeePayment();
        payment.setFeeId(feeId);
        payment.setStudentId(fee.getStudentId());
        payment.setAmount(amount);
        payment.setMethod(asText(body.get("method")).isEmpty() ? "CASH" : asText(body.get("method")));
        payment.setProviderReference(asText(body.get("providerReference")));
        payment.setPaidAt(asText(body.get("paidAt")).isEmpty() ? TimeValues.now() : asText(body.get("paidAt")));
        payment.setRecordedBy(CurrentUser.get().map(user -> user.fullName()).orElse("system"));
        payment.setStatus(FeePayment.Status.CONFIRMED);
        payment = payments.save(payment);

        fee.setPaidAmount(paid.add(amount));
        fee.setStatus(deriveStatus(fee));
        if (fee.getStatus() == FeeRecord.Status.PAID) {
            fee.setPaidDate(payment.getPaidAt());
        }
        fees.save(fee);

        students.findById(fee.getStudentId()).ifPresent(student ->
                notifications.send(
                        NotificationOutbox.Channel.SMS,
                        student.getGuardianPhone(),
                        "Fee payment received",
                        "Payment of " + amount + " TZS received for " + fee.getReference(),
                        "fees",
                        feeId));
        return payment;
    }

    public List<FeePayment> history(UUID feeId) {
        FeeRecord fee = fees.findById(feeId)
                .orElseThrow(() -> Errors.notFound("FEE_NOT_FOUND", "Fee record not found"));
        requireVisible(fee.getStudentId());
        return payments.findAll().stream()
                .filter(payment -> feeId.equals(payment.getFeeId()))
                .toList();
    }

    private void requireVisible(UUID studentId) {
        Optional<AuthenticatedUser> current = CurrentUser.get();
        if (current.isEmpty() || !accessPolicy.isScoped(current.get())) {
            return;
        }
        UUID userId = current.get().id();
        Student student = students.findById(studentId)
                .orElseThrow(() -> Errors.notFound("STUDENT_NOT_FOUND", "Student not found"));
        if (!userId.equals(student.getUserId()) && !userId.equals(student.getGuardianUserId())) {
            throw Errors.forbidden("NOT_YOUR_RECORD", "This record does not belong to your account");
        }
    }

    private FeeRecord.Status deriveStatus(FeeRecord fee) {
        BigDecimal total = fee.getAmount() == null ? BigDecimal.ZERO : fee.getAmount();
        BigDecimal paid = fee.getPaidAmount() == null ? BigDecimal.ZERO : fee.getPaidAmount();
        if (paid.compareTo(total) >= 0) {
            return FeeRecord.Status.PAID;
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return FeeRecord.Status.PARTIAL;
        }
        return FeeRecord.Status.PENDING;
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
