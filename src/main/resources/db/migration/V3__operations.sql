-- Fee management, pocket money, school store, library, lab and operations.
-- Every tenant-owned table repeats the BaseEntity/TenantOwnedEntity columns so
-- Hibernate ddl-auto=validate succeeds in production.

CREATE TABLE fee_categories (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT fee_categories_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE fee_plans (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    code        VARCHAR(40)   NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    category_id UUID,
    class_id    UUID,
    term_id     UUID,
    amount      NUMERIC(14,2) NOT NULL,
    frequency   VARCHAR(20)   DEFAULT 'TERMLY',
    due_date    VARCHAR(20),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT fee_plans_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE invoices (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    invoice_no   VARCHAR(40)   NOT NULL,
    student_id   UUID          NOT NULL,
    fee_plan_id  UUID,
    term_id      UUID,
    issue_date   VARCHAR(20),
    due_date     VARCHAR(20),
    total_amount NUMERIC(14,2) NOT NULL,
    paid_amount  NUMERIC(14,2) DEFAULT 0,
    status       VARCHAR(15)   NOT NULL DEFAULT 'DRAFT',
    remarks      VARCHAR(500),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT invoices_tenant_no_key UNIQUE (tenant_id, invoice_no)
);
CREATE INDEX invoices_student_idx ON invoices (tenant_id, student_id);
CREATE INDEX invoices_due_idx ON invoices (tenant_id, due_date);

CREATE TABLE invoice_items (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    invoice_id  UUID          NOT NULL,
    student_id  UUID          NOT NULL,
    category_id UUID,
    description VARCHAR(200),
    quantity    INTEGER       NOT NULL DEFAULT 1,
    unit_amount NUMERIC(14,2),
    amount      NUMERIC(14,2),
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX invoice_items_invoice_idx ON invoice_items (tenant_id, invoice_id);

CREATE TABLE pocket_cards (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    card_uid     VARCHAR(80)   NOT NULL,
    student_id   UUID          NOT NULL,
    balance      NUMERIC(14,2) NOT NULL DEFAULT 0,
    daily_limit  NUMERIC(14,2),
    weekly_limit NUMERIC(14,2),
    monthly_limit NUMERIC(14,2),
    status       VARCHAR(15)   NOT NULL DEFAULT 'ACTIVE',
    issued_at    VARCHAR(20),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT pocket_cards_tenant_uid_key UNIQUE (tenant_id, card_uid)
);
CREATE INDEX pocket_cards_student_idx ON pocket_cards (tenant_id, student_id);

CREATE TABLE spending_limits (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    card_id      UUID          NOT NULL,
    student_id   UUID          NOT NULL,
    category     VARCHAR(40)   NOT NULL,
    daily_limit  NUMERIC(14,2),
    weekly_limit NUMERIC(14,2),
    monthly_limit NUMERIC(14,2),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX spending_limits_card_idx ON spending_limits (tenant_id, card_id);

CREATE TABLE pocket_transactions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    reference         VARCHAR(40)   NOT NULL,
    card_id           UUID          NOT NULL,
    student_id        UUID          NOT NULL,
    type              VARCHAR(15)   NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    category          VARCHAR(40),
    balance_after     NUMERIC(14,2),
    terminal_id       UUID,
    store_item_id     UUID,
    quantity          INTEGER       NOT NULL DEFAULT 1,
    provider_reference VARCHAR(80),
    status            VARCHAR(15)   NOT NULL DEFAULT 'POSTED',
    description       VARCHAR(500),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT pocket_transactions_provider_key UNIQUE (tenant_id, provider_reference)
);
CREATE INDEX pocket_transactions_card_idx ON pocket_transactions (tenant_id, card_id, created_at DESC);
CREATE INDEX pocket_transactions_student_idx ON pocket_transactions (tenant_id, student_id);
CREATE INDEX pocket_transactions_type_idx ON pocket_transactions (tenant_id, type, created_at DESC);

CREATE TABLE store_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL REFERENCES tenants (id),
    sku           VARCHAR(40)   NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    category      VARCHAR(60),
    price         NUMERIC(14,2) NOT NULL,
    stock_quantity INTEGER      NOT NULL,
    reorder_level INTEGER       NOT NULL,
    unit          VARCHAR(20)   DEFAULT 'PIECE',
    location      VARCHAR(120),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT store_items_tenant_sku_key UNIQUE (tenant_id, sku)
);

CREATE TABLE store_terminals (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    location     VARCHAR(120),
    status       VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_at VARCHAR(20),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT store_terminals_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE inventory_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL REFERENCES tenants (id),
    sku           VARCHAR(40)   NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    category      VARCHAR(60),
    quantity      INTEGER       NOT NULL,
    reorder_level INTEGER       NOT NULL,
    unit_cost     NUMERIC(14,2),
    location      VARCHAR(120),
    supplier      VARCHAR(120),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT inventory_items_tenant_sku_key UNIQUE (tenant_id, sku)
);

CREATE TABLE stock_movements (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    item_id      UUID         NOT NULL,
    type         VARCHAR(10)  NOT NULL,
    quantity     INTEGER      NOT NULL,
    reference    VARCHAR(80),
    reason       VARCHAR(300),
    performed_by VARCHAR(120),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX stock_movements_item_idx ON stock_movements (tenant_id, item_id);

CREATE TABLE purchase_requests (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    reference    VARCHAR(40)  NOT NULL,
    department_id UUID,
    item_id      UUID,
    quantity     INTEGER      NOT NULL,
    purpose      VARCHAR(300),
    status       VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(120),
    approved_by  VARCHAR(120),
    approved_at  VARCHAR(20),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT purchase_requests_tenant_reference_key UNIQUE (tenant_id, reference)
);
CREATE INDEX purchase_requests_status_idx ON purchase_requests (tenant_id, status);

CREATE TABLE departments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    head_teacher_id UUID,
    budget          NUMERIC(14,2),
    status          VARCHAR(20)  DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT departments_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE staff_attendance (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    staff_id        UUID         NOT NULL,
    attendance_date VARCHAR(20)  NOT NULL,
    status          VARCHAR(15)  NOT NULL DEFAULT 'PRESENT',
    check_in        VARCHAR(20),
    check_out       VARCHAR(20),
    recorded_by     VARCHAR(120),
    remarks         VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID
);
CREATE INDEX staff_attendance_date_idx ON staff_attendance (tenant_id, attendance_date);

CREATE TABLE school_tasks (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    title           VARCHAR(180)  NOT NULL,
    description     VARCHAR(1000),
    assigned_to     UUID,
    department_id   UUID,
    due_date        VARCHAR(20),
    status          VARCHAR(15)   NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(15)   NOT NULL DEFAULT 'NORMAL',
    source_resource VARCHAR(40),
    source_id       UUID,
    completed_at    VARCHAR(20),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      UUID,
    updated_by      UUID
);
CREATE INDEX school_tasks_status_idx ON school_tasks (tenant_id, status);

CREATE TABLE books (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    isbn            VARCHAR(30)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    author          VARCHAR(150),
    subject         VARCHAR(100),
    department      VARCHAR(100),
    location        VARCHAR(120),
    copies_total    INTEGER      NOT NULL,
    copies_available INTEGER     NOT NULL,
    digital_url     VARCHAR(500),
    status          VARCHAR(20)  DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT books_tenant_isbn_key UNIQUE (tenant_id, isbn)
);

CREATE TABLE book_copies (
    id       UUID PRIMARY KEY,
    tenant_id UUID        NOT NULL REFERENCES tenants (id),
    barcode  VARCHAR(60)  NOT NULL,
    book_id  UUID         NOT NULL,
    location VARCHAR(120),
    status   VARCHAR(20)  DEFAULT 'AVAILABLE',
    version  BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT book_copies_tenant_barcode_key UNIQUE (tenant_id, barcode)
);
CREATE INDEX book_copies_book_idx ON book_copies (tenant_id, book_id);

CREATE TABLE book_loans (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    copy_id     UUID          NOT NULL,
    book_id     UUID          NOT NULL,
    student_id  UUID          NOT NULL,
    borrowed_at VARCHAR(20),
    due_at      VARCHAR(20),
    returned_at VARCHAR(20),
    status      VARCHAR(20)   DEFAULT 'BORROWED',
    fine_amount NUMERIC(14,2) DEFAULT 0,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX book_loans_student_idx ON book_loans (tenant_id, student_id, status);
CREATE INDEX book_loans_due_idx ON book_loans (tenant_id, due_at);

CREATE TABLE book_reservations (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    book_id    UUID        NOT NULL,
    student_id UUID        NOT NULL,
    reserved_at VARCHAR(20),
    status     VARCHAR(20) DEFAULT 'ACTIVE',
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX book_reservations_book_idx ON book_reservations (tenant_id, book_id, status);

CREATE TABLE lab_sessions (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    reference  VARCHAR(40) NOT NULL,
    student_id UUID        NOT NULL,
    book_id    UUID,
    lab        VARCHAR(60),
    seat       VARCHAR(20),
    start_at   VARCHAR(20),
    end_at     VARCHAR(20),
    purpose    VARCHAR(300),
    status     VARCHAR(20) DEFAULT 'BOOKED',
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT lab_sessions_tenant_reference_key UNIQUE (tenant_id, reference)
);
CREATE INDEX lab_sessions_student_idx ON lab_sessions (tenant_id, student_id, start_at);
