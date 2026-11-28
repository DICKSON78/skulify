# Skulify — Deployment & Operations

For module-level flows, roles, API actions, and runbooks, see [MODULES.md](MODULES.md).

## Stack

- Spring Boot 4.1 (Java 17), React SPA served from the same origin
- PostgreSQL 17 with Flyway migrations and Hibernate `ddl-auto: validate`
- JWT access/refresh tokens, role-based access control, row-level scoping for parents/students

## Configuration rule (important)

Keep configuration only in `src/main/resources/`. **Do not create an `application.yml` in the repository root**: Spring Boot loads external config after classpath config, so a root `application.yml` would silently override `application-prod.yml` and make production fall back to H2 + `create-drop`. The root file was removed and `/application.yml` is git-ignored to prevent regressions.

## Local always-on stack

For a persistent local install, the Compose file starts PostgreSQL and Skulify with `restart: unless-stopped`. Database rows and uploaded documents live in named volumes, so `docker compose down` followed by `docker compose up -d` keeps all data.

```bash
cd /path/to/school_wallet
docker compose up -d --build
curl -fsS http://localhost:8085/actuator/health
```

Open <http://localhost:8085>. The first start seeds the Skulify demo accounts (for example `admin / Admin123!`, `teacher / Teacher123!`, `parent / Parent123!`, `student / Student123!`) and the Pocket Money demo card/terminal. Seeding is idempotent and does not overwrite later edits or balances.

Optional overrides can be placed in `.env` (copy `.env.example`). The built-in defaults are intended for localhost only; set `JWT_SECRET`, `DB_PASSWORD`, and `SUPER_ADMIN_PASSWORD` to real values before exposing port 8085 outside your machine.

- App: `http://localhost:8085`
- PostgreSQL: internal only; use `docker compose exec postgres psql -U school_wallet -d school_wallet`
- Data: `skulify-pgdata` volume (database) and `skulify-documents` volume (`/app/uploads/documents`)
- Logs: `docker compose logs -f app`
- Stop without deleting data: `docker compose stop`
- Start again: `docker compose start`
- Complete removal including data: `docker compose down -v` (destructive)

Compose restarts containers automatically when Docker starts. On a Linux host, also enable Docker itself at boot with `sudo systemctl enable --now docker`. On Docker Desktop, enable “Start Docker Desktop when you sign in”.

## Production with Docker Compose

Create a `.env` file next to `docker-compose.yml`:

```dotenv
DB_PASSWORD=replace-with-a-strong-database-password
JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
CORS_ORIGINS=https://wallet.example.com
BOOTSTRAP_ENABLED=false
API_DOCS_ENABLED=false
AUTOMATION_ENABLED=false
# Optional: AUTOMATION_FEE_CRON, AUTOMATION_LIBRARY_CRON,
# AUTOMATION_REORDER_CRON, AUTOMATION_RESULT_CRON,
# AUTOMATION_DUTY_CRON, AUTOMATION_DAILY_CRON, AUTOMATION_DISPATCH_MS
```

Then:

```bash
docker compose up -d --build
curl -fsS http://localhost:8085/actuator/health
```

`BOOTSTRAP_ENABLED` defaults to `false`, so production starts without demo accounts.

### First-time super admin

Bootstrap is intentionally opt-in. To create the initial platform admin:

1. Set `BOOTSTRAP_ENABLED=true` and `SUPER_ADMIN_PASSWORD` to a unique password of at least 12 characters.
2. Start the stack once: `docker compose up -d --build`.
3. Log in as `SUPER_ADMIN_USERNAME` (default `superadmin`) and rotate the password through the change-password API.
4. Set `BOOTSTRAP_ENABLED=false` and restart: `docker compose up -d`.

Bootstrap fails fast if the username or password is blank, the password is shorter than 12 characters, or the placeholder `ChangeMe123!` is used under the `prod` profile. Re-enabling bootstrap later will not overwrite an existing user; use the password-change API for rotations.

## Database migrations

Flyway runs automatically at startup with these migrations:

| Version | File | Purpose |
| --- | --- | --- |
| 1 | `V1__foundation.sql` | Tenants, users, tokens, audit log |
| 1.1 | `V1_1__school_core.sql` | Students, teachers, classes, subjects, attendance, grades, fees, timetable, settings |
| 2 | `V2__school_workflows.sql` | Academic years, terms, enrolments, assignments, payments, notification outbox |
| 3 | `V3__operations.sql` | Fee plans/invoices, pocket cards and ledger, store/inventory, departments, staff attendance, tasks, library, lab sessions |
| 4 | `V4__academic_automation.sql` | Periods, exams, exam sessions, duty assignments, report cards, staff leaves, school events |
| 5 | `V5__parent_portal_testing.sql` | Online tests, test attempts, parent letters |

Rules:

- Never edit an already applied migration; add a new `Vn__*.sql` instead.
- `spring.jpa.hibernate.ddl-auto=validate` makes the app refuse to start if entities and schema drift.
- `FlywayMigrationIntegrationTest` boots the full app against PostgreSQL 17 via Testcontainers and asserts every mapped table exists. It is skipped automatically when Docker is unavailable.

## Backups and restore

```bash
# Backup (keeps 14 days by default)
DB_HOST=localhost DB_PASSWORD='...' ./scripts/backup.sh

# Restore
DB_HOST=localhost DB_PASSWORD='...' ./scripts/restore.sh backups/school-wallet-20261120-120000.sql.gz
```

Override `DB_NAME`, `DB_USER`, `DB_PORT`, `BACKUP_DIR`, and `RETENTION_DAYS` as needed. Schedule `backup.sh` with cron or a Kubernetes CronJob and test restores regularly.

## Role matrix

| Role | Read | Write | Data scope |
| --- | --- | --- | --- |
| `SUPER_ADMIN`, `TENANT_ADMIN` | Everything | Everything | All tenants / own tenant |
| `HEAD_TEACHER`, `REGISTRAR` | Academic, structure, fees, reports, settings | Academic + structure, timetable/marks/duty/exam/result planning | Own tenant |
| `TEACHER` | Academic modules, duty schedule, own report data, own tests and attempt results | Attendance, grades, bulk marks upload, online tests | Assigned classes/subjects; bulk attendance requires `CLASS_TEACHER`; tests are scoped to the creator |
| `ACCOUNTANT` | Students, reports, settings, finance | Fees, payments, invoices, purchase approvals | Own tenant |
| `LIBRARIAN` | Library, lab, students, classes | Books, loans, reservations, lab sessions | Own tenant |
| `STORE_KEEPER` | Store, inventory, students, tasks | Stock, terminals, purchase requests, work queue | Own tenant |
| `CASHIER` | POS-related cards, transactions, invoices, fees | POS charges, refunds, top-ups | Own tenant |
| `PARENT`, `STUDENT` | Own child's students, attendance, grades, report cards, parent letters, fees, timetable, cards, library, lab | Top-ups, spending limits, reservations, lab bookings, own test attempts | Own linked records only |

Students and parents are scoped in `ResourceAccessPolicy`, `CrudService`, and `OperationsSupport`; teachers are additionally checked against `TeacherAssignment` records in `AcademicWorkflowService`. Platform/school admins bypass those assignment checks through `isPrivileged()`.

Academic domain actions live under `/api/v1/academics` (timetable generation, bulk marks and the CSV template/import flow, duty rosters, leave decisions, exam scheduling, result generation/publishing, parent-letter regeneration, summary, and the daily run). Teacher test workflows live under `/api/v1/tests`; anonymous student sessions use the signed `/api/v1/public/tests/**` endpoints reached from `/test/{token}`; guardian aggregates use `/api/v1/parent-portal`. Generic CRUD remains at `/api/v1/{resource}` and still includes `/api/v1/students` and `/api/v1/parent-letters`. Test links are signed with the same `app.jwt.secret` and need no separate key. Background automation is opt-in via `AUTOMATION_ENABLED` and runs per tenant.

## Security notes

- `JWT_SECRET` is required and must be at least 32 bytes.
- API docs/Swagger are disabled by default in production; set `API_DOCS_ENABLED=true` only temporarily for debugging.
- CORS is restricted to `CORS_ORIGINS`.
- Audit logs are append-only at the database level; TOTP seeds are encrypted with AES-256-GCM.
- Replace the default `DB_PASSWORD` before exposing PostgreSQL.

## Development accounts (dev profile only)

| Username | Password | Role |
| --- | --- | --- |
| `superadmin` | `ChangeMe123!` | `SUPER_ADMIN` |
| `admin` | `Admin123!` | `TENANT_ADMIN`, `HEAD_TEACHER` |
| `teacher` | `Teacher123!` | `TEACHER` |
| `parent` | `Parent123!` | `PARENT` |
| `student` | `Student123!` | `STUDENT` |

Dev uses in-memory H2 with `create-drop`; data resets on restart. Run locally with:

```bash
./gradlew bootRun --no-daemon
```

## CI

`.github/workflows/ci.yml` runs the backend test suite and `bootJar`, then builds the React app with `npm ci && npm run build` on every push and pull request.
