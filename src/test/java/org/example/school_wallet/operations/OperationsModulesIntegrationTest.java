package org.example.school_wallet.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.school_wallet.academic.FeeRecord;
import org.example.school_wallet.academic.FeeRecordRepository;
import org.example.school_wallet.academic.Student;
import org.example.school_wallet.academic.StudentRepository;
import org.example.school_wallet.common.error.ApiException;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.security.AuthenticatedUser;
import org.example.school_wallet.tenant.SchoolTenants;
import org.example.school_wallet.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end coverage for the new fee, pocket money, library and store flows. */
@SpringBootTest
@Transactional
class OperationsModulesIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private StudentRepository students;
    @Autowired private FeeRecordRepository feeRecords;
    @Autowired private FeeManagementService feeService;
    @Autowired private PocketMoneyService pocketService;
    @Autowired private LibraryService libraryService;
    @Autowired private SchoolOperationsService operationsService;

    private final UUID tenantId = SchoolTenants.DEMO_SCHOOL_ID;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), tenantId, "admin", "Test Admin",
                Set.of(Role.TENANT_ADMIN), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, Set.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void feeInvoiceAndPaymentKeepLedgersInSync() {
        Student student = student();
        FeePlan plan = feePlan();

        Invoice invoice = feeService.issueInvoice(Map.of(
                "feePlanId", plan.getId().toString(),
                "studentId", student.getId().toString(),
                "invoiceNo", "INV-" + suffix(),
                "totalAmount", "100000"));

        FeeRecord fee = em.createQuery(
                        "select f from FeeRecord f where f.reference = :ref", FeeRecord.class)
                .setParameter("ref", invoice.getInvoiceNo())
                .getSingleResult();

        feeService.recordPayment(fee.getId(), Map.of(
                "amount", "40000", "method", "CASH"));
        em.flush();
        em.clear();

        assertEquals("PARTIAL", em.find(Invoice.class, invoice.getId()).getStatus().name());
        assertEquals(0, new BigDecimal("60000").compareTo(
                em.find(FeeRecord.class, fee.getId()).getAmount()
                        .subtract(em.find(FeeRecord.class, fee.getId()).getPaidAmount())));

        feeService.recordPayment(fee.getId(), Map.of(
                "amount", "60000", "method", "CASH"));
        em.flush();
        em.clear();
        assertEquals("PAID", em.find(Invoice.class, invoice.getId()).getStatus().name());
    }

    @Test
    void parentTopUpLimitsAndPosPurchaseUseOneCardLedger() {
        Student student = student();
        PocketCard card = pocketService.issueCard(Map.of(
                "studentId", student.getId().toString(),
                "cardUid", "CARD-" + suffix(),
                "dailyLimit", "10000",
                "weeklyLimit", "50000"));
        pocketService.topUp(card.getId(), Map.of(
                "amount", "20000",
                "providerReference", "TOPUP-" + suffix()));

        StoreItem item = storeItem("SNACKS", new BigDecimal("1500"), 10);
        StoreTerminal terminal = storeTerminal();

        PocketTransaction purchase = pocketService.charge(Map.of(
                "cardUid", card.getCardUid(),
                "terminalCode", terminal.getCode(),
                "storeItemId", item.getId().toString(),
                "quantity", "2",
                "providerReference", "SALE-" + suffix()));

        assertEquals(new BigDecimal("3000"), purchase.getAmount());
        assertEquals(0, new BigDecimal("17000").compareTo(purchase.getBalanceAfter()));
        assertEquals(8, em.find(StoreItem.class, item.getId()).getStockQuantity());

        pocketService.setLimits(card.getId(), Map.of(
                "category", "SNACKS",
                "dailyLimit", "1000"));
        ApiException error = assertThrows(ApiException.class, () ->
                pocketService.charge(Map.of(
                        "cardUid", card.getCardUid(),
                        "storeItemId", item.getId().toString(),
                        "quantity", "1")));
        assertEquals("SPENDING_LIMIT_EXCEEDED", error.getCode());
    }

    @Test
    void libraryCheckoutReservesCopyAndLabSessionKeepsDigitalAccess() {
        Student student = student();
        Book book = new Book();
        book.setIsbn("ISBN-" + suffix());
        book.setTitle("Digital Study Book");
        book.setCopiesTotal(2);
        book.setCopiesAvailable(2);
        book.setDigitalUrl("https://library.example.test/book");
        em.persist(book);

        BookCopy copy = new BookCopy();
        copy.setBarcode("BC-" + suffix());
        copy.setBookId(book.getId());
        copy.setStatus("AVAILABLE");
        em.persist(copy);
        em.flush();

        BookLoan loan = libraryService.checkout(Map.of(
                "studentId", student.getId().toString(),
                "copyId", copy.getId().toString()));
        assertEquals("BORROWED", loan.getStatus());
        assertEquals("BORROWED", em.find(BookCopy.class, copy.getId()).getStatus());

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        LabSession session = libraryService.bookLab(Map.of(
                "studentId", student.getId().toString(),
                "bookId", book.getId().toString(),
                "lab", "LAB-A",
                "seat", "7",
                "startAt", start.toString(),
                "endAt", start.plus(1, ChronoUnit.HOURS).toString()));

        assertNotNull(session.getReference());
        assertEquals("BOOKED", session.getStatus());
    }

    @Test
    void lowStockAutomaticallyCreatesPurchaseRequestAndTask() {
        String sku = "INV-" + suffix();
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setName("Chalk boxes");
        item.setQuantity(2);
        item.setReorderLevel(5);
        em.persist(item);
        em.flush();

        int created = operationsService.generateReorderTasks();
        assertTrue(created >= 1);

        PurchaseRequest request = em.createQuery(
                        "select r from PurchaseRequest r where r.itemId = :itemId", PurchaseRequest.class)
                .setParameter("itemId", item.getId())
                .getResultStream()
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", request.getStatus().name());

        SchoolTask task = em.createQuery(
                        "select t from SchoolTask t where t.sourceId = :sourceId", SchoolTask.class)
                .setParameter("sourceId", request.getId())
                .getResultStream()
                .findFirst()
                .orElseThrow();
        assertEquals("HIGH", task.getPriority());
    }

    private Student student() {
        Student student = new Student();
        student.setStudentCode("OPS-" + suffix());
        student.setFirstName("Test");
        student.setLastName("Student");
        student.setGuardianPhone("+255700000099");
        student.setStatus(Student.Status.ACTIVE);
        return students.saveAndFlush(student);
    }

    private FeePlan feePlan() {
        FeePlan plan = new FeePlan();
        plan.setCode("PLAN-" + suffix());
        plan.setName("Tuition");
        plan.setAmount(new BigDecimal("100000"));
        plan.setFrequency("TERMLY");
        em.persist(plan);
        em.flush();
        return plan;
    }

    private StoreItem storeItem(String category, BigDecimal price, int stock) {
        StoreItem item = new StoreItem();
        item.setSku("SKU-" + suffix());
        item.setName("School store item");
        item.setCategory(category);
        item.setPrice(price);
        item.setStockQuantity(stock);
        item.setReorderLevel(2);
        em.persist(item);
        em.flush();
        return item;
    }

    private StoreTerminal storeTerminal() {
        StoreTerminal terminal = new StoreTerminal();
        terminal.setCode("POS-" + suffix());
        terminal.setName("Test terminal");
        terminal.setStatus(StoreTerminal.Status.ACTIVE);
        em.persist(terminal);
        em.flush();
        return terminal;
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
