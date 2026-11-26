package org.example.school_wallet.operations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.common.error.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Library circulation, digital resources and computer-lab study sessions. */
@Service
@Transactional
public class LibraryService {

    private static final int LOAN_DAYS = 14;
    private static final int RENEW_DAYS = 7;
    private static final int MAX_LOANS = 5;
    private static final BigDecimal FINE_PER_DAY = BigDecimal.valueOf(500);

    private final EntityManager em;
    private final OperationsSupport support;

    public LibraryService(EntityManager em, OperationsSupport support) {
        this.em = em;
        this.support = support;
    }

    public BookLoan checkout(Map<String, Object> body) {
        UUID copyId = support.uuid(body.get("copyId"));
        UUID studentId = support.uuid(body.get("studentId"));
        support.requireVisibleStudent(studentId);
        BookCopy copy = find(BookCopy.class, copyId);
        if (!"AVAILABLE".equals(copy.getStatus())) {
            throw Errors.conflict("COPY_NOT_AVAILABLE", "This copy is not available");
        }
        long activeLoans = em.createQuery(
                        "select count(l) from BookLoan l where l.studentId = :studentId and l.status in ('BORROWED','OVERDUE')",
                        Long.class)
                .setParameter("studentId", studentId)
                .getSingleResult();
        if (activeLoans >= MAX_LOANS) {
            throw Errors.conflict("LOAN_LIMIT", "Student has reached the maximum number of loans");
        }
        BigDecimal fine = unpaidFines(studentId);
        if (fine.compareTo(BigDecimal.ZERO) > 0) {
            throw Errors.conflict("OUTSTANDING_FINE", "Clear outstanding fines before borrowing");
        }

        Instant now = Instant.now();
        BookLoan loan = new BookLoan();
        loan.setCopyId(copyId);
        loan.setBookId(copy.getBookId());
        loan.setStudentId(studentId);
        loan.setBorrowedAt(support.timestamp(now));
        loan.setDueAt(support.timestamp(now.plus(Duration.ofDays(LOAN_DAYS))));
        loan.setStatus("BORROWED");
        loan.setFineAmount(BigDecimal.ZERO);
        em.persist(loan);

        copy.setStatus("BORROWED");
        em.merge(copy);
        Book book = find(Book.class, copy.getBookId());
        book.setCopiesAvailable(Math.max(0, book.getCopiesAvailable() - 1));
        em.merge(book);

        support.audit("LIBRARY_CHECKOUT", "BOOK_LOANS", loan.getId(),
                Map.of("barcode", copy.getBarcode(), "studentId", studentId.toString()));
        Student student = support.student(studentId);
        support.notifyGuardian(
                student,
                "Book borrowed",
                "Book '" + book.getTitle() + "' is due on " + loan.getDueAt(),
                "book-loans",
                loan.getId());
        return loan;
    }

    public BookLoan returnLoan(UUID loanId, Map<String, Object> body) {
        BookLoan loan = find(BookLoan.class, loanId);
        if (loan.getReturnedAt() != null) {
            throw Errors.badRequest("ALREADY_RETURNED", "Loan was already returned");
        }
        Instant now = Instant.now();
        Instant due = loan.getDueAt() == null
                ? now
                : support.instant(loan.getDueAt(), now);
        BigDecimal fine = BigDecimal.ZERO;
        if (now.isAfter(due)) {
            long days = Math.max(1, Duration.between(due, now).toDays());
            fine = FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        }
        loan.setReturnedAt(support.timestamp(now));
        loan.setStatus("RETURNED");
        loan.setFineAmount(fine);
        em.merge(loan);

        BookCopy copy = find(BookCopy.class, loan.getCopyId());
        copy.setStatus("AVAILABLE");
        em.merge(copy);
        Book book = find(Book.class, loan.getBookId());
        book.setCopiesAvailable(book.getCopiesAvailable() + 1);
        em.merge(book);

        support.audit("LIBRARY_RETURN", "BOOK_LOANS", loanId, Map.of("fine", fine));
        Student student = support.student(loan.getStudentId());
        support.notifyGuardian(
                student,
                fine.compareTo(BigDecimal.ZERO) > 0 ? "Overdue book returned" : "Book returned",
                "Book return recorded. Fine: " + fine + " TZS.",
                "book-loans",
                loan.getId());
        return loan;
    }

    public BookLoan renew(UUID loanId) {
        BookLoan loan = find(BookLoan.class, loanId);
        if (!"BORROWED".equals(loan.getStatus())) {
            throw Errors.badRequest("NOT_BORROWED", "Only borrowed books can be renewed");
        }
        long reservations = em.createQuery(
                        "select count(r) from BookReservation r where r.bookId = :bookId and r.status = 'ACTIVE'",
                        Long.class)
                .setParameter("bookId", loan.getBookId())
                .getSingleResult();
        if (reservations > 0) {
            throw Errors.conflict("BOOK_RESERVED", "Another student has reserved this book");
        }
        Instant due = support.instant(loan.getDueAt(), Instant.now());
        loan.setDueAt(support.timestamp(due.plus(Duration.ofDays(RENEW_DAYS))));
        em.merge(loan);
        support.audit("LIBRARY_RENEW", "BOOK_LOANS", loanId, Map.of("dueAt", loan.getDueAt()));
        return loan;
    }

    public BookReservation reserve(Map<String, Object> body) {
        UUID bookId = support.uuid(body.get("bookId"));
        UUID studentId = support.uuid(body.get("studentId"));
        support.requireVisibleStudent(studentId);
        find(Book.class, bookId);
        BookReservation reservation = new BookReservation();
        reservation.setBookId(bookId);
        reservation.setStudentId(studentId);
        reservation.setReservedAt(support.now());
        reservation.setStatus("ACTIVE");
        em.persist(reservation);
        support.audit("LIBRARY_RESERVATION", "BOOK_RESERVATIONS", reservation.getId(),
                Map.of("bookId", bookId.toString()));
        return reservation;
    }

    public LabSession bookLab(Map<String, Object> body) {
        UUID studentId = support.uuid(body.get("studentId"));
        support.requireVisibleStudent(studentId);
        UUID bookId = support.uuid(body.get("bookId"));
        String lab = support.text(body.get("lab"));
        String seat = support.text(body.get("seat"));
        Instant start = support.instant(body.get("startAt"), Instant.now());
        Instant end = support.instant(body.get("endAt"), start.plus(Duration.ofHours(1)));
        if (!end.isAfter(start)) {
            throw Errors.badRequest("INVALID_TIME_RANGE", "Session end must be after start");
        }
        if (lab.isEmpty()) {
            throw Errors.badRequest("LAB_REQUIRED", "lab is required");
        }
        if (bookId != null) {
            Book book = find(Book.class, bookId);
            if (support.text(book.getDigitalUrl()).isEmpty()) {
                throw Errors.badRequest(
                        "NO_DIGITAL_ACCESS",
                        "This book has no digital copy for computer-lab use");
            }
        }
        List<LabSession> conflicts = em.createQuery(
                        """
                        select s from LabSession s
                        where s.status = 'BOOKED'
                          and ((:seat is not null and s.seat = :seat and s.lab = :lab)
                               or s.studentId = :studentId)
                          and s.startAt < :end and s.endAt > :start
                        """,
                        LabSession.class)
                .setParameter("seat", seat.isEmpty() ? null : seat)
                .setParameter("lab", lab)
                .setParameter("studentId", studentId)
                .setParameter("start", support.timestamp(start))
                .setParameter("end", support.timestamp(end))
                .getResultList();
        if (!conflicts.isEmpty()) {
            throw Errors.conflict("LAB_CONFLICT", "Lab seat or student is already booked");
        }

        LabSession session = new LabSession();
        session.setReference("LAB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        session.setStudentId(studentId);
        session.setBookId(bookId);
        session.setLab(lab);
        session.setSeat(seat.isEmpty() ? "OPEN" : seat);
        session.setStartAt(support.timestamp(start));
        session.setEndAt(support.timestamp(end));
        session.setPurpose(support.text(body.get("purpose")));
        session.setStatus("BOOKED");
        em.persist(session);
        support.audit("LIBRARY_LAB_BOOKING", "LAB_SESSIONS", session.getId(), Map.of("lab", lab));
        return session;
    }

    public int expireLoans() {
        String now = support.now();
        List<BookLoan> loans = em.createQuery(
                        "select l from BookLoan l where l.status = 'BORROWED' and l.dueAt < :now",
                        BookLoan.class)
                .setParameter("now", now)
                .getResultList();
        loans.forEach(loan -> {
            Instant due = support.instant(loan.getDueAt(), Instant.now());
            long days = Math.max(1, Duration.between(due, Instant.now()).toDays());
            loan.setStatus("OVERDUE");
            loan.setFineAmount(FINE_PER_DAY.multiply(BigDecimal.valueOf(days)));
            em.merge(loan);
            support.notifyGuardian(
                    support.student(loan.getStudentId()),
                    "Overdue library book",
                    "Book loan is overdue. Fine so far: " + loan.getFineAmount() + " TZS.",
                    "book-loans",
                    loan.getId());
        });
        return loans.size();
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<>();
        result.put("books", em.createQuery("select count(b) from Book b", Long.class).getSingleResult());
        result.put("loans", em.createQuery("select count(l) from BookLoan l", Long.class).getSingleResult());
        result.put("activeLoans", em.createQuery(
                        "select count(l) from BookLoan l where l.status in ('BORROWED','OVERDUE')",
                        Long.class)
                .getSingleResult());
        result.put("labSessions", em.createQuery("select count(s) from LabSession s", Long.class).getSingleResult());
        return result;
    }

    private BigDecimal unpaidFines(UUID studentId) {
        BigDecimal sum = em.createQuery(
                        "select sum(l.fineAmount) from BookLoan l where l.studentId = :studentId and l.status = 'OVERDUE'",
                        BigDecimal.class)
                .setParameter("studentId", studentId)
                .getSingleResult();
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private <T> T find(Class<T> type, UUID id) {
        T entity = id == null ? null : em.find(type, id);
        if (entity == null) {
            throw Errors.notFound(type.getSimpleName().toUpperCase() + "_NOT_FOUND", "Record not found");
        }
        return entity;
    }
}
