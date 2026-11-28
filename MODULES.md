# Skulify — Module Reference

This document describes the fee, pocket-money, store, library, lab, school-operations, academic-automation, parent-portal, and online-testing modules added on top of the existing generic CRUD platform.

## Design principles

- The generic `CrudService` / `CrudController` and `/api/v1/{resource}` routes are unchanged in shape. New entities are registered in `CrudService.TYPES` and get index/create/edit pages automatically from `RESOURCE_CONFIGS`.
- Domain actions that need invariants (top-ups, POS charges, fee payments, loans, approvals) live in dedicated services and controllers alongside CRUD. They are **not** implemented as generic ledger writes.
- `ResourceAccessPolicy` is the single source of truth for role permissions. Row scope for parents/students comes from `StudentLinked` entities, `CrudService`, and `OperationsSupport.requireVisibleStudent()`.
- A card is technology-agnostic: `PocketCard.cardUid` can hold an NFC, RFID, or QR identifier. `StoreTerminal.code` is recorded on each purchase for audit.
- All money movements are immutable ledger rows. Header balances are derived and updated in the same transaction.
- Date/time text fields use `TimeValues` (whole-second ISO-8601 UTC) so values fit the existing `VARCHAR(20)` schema and remain lexicographically sortable.

## Roles added

| Role | Owns | Main permissions |
| --- | --- | --- |
| `LIBRARIAN` | Books, copies, loans, reservations, lab sessions | Full library/lab CRUD and actions; read students, teachers, classes, subjects, timetable, departments, tasks, store items |
| `STORE_KEEPER` | Store items, terminals, inventory, stock, purchase requests | Full store CRUD, stock movements, purchase approvals/receiving, read/update work queue |
| `CASHIER` | POS and top-ups | `store-pos`, `pocket-topups`; read cards, transactions, store items/terminals, fees, invoices, tasks |

Existing roles keep their previous access. Parents/students gain read access to their child's cards, limits, transactions, library records, and lab sessions, plus the guarded actions listed below.

## Generic resources registered

All are available under `/api/v1/{resource}` with the standard index/create/edit UI:

| Group | Resources |
| --- | --- |
| Fees | `fee-categories`, `fee-plans`, `invoices`, `invoice-items` |
| Pocket money | `pocket-cards`, `spending-limits`, `pocket-transactions` |
| Store | `store-items`, `store-terminals`, `inventory-items`, `stock-movements`, `purchase-requests` |
| Operations | `departments`, `staff-attendance`, `school-tasks`, `staff-leaves`, `school-events` |
| Library/lab | `books`, `book-copies`, `book-loans`, `book-reservations`, `lab-sessions` |
| Academic planning | `periods`, `exams`, `exam-sessions`, `duty-assignments`, `report-cards`, `parent-letters` |

`pocket-topups`, `store-pos`, `marks-upload`, `timetable-generate`, `duty-generate`, `exam-schedule`, `results-generate`, `academic-automation`, `online-tests`, and `test-attempts` are **policy-only pseudo-resources**. They have no CRUD table or route; they gate the domain endpoints below. `OnlineTest` rows are managed through `/api/v1/tests`. `ReportCard` and `ParentLetter` implement `StudentLinked`, so guardians and students only ever see their own child's results.

## Fee management

### Lifecycle

1. Define a `FeeCategory` and a `FeePlan` (amount, class, term, due date, frequency).
2. Issue an invoice with `POST /api/v1/fee-management/invoices`.
   - Creates `Invoice` + `InvoiceItem`.
   - Creates a `FeeRecord` with `reference = invoiceNo`, so the existing fee ledger and reports keep working.
   - Sends an in-app/SMS guardian notification to the outbox.
   - `POST /api/v1/fee-management/invoices/generate` issues invoices for every active student in a class from one plan.
3. Record payment with `POST /api/v1/fee-management/fees/{feeId}/payments`.
   - `FeeService` writes a separate `FeePayment` and recomputes `FeeRecord.paidAmount`/`status`.
   - `FeeManagementService` mirrors the same totals and status onto the linked `Invoice`.
   - Statuses: `PENDING` → `PARTIAL` → `PAID`.
4. Reverse with `POST /api/v1/fee-management/payments/{id}/reverse`. Only `CONFIRMED` payments can be reversed; fee, invoice, and payment statuses are restored consistently.
5. `GET /api/v1/fee-management/summary` returns billed, collected, and outstanding totals for scoped data.

Guardians may read invoices/plans/categories and fee records for their own child, but cannot issue or reverse invoices. Accountants and admins own the write actions.

### API

| Method | Path | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/fee-management/invoices` | `invoices:CREATE` |
| `POST` | `/api/v1/fee-management/invoices/generate` | `invoices:CREATE` |
| `POST` | `/api/v1/fee-management/fees/{id}/payments` | `fees:UPDATE` |
| `POST` | `/api/v1/fee-management/payments/{id}/reverse` | `payments:UPDATE` |
| `GET` | `/api/v1/fee-management/summary` | `fees:READ` |

## Pocket money and school store POS

### Card and top-up flow

1. An admin/accountant/cashier issues a `PocketCard` with a `cardUid`. The card starts `ACTIVE` with configurable daily/weekly/monthly limits.
2. A parent tops up with `POST /api/v1/pocket/cards/{id}/topups`.
   - `amount` must be positive.
   - `providerReference` (e.g. M-PESA code) is idempotent: replaying the same reference returns the original transaction.
   - A `TOPUP` ledger row is written and `card.balance` increases in the same transaction.
3. The parent sets limits with `PUT /api/v1/pocket/cards/{id}/limits`.
   - Empty `category` updates card-level daily/weekly/monthly limits.
   - A `category` updates that category's daily/weekly limits.
   - `POST /api/v1/pocket/cards/{id}/status` freezes, marks lost, or closes a card.
4. `GET /api/v1/pocket/cards/{id}/balance` returns the balance, current limits, and spent today/week/month. `GET .../transactions` returns the ledger.

### Checkout flow

`POST /api/v1/store/pos/charge` accepts:

```json
{
  "cardUid": "CARD-ABCD1234",
  "terminalCode": "POS-01",
  "storeItemId": "<uuid>",
  "quantity": 2,
  "providerReference": "SALE-0001"
}
```

The service checks, in one transaction:

- card is `ACTIVE`;
- terminal exists and is `ACTIVE` (optional for manual sales, but recorded when present);
- item is active and has enough stock;
- balance covers `price × quantity`;
- card daily/weekly/monthly and category daily/weekly limits are not exceeded;
- `providerReference` is not a replay.

On success it writes a `PURCHASE` ledger row with `balanceAfter`, debits the card, decrements store stock, stamps `terminal.lastSeenAt`, audits the sale, and notifies the guardian when the remaining balance drops to 20% of the daily limit.

`POST /api/v1/store/transactions/{id}/refund` reverses a posted purchase, credits the card, restores stock, and writes a `REFUND` row.

### API

| Method | Path | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/pocket/cards` | `pocket-cards:CREATE` |
| `GET` | `/api/v1/pocket/cards/{id}/balance` | `pocket-cards:READ` |
| `POST` | `/api/v1/pocket/cards/{id}/topups` | `pocket-topups:CREATE` |
| `PUT` | `/api/v1/pocket/cards/{id}/limits` | `spending-limits:UPDATE` |
| `POST` | `/api/v1/pocket/cards/{id}/status` | `pocket-cards:UPDATE` |
| `GET` | `/api/v1/pocket/cards/{id}/transactions` | `pocket-transactions:READ` |
| `POST` | `/api/v1/store/pos/charge` | `store-pos:CREATE` |
| `POST` | `/api/v1/store/transactions/{id}/refund` | `store-pos:UPDATE` |

Common 409 conflicts: `INSUFFICIENT_BALANCE`, `SPENDING_LIMIT_EXCEEDED`, `OUT_OF_STOCK`. Common 400 errors: `CARD_NOT_ACTIVE`, `TERMINAL_INACTIVE`, `ITEM_INACTIVE`, `INVALID_QUANTITY`.

## Library and computer lab

1. Maintain `Book` catalogue rows with total/available copies and optional `digitalUrl`; register barcoded `BookCopy` rows.
2. `POST /api/v1/library/loans` checks out an available copy to a student:
   - maximum 5 active loans (`BORROWED` or `OVERDUE`);
   - overdue fines must be cleared first;
   - loan period is 14 days; copy status and book availability are updated atomically.
3. `POST /api/v1/library/loans/{id}/return` calculates a 500 TZS/day overdue fine, restores the copy, and increments availability.
4. `POST /api/v1/library/loans/{id}/renew` adds 7 days, but is blocked when an active reservation exists.
5. `POST /api/v1/library/reservations` places a hold for a student.
6. `POST /api/v1/library/lab-sessions` books lab time. It rejects seat/student overlaps, requires a lab name and valid time range, and requires the selected book to have a digital copy when one is supplied. The session record carries the book reference, giving the student digital library access during study time.
7. `GET /api/v1/library/summary` returns title, loan, active-loan, and lab-session counts.

Librarians own catalogue and circulation. Teachers can read library records and book lab sessions. Parents/students can read their own records and create reservations/lab bookings; `requireVisibleStudent()` prevents acting for another child.

Common 409 conflicts: `COPY_NOT_AVAILABLE`, `LOAN_LIMIT`, `OUTSTANDING_FINE`, `BOOK_RESERVED`, `LAB_CONFLICT`.

## School operations (beyond students)

| Area | What is covered |
| --- | --- |
| Departments | `departments` with head teacher, budget, status; `POST /api/v1/operations/departments/{id}/head` assigns a head |
| Staff attendance | `staff-attendance` for teacher/staff check-in, check-out, status, and remarks |
| Work queue | `school-tasks` for automated and manual jobs, with priority, due date, department, completion timestamp; `POST /api/v1/operations/tasks/{id}/complete` |
| Inventory | `inventory-items` plus `stock-movements` (`IN`, `OUT`, `ADJUST`) through `POST /api/v1/operations/stock/movements` |
| Purchasing | `purchase-requests` with approve/reject and receiving through `POST /api/v1/operations/purchase-requests/{id}/approve` and `/receive` |
| Notifications | `GET /api/v1/operations/notifications` lists pending outbox messages; dispatch is automated |

Low stock (`quantity <= reorderLevel`) automatically creates a pending purchase request and a high-priority review task, and never duplicates an open request for the same item. Approving or rejecting a request also creates a follow-up task. Receiving an approved request posts an `IN` stock movement.

`GET /api/v1/operations/summary` is the data source for the Operations Hub page.

## Academic automation

`AcademicAutomationService` turns the flat academic records into real school workflows. All actions are guarded by `ResourceAccessPolicy` and write an audit entry.

### Timetable generation

`POST /api/v1/academics/timetable/generate` builds a weekly timetable for one class from `Period` time slots, active `Subject` rows, and `TeacherAssignment` rows. It assigns a teacher per subject, falls back to the class teacher, and calls the existing clash checks so a class, teacher, or room is never double-booked. Conflicting slots are skipped and reported, never overwritten. Set `replace=true` to clear the class's existing entries first. Defaults: all active periods, all weekdays, and the school class's room.

### Bulk marks upload

`POST /api/v1/academics/marks/bulk` upserts `GradeRecord` rows for a whole class from an `entries` array of `{ studentId, score, remarks }`. It requires the caller to be assigned to the subject (admins/registrars bypass this via `isPrivileged()`), rejects students not actively enrolled in the class, validates `0 <= score <= maxScore`, and computes the grade band with `GradingService`. Set `notify=true` to queue a guardian message per student.

### Spreadsheet marks, ranking and parent letters

The clerk-friendly spreadsheet flow is an Excel-compatible UTF-8 CSV:

1. `GET /api/v1/academics/marks/template?classId=...&examType=TEST&term=TERM%201` downloads a template prefilled with the active class register (registration number, student name and score column).
2. Fill the `score` column and upload the file as multipart `file` to `POST /api/v1/academics/marks/import`, together with `classId`, `subjectId`, `examType`, `term`, `maxScore`, `notify`, and `autoResults`.
3. `ResultsAutomationService` parses the CSV into `GradeRecord` rows, grades them through `GradingService`, and (when `autoResults=true`) creates or reuses an `Exam`, generates and publishes ranked `ReportCard` rows, and writes one `ParentLetter` per published card.

Each letter records the student's strongest and weakest subjects, the class position, and a next-semester requirements section derived from weak subjects and average. `POST /api/v1/academics/results/letters/generate` regenerates letters from already published cards without re-importing marks. The import response returns saved marks, ranking (strong/weak subjects), and compact letter summaries for the teacher's review screen.

### Duty roster and staff leave

- `POST /api/v1/academics/duty/generate` rotates active teachers across duty types (default `ASSEMBLY, DISCIPLINE, LUNCH, DISMISSAL`) for a date range, excluding teachers on approved leave and skipping duplicate assignments.
- `POST /api/v1/academics/duty/{id}/cancel` marks a duty `CANCELLED`.
- `POST /api/v1/academics/leaves/{id}/decision` approves or rejects a `StaffLeave`. Approval stamps the decision, notifies the teacher, and opens a high-priority `school-tasks` item to arrange duty cover.

### Exam scheduling and results

- `POST /api/v1/academics/exams/{id}/schedule` places each class/subject paper into a clash-free date and time slot, sets an invigilator from teacher assignments, and moves the exam to `SCHEDULED`.
- `POST /api/v1/academics/results/generate` computes ranked `ReportCard` rows from grades matching the exam's type and term: total, maximum, average, grade, position, and class size. Re-running regenerates drafts in place (references stay stable).
- `POST /api/v1/academics/results/{id}/publish` publishes a card and notifies the guardian. Publication is also automatic for cards whose exam has ended.

### Daily automation

`POST /api/v1/academics/automation/daily` publishes due results, sends tomorrow's duty and exam reminders, escalates overdue work tasks to `URGENT`, and prepares the staff roll call. It is idempotent: repeated runs do not duplicate attendance rows. `GET /api/v1/academics/summary` backs the Results Planning tiles.

### Parent portal

`GET /api/v1/parent-portal/children` lists students linked to the logged-in guardian (`Student.guardianUserId` / `Student.userId`). `GET /api/v1/parent-portal/overview?studentId=...` returns one authorised aggregate with every linked record: subject results grouped by term, attendance summary and raw records, report cards and results, generated parent letters, fee totals and invoices, the class timetable, and enrolments. The service re-applies row scoping, so a parent or student can never request another child's data even with a guessed identifier. The `/parent-portal` page presents these as tabs.

### Online tests

`POST /api/v1/tests` lets a teacher prepare a test (title, class, subject, duration, optional teacher code, and questions with options, correct index and points). A six-digit code is generated when none is supplied. `GET /api/v1/tests` lists tests prepared by the teacher (admins see all), `GET /api/v1/tests/{id}/link` returns the signed student link, and `POST /api/v1/tests/{id}/publish` / `/close` (or `POST /api/v1/tests/{id}/status` with `status`) change the status. Links are JWT `TEST_LINK` tokens carrying tenant and test identifiers; `/test/{token}` is a public SPA route and the public API restores tenant scope with `TenantContext.callAs(...)`.

Students enter the teacher code and their registration number (`Student.studentCode`). `GET /api/v1/public/tests/{token}/preview?accessCode=...`, `POST .../start`, and `POST .../submit` validate the code, the test's published status, the class enrolment, and the timer. Attempts are stored per `testId + studentId`, so a resubmission updates the same row. `GET /api/v1/tests/{id}/results` lists every active enrolment with attended/not-attended, score, and submission time, plus totals and average score.

### API

| Method | Path | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/academics/timetable/generate` | `timetable:CREATE` |
| `POST` | `/api/v1/academics/marks/bulk` | `marks-upload:CREATE` + subject assignment |
| `GET` | `/api/v1/academics/marks/template` | `marks-upload:CREATE` |
| `POST` | `/api/v1/academics/marks/import` | `marks-upload:CREATE` + subject assignment |
| `POST` | `/api/v1/academics/results/letters/generate` | `results-generate:CREATE` |
| `POST` | `/api/v1/academics/duty/generate` | `duty-assignments:CREATE` |
| `POST` | `/api/v1/academics/duty/{id}/cancel` | `duty-assignments:UPDATE` |
| `POST` | `/api/v1/academics/leaves/{id}/decision` | `staff-leaves:UPDATE` |
| `POST` | `/api/v1/academics/exams/{id}/schedule` | `exam-sessions:CREATE` |
| `POST` | `/api/v1/academics/results/generate` | `report-cards:CREATE` |
| `POST` | `/api/v1/academics/results/{id}/publish` | `report-cards:UPDATE` |
| `GET` | `/api/v1/academics/summary` | `exams:READ` |
| `POST` | `/api/v1/academics/automation/daily` | `academic-automation:CREATE` |
| `GET` | `/api/v1/parent-portal/children` | `students:READ` + guardian row scope |
| `GET` | `/api/v1/parent-portal/overview` | `students:READ` + guardian row scope |
| `POST` | `/api/v1/tests` | `online-tests:CREATE` |
| `GET` | `/api/v1/tests` | `online-tests:READ` |
| `GET` | `/api/v1/tests/{id}/link` | `online-tests:READ` |
| `POST` | `/api/v1/tests/{id}/publish`, `/close`, `/status` | `online-tests:UPDATE` |
| `GET` | `/api/v1/tests/{id}/results` | `online-tests:READ` |
| `GET` | `/api/v1/public/tests/{token}/preview` | Signed `TEST_LINK` token + code |
| `POST` | `/api/v1/public/tests/{token}/start`, `/submit` | Signed `TEST_LINK` token + code |

Admins and registrars own planning and result actions; teachers may upload marks for their assigned subjects, prepare and run their own tests, and read duty/report data. Attempts and parent letters are read-only for teachers. Guardians and students can read their own report cards and letters through `/api/v1/report-cards` and `/api/v1/parent-letters`, or the aggregate parent portal API. Public test endpoints are the only anonymous domain routes; they accept a short-lived signed link and no stored credentials.

## Frontend pages

| Route | Page | Purpose |
| --- | --- | --- |
| `/pocket-money` | `PocketMoney.tsx` | Select child card, top up, set limits, view ledger |
| `/library-desk` | `LibraryDesk.tsx` | Issue/return/renew books, reserve, book lab time |
| `/store-pos` | `StorePos.tsx` | Scan card UID, choose terminal and item, charge, view receipt |
| `/operations-hub` | `OperationsHub.tsx` | Summary, quick links, approve requests, complete tasks |
| `/timetable-generator` | `TimetableGenerator.tsx` | Choose class, days, and periods; generate a clash-free timetable |
| `/marks-upload` | `MarksUpload.tsx` | Download/fill/upload the marks template, direct bulk entry, ranking and parent-letter summaries |
| `/duty-roster` | `DutyRoster.tsx` | Generate teacher duty rotations, cancel duties, decide leave requests |
| `/results-planning` | `ResultsPlanning.tsx` | Schedule exam papers, generate/publish report cards, run daily jobs |
| `/parent-portal` | `ParentPortal.tsx` | All linked child data in tabs: results by term, attendance, cards, letters, fees, timetable |
| `/online-tests` | `OnlineTests.tsx` | Prepare/publish tests, copy student links, view attendance and scores |
| `/test/:token` | `TakeTest.tsx` | Public student test page: teacher code + registration number, timed questions, submission |
| `/parent-letters` | `ResourcePage` | Index/create/edit page for automatically generated guardian letters |

All other resources use the existing MTAI-style index cards/tables and create/edit pages. Custom pages reuse the same navy sidebar, palette, and shared CSS classes; no gradients were introduced.

## Automation

Background jobs are opt-in and disabled by default. Enable with `AUTOMATION_ENABLED=true`. Each job runs once per tenant inside `TenantContext.runAs(...)`, so tenant isolation and auditing stay correct outside HTTP requests.

| Job | Default schedule | Env override | Action |
| --- | --- | --- | --- |
| Fee reminders | `0 0 6 * * *` (UTC) | `AUTOMATION_FEE_CRON` | Flags overdue invoices/fees and queues guardian reminders |
| Library expiry | `0 5 6 * * *` (UTC) | `AUTOMATION_LIBRARY_CRON` | Marks loans overdue and computes daily fines |
| Stock reorder | `0 10 6 * * *` (UTC) | `AUTOMATION_REORDER_CRON` | Creates purchase requests and high-priority tasks for low stock |
| Result publishing | `0 15 6 * * *` (UTC) | `AUTOMATION_RESULT_CRON` | Publishes draft report cards once their exam has ended |
| Academic reminders | `0 20 6 * * *` (UTC) | `AUTOMATION_DUTY_CRON` | Sends tomorrow's duty and exam reminders to teachers/guardians |
| Daily school run | `0 25 6 * * *` (UTC) | `AUTOMATION_DAILY_CRON` | Publishes results, sends reminders, escalates tasks, prepares staff roll call |
| Notification dispatch | every 15 min | `AUTOMATION_DISPATCH_MS` | Drains the outbox through the configured adapter, marking rows `SENT`/`FAILED` |

`NotificationService.deliver()` currently logs each message. Replace that method with SMS/email/WhatsApp gateway calls when credentials are available; retries remain auditable because outbox rows are never deleted.

## Runbooks

### Verify a production deployment

```bash
docker compose up -d --build
curl -fsS http://localhost:8085/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8085/api/v1/students          # 401
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8085/v3/api-docs              # 404
docker compose exec -T postgres psql -U school_wallet -d school_wallet \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

Expected migrations: `1 foundation`, `1.1 school core`, `2 school workflows`, `3 operations`, `4 academic automation`, `5 parent portal testing`, `6 student location and rebrand`, `7 roles positions documents`, `8 cash withdrawal requests`, `9 pocket money init`, `10 student prefect roles`, `11 term attendance weeks`, `12 pocket money card labels`, all successful. For a production-style check, `BOOTSTRAP_ENABLED=false` leaves `app_users` at zero; the local always-on stack seeds 12 demo users instead.

### Reconcile a card

```sql
select sum(amount) filter (where type = 'TOPUP') as topups,
       sum(amount) filter (where type = 'PURCHASE') as spent,
       max(balance_after) filter (where status = 'POSTED') as ledger_balance
from pocket_transactions
where tenant_id = :tenantId and card_id = :cardId;

select balance from pocket_cards where tenant_id = :tenantId and id = :cardId;
```

`ledger_balance` and `pocket_cards.balance` must agree. Reversed rows stay in the ledger with `status = 'REVERSED'` and are excluded from spend totals.

### Reconcile a fee

`FeeRecord.reference` equals `Invoice.invoiceNo`. Compare `fee_records.paid_amount` with `invoices.paid_amount`; both are updated by the same payment service call. Payment reversals restore both totals.

### Investigate failed automation

1. Check the application log for `Automation job failed for tenant`.
2. Inspect the tenant's `notification_outbox` rows with `status = 'FAILED'` and `last_error`.
3. Verify cron properties and that `AUTOMATION_ENABLED=true` reached the container.
4. Re-run the specific service action manually through its API once the root cause is resolved.

## Verification performed

- `./gradlew test bootJar --no-daemon` — 30 tests, 0 failures (includes PostgreSQL Flyway/validate, the four operations flows, and the five academic automation flows).
- `npm --prefix frontend/school-wallet-react run build` — passed; built assets synced to `src/main/resources/static/`.
- `docker compose config --quiet` — passed. `docker compose build` could not be re-run in this environment because Docker registry DNS is unavailable; the isolated smoke below used the locally built bootJar staged into the cached runtime image instead.
- Dev smoke on `8085` — `/`, `/login`, `/dashboard`, `/parent-portal`, `/online-tests`, `/test/{token}`, `/parent-letters`, create/edit deep links, hashed assets and health all `200`; unauthenticated API `401`.
- Online-test flow — teacher created and published a two-question test, the signed link preview/start/submit worked for `DEMO-STU-001` (score 5/5), and the teacher's attendance report showed 1 attended / 1 not attended.
- Marks flow — CSV template download, two-subject import, grading, ranking (Asha 81.5% position 1, Juma 53.5% position 2), strong/weak subjects and two parent letters, plus guardian aggregate showing results, attendance, card, letter and fees.
- Isolated prod smoke on temporary ports `8086`/`55432` — health `UP`, SPA and deep links `200`, unauthenticated API `401`, API docs `404`, migrations `1`/`1.1`/`2`/`3`/`4`/`5` successful, 54 public tables (including `online_tests`, `test_attempts`, `parent_letters`), `app_users=0`.
