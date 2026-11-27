package org.example.school_wallet.academic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.skulify.pocketmoney.common.PocketMoneyHashes;
import org.example.school_wallet.common.tenant.TenantContext;
import org.example.school_wallet.config.SchoolBootstrapSeeder;
import org.example.school_wallet.operations.SchoolDocumentStorage;
import org.example.school_wallet.tenant.SchoolTenants;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Small development dataset so every module has records and reference
 * dropdowns work out of the box. Existing data is never overwritten.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AcademicDemoSeeder implements ApplicationRunner {

    /** Demo-only sensor secret. Production keys are registered through the API. */
    static final String DEMO_DEVICE_KEY = "SKULIFY-DEMO-SENSOR-KEY";

    private final CrudService crud;
    private final JdbcTemplate jdbc;
    private final SchoolDocumentStorage documents;

    public AcademicDemoSeeder(
            CrudService crud, JdbcTemplate jdbc, SchoolDocumentStorage documents) {
        this.crud = crud;
        this.jdbc = jdbc;
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.runAs(SchoolTenants.DEMO_SCHOOL_ID, this::seed);
    }

    private void seed() {
        Map<String, Object> student1 = seedOne("students", "studentCode",
                "DEMO-STU-001", student("DEMO-STU-001", "Asha", "Mwangi", "Form 1A", "PREFECT"));
        Map<String, Object> student2 = seedOne("students", "studentCode",
                "DEMO-STU-002", student("DEMO-STU-002", "Juma", "Ali", "Form 1A", "LIBRARY_PREFECT"));
        Map<String, Object> student3 = seedOne("students", "studentCode",
                "DEMO-STU-003", student("DEMO-STU-003", "Neema", "Kato", "Form 2B", "LAB_PREFECT"));
        seedPocketWallet(student1);
        seedPocketCredentials(student1);

        Map<String, Object> teacher1 = seedOne("teachers", "employeeCode",
                "DEMO-EMP-001", teacher("DEMO-EMP-001", "Mary", "Sharma", "Mathematics"));
        Map<String, Object> teacher2 = seedOne("teachers", "employeeCode",
                "DEMO-EMP-002", teacher("DEMO-EMP-002", "Peter", "Kamau", "English"));
        Map<String, Object> headmaster = seedOne("teachers", "employeeCode",
                "DEMO-EMP-003", staff("DEMO-EMP-003", "Edward", "Mkubwa", "Administration",
                        "ADMINISTRATIVE", "Head Master"));
        Map<String, Object> secondMaster = seedOne("teachers", "employeeCode",
                "DEMO-EMP-004", staff("DEMO-EMP-004", "Zawadi", "Mosi", "Administration",
                        "ADMINISTRATIVE", "Second Master"));
        Map<String, Object> academicMaster = seedOne("teachers", "employeeCode",
                "DEMO-EMP-005", staff("DEMO-EMP-005", "Sofia", "Alimi", "Curriculum",
                        "TEACHER", "Academic Master"));
        Map<String, Object> disciplineMaster = seedOne("teachers", "employeeCode",
                "DEMO-EMP-006", staff("DEMO-EMP-006", "Jumanne", "Adili", "Student Welfare",
                        "TEACHER", "Discipline Master"));
        Map<String, Object> hrManager = seedOne("teachers", "employeeCode",
                "DEMO-EMP-007", staff("DEMO-EMP-007", "Neema", "Rasilimali", "Human Resources",
                        "ADMINISTRATIVE", "School HR"));
        seedOne("teachers", "employeeCode",
                "DEMO-EMP-008", staff("DEMO-EMP-008", "Amani", "Hesabu", "Finance",
                        "ADMINISTRATIVE", "Accountant"));
        Map<String, Object> guard = seedOne("teachers", "employeeCode",
                "DEMO-EMP-009", staff("DEMO-EMP-009", "Hassan", "Walinzi", "Security",
                        "SECURITY_GUARD", "Walinzi"));
        seedOne("teachers", "employeeCode",
                "DEMO-EMP-010", staff("DEMO-EMP-010", "Asha", "Usafi", "Cleaning",
                        "CLEANER", "Wafanya Usafi"));

        Map<String, Object> class1 = seedOne("classes", "code",
                "DEMO-F1A", schoolClass("DEMO-F1A", "Form 1A", "Form 1", id(teacher1)));
        Map<String, Object> class2 = seedOne("classes", "code",
                "DEMO-F2B", schoolClass("DEMO-F2B", "Form 2B", "Form 2", id(teacher2)));

        Map<String, Object> subject1 = seedOne("subjects", "code",
                "DEMO-MATH", subject("DEMO-MATH", "Mathematics", "Science"));
        Map<String, Object> subject2 = seedOne("subjects", "code",
                "DEMO-ENG", subject("DEMO-ENG", "English", "Humanities"));
        Map<String, Object> subject3 = seedOne("subjects", "code",
                "DEMO-SCI", subject("DEMO-SCI", "Science", "Science"));

        Map<String, Object> year = seedFirst("academic-years",
                academicYear("2026/2027", "2026-01-01", "2026-12-31"));
        Map<String, Object> term = seedFirst("terms",
                term(id(year), "Term 1", 1, "2026-01-01", "2026-04-30", 16));

        seedOne("attendance", "remarks", "Demo attendance 1",
                attendance(id(class1), id(student1), "2026-01-01", "PRESENT", "08:05", "Demo attendance 1"));
        seedOne("attendance", "remarks", "Demo attendance 2",
                attendance(id(class1), id(student2), "2026-01-01", "LATE", "08:25", "Demo attendance 2"));
        seedOne("attendance", "remarks", "Demo attendance 3",
                attendance(id(class2), id(student3), "2026-01-01", "ABSENT", "", "Demo attendance 3"));

        seedOne("grades", "remarks", "Demo grade 1",
                grade(id(student1), id(subject1), "Mid-term", "TERM_1", 88, "A", "Demo grade 1"));
        seedOne("grades", "remarks", "Demo grade 2",
                grade(id(student2), id(subject2), "Mid-term", "TERM_1", 74, "B", "Demo grade 2"));
        seedOne("grades", "remarks", "Demo grade 3",
                grade(id(student3), id(subject3), "Final", "TERM_1", 91, "A", "Demo grade 3"));

        seedOne("fees", "reference", "DEMO-FEE-001",
                fee("DEMO-FEE-001", id(student1), "Tuition", 250000, 250000, "PAID",
                        "2026-12-15", "2026-10-02"));
        seedOne("fees", "reference", "DEMO-FEE-002",
                fee("DEMO-FEE-002", id(student2), "Tuition", 250000, 100000, "PARTIAL",
                        "2026-12-15", "2026-10-08"));
        seedOne("fees", "reference", "DEMO-FEE-003",
                fee("DEMO-FEE-003", id(student3), "Transport", 60000, 0, "PENDING",
                        "2026-12-15", ""));
        seedOne("fees", "reference", "DEMO-FEE-004",
                fee("DEMO-FEE-004", id(student1), "Activity", 120000, 120000, "PAID",
                        "2026-10-30", "2026-10-05"));
        seedOne("fees", "reference", "DEMO-FEE-005",
                fee("DEMO-FEE-005", id(student1), "Transport", 80000, 40000, "PARTIAL",
                        "2026-11-30", "2026-11-10"));
        seedOne("fees", "reference", "DEMO-FEE-006",
                fee("DEMO-FEE-006", id(student1), "Library", 30000, 0, "PENDING",
                        "2026-12-20", ""));

        seedOne("timetable", "room", "DEMO-A1",
                timetable(id(class1), id(subject1), id(teacher1), "MONDAY", "08:00", "09:00", "DEMO-A1"));
        seedOne("timetable", "room", "DEMO-B2",
                timetable(id(class2), id(subject2), id(teacher2), "TUESDAY", "10:00", "11:00", "DEMO-B2"));
        seedOne("timetable", "room", "DEMO-C3",
                timetable(id(class1), id(subject3), id(teacher1), "WEDNESDAY", "09:00", "10:00", "DEMO-C3"));

        seedOne("reports", "name", "Attendance summary",
                report("Attendance summary", "Attendance", "PDF", "ACTIVE"));
        seedOne("reports", "name", "Fee collection",
                report("Fee collection", "Finance", "XLSX", "ACTIVE"));

        seedOne("settings", "settingKey", "school.name",
                setting("school.name", "Skulify Academy", "GENERAL", "Display name used across the portal"));
        seedOne("settings", "settingKey", "academic.currentTerm",
                setting("academic.currentTerm", "TERM_1", "ACADEMIC", "Current academic term"));
        seedOne("settings", "settingKey", "fees.currency",
                setting("fees.currency", "TZS", "FINANCE", "Currency used for fee records"));
        seedOne("settings", "settingKey", "grading.bands",
                setting("grading.bands", "90:A+,80:A,70:B,60:C,50:D,40:E,0:F", "ACADEMIC",
                        "Minimum percentage and label for each grade band"));

        if (crud.list("enrolments", 0, 1, "").content().isEmpty()) {
            crud.create("enrolments", enrolment(id(student1), id(class1), id(year), "2026-01-10"));
            crud.create("enrolments", enrolment(id(student2), id(class1), id(year), "2026-01-10"));
            crud.create("enrolments", enrolment(id(student3), id(class2), id(year), "2026-01-10"));
        }
        if (crud.list("teacher-assignments", 0, 1, "").content().isEmpty()) {
            crud.create("teacher-assignments",
                    assignment(id(teacher1), id(class1), id(subject1), id(year), "CLASS_TEACHER"));
            crud.create("teacher-assignments",
                    assignment(id(teacher2), id(class2), id(subject2), id(year), "CLASS_TEACHER"));
        }

        Map<String, Object> reportCard = seedOne("report-cards", "reference", "DEMO-RC-001",
                reportCard("DEMO-RC-001", id(student1), id(class1), id(year), "TERM_1",
                        440, 500, 88, "A", 1, 3,
                        "Excellent progress across core subjects.", "PUBLISHED", "2026-11-20"));
        seedOne("parent-letters", "reference", "DEMO-PL-001",
                parentLetter("DEMO-PL-001", id(student1), id(reportCard), id(class1), "TERM_1",
                        "End of Term 1 progress letter",
                        "Dear Parent / Guardian,\n\nAsha has completed Term 1 with an average of 88% "
                                + "and secured position 1 in a class of 3. Attendance has been satisfactory "
                                + "and subject teachers report steady improvement.\n\nKeep encouraging "
                                + "regular reading and homework routines.\n\nWarm regards,\nSkulify Academy",
                        "1. Maintain at least 90% attendance next term.\n"
                                + "2. Submit all holiday assignments on the first day of Term 2.\n"
                                + "3. Settle the remaining Transport and Library balances before "
                                + "the new term begins.",
                        "GENERATED", "2026-11-21"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 1",
                staffAttendance(id(teacher1), "2026-11-26", "PRESENT", "07:45", "16:05",
                        "Teacher check-in/out", "Demo staff attendance 1"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 2",
                staffAttendance(id(teacher2), "2026-11-26", "PRESENT", "08:00", "16:15",
                        "Teacher check-in/out", "Demo staff attendance 2"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 3",
                staffAttendance(id(headmaster), "2026-11-26", "PRESENT", "07:30", "16:30",
                        "Head master check-in/out", "Demo staff attendance 3"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 4",
                staffAttendance(id(secondMaster), "2026-11-26", "PRESENT", "07:50", "16:20",
                        "Second master check-in/out", "Demo staff attendance 4"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 5",
                staffAttendance(id(academicMaster), "2026-11-26", "PRESENT", "07:55", "16:25",
                        "Academic master check-in/out", "Demo staff attendance 5"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 6",
                staffAttendance(id(disciplineMaster), "2026-11-26", "PRESENT", "08:10", "16:35",
                        "Discipline master check-in/out", "Demo staff attendance 6"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 7",
                staffAttendance(id(hrManager), "2026-11-26", "PRESENT", "07:40", "16:40",
                        "HR check-in/out", "Demo staff attendance 7"));
        seedOne("staff-attendance", "remarks", "Demo staff attendance 8",
                staffAttendance(id(guard), "2026-11-26", "PRESENT", "06:30", "18:00",
                        "Security check-in/out", "Demo staff attendance 8"));

        seedDocument("DOC-DEMO-001", "School Rules and Regulations", "POLICY",
                "Approved code of conduct for students and staff.",
                "School-Rules.txt", "text/plain", "2026-01-15",
                "SKULIFY ACADEMY - SCHOOL RULES\n\n"
                        + "1. Arrive before 08:00 and sign the attendance register.\n"
                        + "2. Wear the full school uniform every day.\n"
                        + "3. Respect teachers, visitors and fellow students.\n"
                        + "4. Keep classrooms and shared spaces clean.\n"
                        + "5. Report illness or emergencies to the class teacher immediately.\n");
        seedDocument("DOC-DEMO-002", "Staff Attendance Policy", "HR",
                "Check-in, check-out and notification rules for all employees.",
                "Staff-Attendance-Policy.txt", "text/plain", "2026-02-01",
                "SKULIFY ACADEMY - STAFF ATTENDANCE POLICY\n\n"
                        + "Teachers and employees must check in on arrival and check out before leaving.\n"
                        + "Each check-in and check-out creates an in-app notification for the "
                        + "HR Manager, Head Master and school administrator.\n"
                        + "Staff categories covered: Teachers, Walinzi (security) and Wafanya Usafi "
                        + "(cleaning).\n");
        seedDocument("DOC-DEMO-003", "Term 1 Academic Calendar", "ACADEMIC",
                "Teaching weeks, examinations and reporting dates for Term 1.",
                "Term-1-Calendar.txt", "text/plain", "2026-03-10",
                "SKULIFY ACADEMY - TERM 1 ACADEMIC CALENDAR\n\n"
                        + "Term 1: 2026-01-01 to 2026-04-30\n"
                        + "Mid-term assessment: 2026-02-20\n"
                        + "End-of-term examinations: 2026-04-15\n"
                        + "Report cards issued: 2026-04-30\n");

        Map<String, Object> studentCard = seedOne("pocket-cards", "cardUid",
                "SKUL-NFC-0001", pocketCard("SKUL-NFC-0001", id(student1), 188500,
                        80000, 250000, 600000));
        Map<String, Object> canteenTerminal = seedOne("store-terminals", "code",
                "POS-CANTEEN-01", storeTerminal("POS-CANTEEN-01", "Canteen sensor",
                        "Main canteen", "2026-11-26T12:40:00Z"));
        Map<String, Object> libraryTerminal = seedOne("store-terminals", "code",
                "POS-LIBRARY-01", storeTerminal("POS-LIBRARY-01", "Library sensor",
                        "School library", "2026-11-26T11:05:00Z"));
        Map<String, Object> uniformTerminal = seedOne("store-terminals", "code",
                "POS-UNIFORM-01", storeTerminal("POS-UNIFORM-01", "Uniform desk sensor",
                        "Student services", "2026-11-25T15:20:00Z"));

        Map<String, Object> itemMandazi = seedOne("store-items", "sku",
                "SKU-SNACK-001", storeItem("SKU-SNACK-001", "Mandazi and chai",
                        "SNACKS", 1500, 80, 20, "Canteen"));
        Map<String, Object> itemBusToken = seedOne("store-items", "sku",
                "SKU-TRANSPORT-001", storeItem("SKU-TRANSPORT-001", "School bus token",
                        "TRANSPORT", 3500, 40, 10, "Transport desk"));
        Map<String, Object> itemExerciseBook = seedOne("store-items", "sku",
                "SKU-BOOK-001", storeItem("SKU-BOOK-001", "Exercise book",
                        "BOOKS", 3000, 120, 30, "Library"));
        Map<String, Object> itemJuice = seedOne("store-items", "sku",
                "SKU-SNACK-002", storeItem("SKU-SNACK-002", "Fruit juice",
                        "SNACKS", 1700, 60, 15, "Canteen"));
        Map<String, Object> itemSocks = seedOne("store-items", "sku",
                "SKU-UNIFORM-001", storeItem("SKU-UNIFORM-001", "School socks",
                        "UNIFORM", 6000, 35, 10, "Student services"));
        Map<String, Object> itemLunch = seedOne("store-items", "sku",
                "SKU-SNACK-003", storeItem("SKU-SNACK-003", "Lunch snack pack",
                        "SNACKS", 5500, 45, 12, "Canteen"));

        String cardId = id(studentCard);
        String studentId = id(student1);
        seedOne("pocket-transactions", "reference", "PTX-DEMO-001",
                pocketTransaction("PTX-DEMO-001", cardId, studentId, "TOPUP", 200000,
                        "TOPUP", 200000, null, null, 1, "MPESA-DEMO-001",
                        "Parent top-up via M-PESA"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-002",
                pocketTransaction("PTX-DEMO-002", cardId, studentId, "PURCHASE", 12000,
                        "SNACKS", 188000, id(canteenTerminal), id(itemMandazi), 8,
                        "POS-CANTEEN-01-DEMO-002", "Sensor purchase: Mandazi and chai"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-003",
                pocketTransaction("PTX-DEMO-003", cardId, studentId, "PURCHASE", 3500,
                        "TRANSPORT", 184500, id(canteenTerminal), id(itemBusToken), 1,
                        "POS-CANTEEN-01-DEMO-003", "Sensor purchase: School bus token"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-004",
                pocketTransaction("PTX-DEMO-004", cardId, studentId, "PURCHASE", 18000,
                        "BOOKS", 166500, id(libraryTerminal), id(itemExerciseBook), 6,
                        "POS-LIBRARY-01-DEMO-004", "Sensor purchase: Exercise book"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-005",
                pocketTransaction("PTX-DEMO-005", cardId, studentId, "REFUND", 3500,
                        "TRANSPORT", 170000, id(canteenTerminal), id(itemBusToken), 1,
                        "REFUND-PTX-DEMO-003", "Refund: bus token not used"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-006",
                pocketTransaction("PTX-DEMO-006", cardId, studentId, "PURCHASE", 8500,
                        "SNACKS", 161500, id(canteenTerminal), id(itemJuice), 5,
                        "POS-CANTEEN-01-DEMO-006", "Sensor purchase: Fruit juice"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-007",
                pocketTransaction("PTX-DEMO-007", cardId, studentId, "PURCHASE", 12000,
                        "UNIFORM", 149500, id(uniformTerminal), id(itemSocks), 2,
                        "POS-UNIFORM-01-DEMO-007", "Sensor purchase: School socks"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-008",
                pocketTransaction("PTX-DEMO-008", cardId, studentId, "TOPUP", 50000,
                        "TOPUP", 199500, null, null, 1, "MPESA-DEMO-002",
                        "Parent top-up via bank transfer"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-009",
                pocketTransaction("PTX-DEMO-009", cardId, studentId, "PURCHASE", 11000,
                        "SNACKS", 188500, id(canteenTerminal), id(itemLunch), 2,
                        "POS-CANTEEN-01-DEMO-009", "Sensor purchase: Lunch snack pack"));
        seedOne("pocket-transactions", "reference", "PTX-DEMO-010",
                pocketTransaction("PTX-DEMO-010", cardId, studentId, "TOPUP", 30000,
                        "TOPUP", 188500, null, null, 1, "USSD-DEMO-PENDING",
                        "Mobile money USSD push awaiting parent approval", "PENDING"));

        seedOne("cash-requests", "reason", "Demo cash request - school trip fare",
                cashRequest(studentId, id(teacher1), 15000, "Demo cash request - school trip fare",
                        "PENDING", "2026-11-26T08:15:00Z", "", "CASH-DEMO-PEND1", "",
                        "0712345678", ""));
        seedOne("cash-requests", "reason", "Demo cash request - stationery",
                cashRequest(studentId, id(teacher1), 12000, "Demo cash request - stationery",
                        "APPROVED", "2026-11-25T10:05:00Z", "2026-11-25T10:08:00Z",
                        "PAY-DEMO-APPR1", "", "0712345678", "Approved by guardian"));
        seedOne("cash-requests", "reason", "Demo cash request - library fine",
                cashRequest(studentId, id(teacher1), 5000, "Demo cash request - library fine",
                        "PAID", "2026-11-24T09:30:00Z", "2026-11-24T09:33:00Z",
                        "PAY-DEMO-PAID1", "AGENT-DAR-01", "0712345678",
                        "Cash collected at agent"));

        seedOne("spending-limits", "category", "SNACKS",
                categoryLimit(cardId, studentId, "SNACKS", 25000, 100000, 300000));
        seedOne("spending-limits", "category", "BOOKS",
                categoryLimit(cardId, studentId, "BOOKS", 30000, 80000, 200000));
        linkDemoUsers();
    }

    private Map<String, Object> seedOne(
            String resource, String matchKey, Object matchValue, Map<String, Object> body) {
        String expected = String.valueOf(matchValue);
        return crud.list(resource, 0, 100, "").content().stream()
                .filter(row -> Objects.equals(String.valueOf(row.get(matchKey)), expected))
                .findFirst()
                .orElseGet(() -> crud.create(resource, body));
    }

    private Map<String, Object> seedFirst(String resource, Map<String, Object> body) {
        return crud.list(resource, 0, 1, "").content().stream()
                .findFirst()
                .orElseGet(() -> crud.create(resource, body));
    }

    /** Gives the demo student a ledger-backed wallet so the Pocket Money API is usable. */
    private void seedPocketWallet(Map<String, Object> student) {
        UUID studentId = UUID.fromString(id(student));
        UUID walletId = UUID.nameUUIDFromBytes(
                ("WALLET|" + studentId).getBytes(StandardCharsets.UTF_8));
        jdbc.update(
                """
                insert into pm_wallets
                    (id, tenant_id, student_id, account_code, currency, balance, status,
                     daily_limit, weekly_limit, monthly_limit, per_txn_limit, version,
                     created_at, updated_at)
                select ?, ?, ?, 'STUDENT_WALLET', 'TZS', 0, 'ACTIVE',
                       100000, 300000, 1000000, 50000, 0, current_timestamp, current_timestamp
                where not exists (select 1 from pm_wallets where student_id = ?)
                """,
                walletId,
                SchoolTenants.DEMO_SCHOOL_ID,
                studentId,
                studentId);
    }

    /** Seeds the ledger-backed card, merchant and sensor used by demo purchases. */
    private void seedPocketCredentials(Map<String, Object> student) {
        UUID studentId = UUID.fromString(id(student));
        UUID walletId = PocketMoneyHashes.deterministicId("WALLET", studentId.toString());
        UUID merchantId = PocketMoneyHashes.deterministicId("MERCHANT", "DEMO-CANTEEN");
        UUID cardId = PocketMoneyHashes.deterministicId("CARD", "SKUL-NFC-0001");
        UUID terminalId = PocketMoneyHashes.deterministicId("TERMINAL", "POS-CANTEEN-01");

        jdbc.update(
                """
                insert into pm_merchants
                    (id, tenant_id, code, name, category, status, settlement_account_code,
                     created_at, updated_at)
                select ?, ?, 'DEMO-CANTEEN', 'Demo school canteen', 'SNACKS', 'ACTIVE',
                       'SHOP_SETTLEMENT', current_timestamp, current_timestamp
                where not exists (select 1 from pm_merchants where id = ?)
                """,
                merchantId, SchoolTenants.DEMO_SCHOOL_ID, merchantId);

        jdbc.update(
                """
                insert into pm_cards
                    (id, tenant_id, wallet_id, uid_hash, uid_label, status, issued_at,
                     created_at, updated_at)
                select ?, ?, ?, ?, 'SKUL-NFC-0001', 'ACTIVE', current_timestamp,
                       current_timestamp, current_timestamp
                where not exists (select 1 from pm_cards where id = ?)
                """,
                cardId,
                SchoolTenants.DEMO_SCHOOL_ID,
                walletId,
                PocketMoneyHashes.identifier("SKUL-NFC-0001"),
                cardId);

        jdbc.update(
                """
                insert into pm_terminals
                    (id, tenant_id, merchant_id, code, device_key_hash, status,
                     created_at, updated_at)
                select ?, ?, ?, 'POS-CANTEEN-01', ?, 'ACTIVE', current_timestamp, current_timestamp
                where not exists (select 1 from pm_terminals where id = ?)
                """,
                terminalId,
                SchoolTenants.DEMO_SCHOOL_ID,
                merchantId,
                PocketMoneyHashes.identifier(DEMO_DEVICE_KEY),
                terminalId);
    }

    private String id(Map<String, Object> record) {
        return record.containsKey("id") ? String.valueOf(record.get("id")) : "";
    }

    private Map<String, Object> pocketCard(
            String cardUid, String studentId, int balance,
            int dailyLimit, int weeklyLimit, int monthlyLimit) {
        return Map.ofEntries(
                Map.entry("cardUid", cardUid),
                Map.entry("studentId", UUID.fromString(studentId)),
                Map.entry("balance", BigDecimal.valueOf(balance)),
                Map.entry("dailyLimit", BigDecimal.valueOf(dailyLimit)),
                Map.entry("weeklyLimit", BigDecimal.valueOf(weeklyLimit)),
                Map.entry("monthlyLimit", BigDecimal.valueOf(monthlyLimit)),
                Map.entry("status", "ACTIVE"),
                Map.entry("issuedAt", "2026-11-01T08:00:00Z"));
    }

    private Map<String, Object> storeTerminal(
            String code, String name, String location, String lastSeenAt) {
        return Map.of(
                "code", code,
                "name", name,
                "location", location,
                "status", "ACTIVE",
                "lastSeenAt", lastSeenAt);
    }

    private Map<String, Object> storeItem(
            String sku, String name, String category, int price,
            int stockQuantity, int reorderLevel, String location) {
        return Map.ofEntries(
                Map.entry("sku", sku),
                Map.entry("name", name),
                Map.entry("category", category),
                Map.entry("price", BigDecimal.valueOf(price)),
                Map.entry("stockQuantity", stockQuantity),
                Map.entry("reorderLevel", reorderLevel),
                Map.entry("unit", "PIECE"),
                Map.entry("location", location),
                Map.entry("active", true));
    }

    private Map<String, Object> pocketTransaction(
            String reference, String cardId, String studentId, String type, int amount,
            String category, int balanceAfter, String terminalId, String itemId, int quantity,
            String providerReference, String description) {
        return pocketTransaction(reference, cardId, studentId, type, amount, category,
                balanceAfter, terminalId, itemId, quantity, providerReference, description, "POSTED");
    }

    private Map<String, Object> pocketTransaction(
            String reference, String cardId, String studentId, String type, int amount,
            String category, int balanceAfter, String terminalId, String itemId, int quantity,
            String providerReference, String description, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("reference", reference);
        data.put("cardId", UUID.fromString(cardId));
        data.put("studentId", UUID.fromString(studentId));
        data.put("type", type);
        data.put("amount", BigDecimal.valueOf(amount));
        data.put("category", category);
        data.put("balanceAfter", BigDecimal.valueOf(balanceAfter));
        if (terminalId != null && !terminalId.isBlank()) {
            data.put("terminalId", UUID.fromString(terminalId));
        }
        if (itemId != null && !itemId.isBlank()) {
            data.put("storeItemId", UUID.fromString(itemId));
        }
        data.put("quantity", quantity);
        data.put("providerReference", providerReference);
        data.put("status", status);
        data.put("description", description);
        return data;
    }

    private Map<String, Object> cashRequest(
            String studentId,
            String teacherId,
            int amount,
            String reason,
            String status,
            String requestedAt,
            String decidedAt,
            String payoutReference,
            String agentCode,
            String phoneNumber,
            String note) {
        return Map.ofEntries(
                Map.entry("studentId", UUID.fromString(studentId)),
                Map.entry("teacherId", UUID.fromString(teacherId)),
                Map.entry("amount", BigDecimal.valueOf(amount)),
                Map.entry("reason", reason),
                Map.entry("status", status),
                Map.entry("requestedAt", requestedAt),
                Map.entry("decidedAt", decidedAt),
                Map.entry("payoutReference", payoutReference),
                Map.entry("agentCode", agentCode),
                Map.entry("phoneNumber", phoneNumber),
                Map.entry("note", note));
    }

    private Map<String, Object> categoryLimit(
            String cardId, String studentId, String category,
            int dailyLimit, int weeklyLimit, int monthlyLimit) {
        return Map.ofEntries(
                Map.entry("cardId", UUID.fromString(cardId)),
                Map.entry("studentId", UUID.fromString(studentId)),
                Map.entry("category", category),
                Map.entry("dailyLimit", BigDecimal.valueOf(dailyLimit)),
                Map.entry("weeklyLimit", BigDecimal.valueOf(weeklyLimit)),
                Map.entry("monthlyLimit", BigDecimal.valueOf(monthlyLimit)),
                Map.entry("active", true));
    }

    private Map<String, Object> staffAttendance(
            String staffId,
            String date,
            String status,
            String checkIn,
            String checkOut,
            String recordedBy,
            String remarks) {
        return Map.of(
                "staffId", staffId,
                "attendanceDate", date,
                "status", status,
                "checkIn", checkIn,
                "checkOut", checkOut,
                "recordedBy", recordedBy,
                "remarks", remarks);
    }

    /** Creates the metadata row and its demo file so downloads work immediately. */
    private void seedDocument(
            String reference,
            String title,
            String category,
            String description,
            String fileName,
            String mimeType,
            String documentDate,
            String content) {
        Map<String, Object> document = seedOne("school-documents", "reference", reference,
                Map.ofEntries(
                        Map.entry("reference", reference),
                        Map.entry("title", title),
                        Map.entry("category", category),
                        Map.entry("description", description),
                        Map.entry("fileName", fileName),
                        Map.entry("filePath", "pending"),
                        Map.entry("mimeType", mimeType),
                        Map.entry("fileSize", 0L),
                        Map.entry("documentDate", documentDate),
                        Map.entry("uploadedById", SchoolBootstrapSeeder.SCHOOL_ADMIN_ID),
                        Map.entry("status", "ACTIVE")));

        String documentId = id(document);
        String relativePath = SchoolTenants.DEMO_SCHOOL_ID + "/" + documentId + "-"
                + fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = documents.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            long size = Files.size(target);
            Object storedSize = document.get("fileSize");
            long previousSize = storedSize instanceof Number number ? number.longValue() : 0L;
            if (!relativePath.equals(String.valueOf(document.get("filePath")))
                    || size != previousSize) {
                crud.update("school-documents", UUID.fromString(documentId),
                        Map.of("filePath", relativePath, "fileSize", size));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to seed demo document " + reference, ex);
        }
    }

    private void linkDemoUsers() {
        jdbc.update(
                """
                update students
                   set user_id = ?, guardian_user_id = ?
                 where tenant_id = ? and student_code = 'DEMO-STU-001'
                """,
                SchoolBootstrapSeeder.STUDENT_USER_ID,
                SchoolBootstrapSeeder.PARENT_USER_ID,
                SchoolTenants.DEMO_SCHOOL_ID);
        linkTeacherUser("DEMO-EMP-001", SchoolBootstrapSeeder.TEACHER_USER_ID);
        linkTeacherUser("DEMO-EMP-002", SchoolBootstrapSeeder.CLASS_TEACHER_USER_ID);
        linkTeacherUser("DEMO-EMP-003", SchoolBootstrapSeeder.HEAD_MASTER_USER_ID);
        linkTeacherUser("DEMO-EMP-004", SchoolBootstrapSeeder.SECOND_MASTER_USER_ID);
        linkTeacherUser("DEMO-EMP-005", SchoolBootstrapSeeder.ACADEMIC_MASTER_USER_ID);
        linkTeacherUser("DEMO-EMP-006", SchoolBootstrapSeeder.DISCIPLINE_MASTER_USER_ID);
        linkTeacherUser("DEMO-EMP-007", SchoolBootstrapSeeder.HR_MANAGER_USER_ID);
        linkTeacherUser("DEMO-EMP-008", SchoolBootstrapSeeder.ACCOUNTANT_USER_ID);
    }

    private void linkTeacherUser(String employeeCode, UUID userId) {
        jdbc.update(
                """
                update teachers
                   set user_id = ?
                 where tenant_id = ? and employee_code = ?
                """,
                userId,
                SchoolTenants.DEMO_SCHOOL_ID,
                employeeCode);
    }

    private Map<String, Object> student(String code, String first, String last, String className) {
        return student(code, first, last, className, "NONE");
    }

    private Map<String, Object> student(
            String code, String first, String last, String className, String position) {
        return Map.of(
                "studentCode", code,
                "firstName", first,
                "lastName", last,
                "email", first.toLowerCase() + "." + last.toLowerCase() + "@skulify.test",
                "className", className,
                "guardianName", "Parent / Guardian",
                "studentPosition", position,
                "status", "ACTIVE");
    }

    private Map<String, Object> teacher(String code, String first, String last, String speciality) {
        return staff(code, first, last, speciality, "TEACHER", "Teacher");
    }

    private Map<String, Object> staff(
            String code,
            String first,
            String last,
            String speciality,
            String employeeType,
            String position) {
        return Map.of(
                "employeeCode", code,
                "firstName", first,
                "lastName", last,
                "email", first.toLowerCase() + "." + last.toLowerCase() + "@skulify.test",
                "specialization", speciality,
                "employmentType", "FULL_TIME",
                "employeeType", employeeType,
                "position", position,
                "status", "ACTIVE");
    }

    private Map<String, Object> schoolClass(String code, String name, String grade, String teacherId) {
        return Map.of(
                "code", code,
                "name", name,
                "gradeLevel", grade,
                "room", code.replace("DEMO-", ""),
                "capacity", 40,
                "classTeacherId", teacherId,
                "status", "ACTIVE");
    }

    private Map<String, Object> subject(String code, String name, String department) {
        return Map.of(
                "code", code,
                "name", name,
                "department", department,
                "credits", 1,
                "status", "ACTIVE");
    }

    private Map<String, Object> attendance(
            String classId,
            String studentId,
            String date,
            String status,
            String checkIn,
            String remarks) {
        return Map.of(
                "classId", classId,
                "studentId", studentId,
                "attendanceDate", date,
                "status", status,
                "checkIn", checkIn,
                "recordedBy", "Demo seed",
                "remarks", remarks);
    }

    private Map<String, Object> grade(
            String studentId,
            String subjectId,
            String examType,
            String term,
            double score,
            String grade,
            String remarks) {
        return Map.of(
                "studentId", studentId,
                "subjectId", subjectId,
                "examType", examType,
                "term", term,
                "score", score,
                "maxScore", 100,
                "grade", grade,
                "remarks", remarks);
    }

    private Map<String, Object> fee(
            String reference, String studentId, String feeType, int amount, int paid,
            String status, String dueDate, String paidDate) {
        return Map.of(
                "reference", reference,
                "studentId", studentId,
                "feeType", feeType,
                "amount", BigDecimal.valueOf(amount),
                "paidAmount", BigDecimal.valueOf(paid),
                "dueDate", dueDate,
                "paidDate", paidDate,
                "status", status);
    }

    private Map<String, Object> reportCard(
            String reference, String studentId, String classId, String academicYearId,
            String term, int totalScore, int maxScore, double average, String grade,
            int position, int classSize, String remarks, String status, String publishedAt) {
        return Map.ofEntries(
                Map.entry("reference", reference),
                Map.entry("studentId", studentId),
                Map.entry("classId", classId),
                Map.entry("academicYearId", academicYearId),
                Map.entry("term", term),
                Map.entry("totalScore", BigDecimal.valueOf(totalScore)),
                Map.entry("maxScore", BigDecimal.valueOf(maxScore)),
                Map.entry("average", BigDecimal.valueOf(average)),
                Map.entry("grade", grade),
                Map.entry("position", position),
                Map.entry("classSize", classSize),
                Map.entry("remarks", remarks),
                Map.entry("status", status),
                Map.entry("publishedAt", publishedAt));
    }

    private Map<String, Object> parentLetter(
            String reference, String studentId, String reportCardId, String classId,
            String term, String title, String content, String requirements,
            String status, String generatedAt) {
        return Map.of(
                "reference", reference,
                "studentId", studentId,
                "reportCardId", reportCardId,
                "classId", classId,
                "term", term,
                "title", title,
                "content", content,
                "requirements", requirements,
                "status", status,
                "generatedAt", generatedAt);
    }

    private Map<String, Object> timetable(
            String classId,
            String subjectId,
            String teacherId,
            String day,
            String start,
            String end,
            String room) {
        return Map.of(
                "classId", classId,
                "subjectId", subjectId,
                "teacherId", teacherId,
                "dayOfWeek", day,
                "startTime", start,
                "endTime", end,
                "room", room);
    }

    private Map<String, Object> report(String name, String type, String format, String status) {
        return Map.of(
                "name", name,
                "reportType", type,
                "format", format,
                "status", status);
    }

    private Map<String, Object> setting(String key, String value, String category, String description) {
        return Map.of(
                "settingKey", key,
                "settingValue", value,
                "category", category,
                "description", description,
                "active", true);
    }

    private Map<String, Object> academicYear(String name, String start, String end) {
        return Map.of(
                "name", name,
                "startDate", start,
                "endDate", end,
                "current", true,
                "status", "ACTIVE");
    }

    private Map<String, Object> term(
            String yearId, String name, int order, String start, String end, int numberOfWeeks) {
        return Map.of(
                "academicYearId", yearId,
                "name", name,
                "termOrder", order,
                "startDate", start,
                "endDate", end,
                "numberOfWeeks", numberOfWeeks,
                "status", "ACTIVE");
    }

    private Map<String, Object> enrolment(
            String studentId, String classId, String yearId, String enrolmentDate) {
        return Map.of(
                "studentId", studentId,
                "classId", classId,
                "academicYearId", yearId,
                "enrolmentDate", enrolmentDate,
                "status", "ACTIVE");
    }

    private Map<String, Object> assignment(
            String teacherId, String classId, String subjectId, String yearId, String scope) {
        return Map.of(
                "teacherId", teacherId,
                "classId", classId,
                "subjectId", subjectId,
                "academicYearId", yearId,
                "scope", scope,
                "status", "ACTIVE");
    }
}
