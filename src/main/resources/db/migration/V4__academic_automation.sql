-- Timetable generation, exam planning, teacher duty rosters, marks uploads and
-- result cards. All tenant-owned tables repeat the BaseEntity/TenantOwnedEntity
-- columns so Hibernate ddl-auto=validate succeeds in production.

CREATE TABLE periods (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    start_time  VARCHAR(10) NOT NULL,
    end_time    VARCHAR(10) NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT periods_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE exams (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    exam_type        VARCHAR(60)  NOT NULL,
    term             VARCHAR(30),
    academic_year_id UUID,
    start_date       VARCHAR(20),
    end_date         VARCHAR(20),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    remarks          VARCHAR(500),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT exams_tenant_code_key UNIQUE (tenant_id, code)
);
CREATE INDEX exams_term_idx ON exams (tenant_id, term, status);

CREATE TABLE exam_sessions (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    exam_id    UUID        NOT NULL,
    class_id   UUID        NOT NULL,
    subject_id UUID        NOT NULL,
    teacher_id UUID,
    exam_date  VARCHAR(20) NOT NULL,
    start_time VARCHAR(10) NOT NULL,
    end_time   VARCHAR(10) NOT NULL,
    room       VARCHAR(40),
    status     VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    remarks    VARCHAR(500),
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX exam_sessions_exam_idx ON exam_sessions (tenant_id, exam_id);
CREATE INDEX exam_sessions_date_idx ON exam_sessions (tenant_id, exam_date);
CREATE INDEX exam_sessions_class_idx ON exam_sessions (tenant_id, class_id, exam_date);

CREATE TABLE duty_assignments (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    teacher_id UUID        NOT NULL,
    duty_type  VARCHAR(40) NOT NULL,
    duty_date  VARCHAR(20),
    day_of_week VARCHAR(15),
    start_time VARCHAR(10),
    end_time   VARCHAR(10),
    location   VARCHAR(120),
    term       VARCHAR(30),
    status     VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes      VARCHAR(500),
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX duty_assignments_teacher_idx ON duty_assignments (tenant_id, teacher_id, duty_date);
CREATE INDEX duty_assignments_date_idx ON duty_assignments (tenant_id, duty_date, status);

CREATE TABLE report_cards (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    reference       VARCHAR(40)  NOT NULL,
    student_id      UUID         NOT NULL,
    class_id        UUID,
    exam_id         UUID,
    term            VARCHAR(30),
    academic_year_id UUID,
    total_score     NUMERIC(10,2),
    max_score       NUMERIC(10,2),
    average         NUMERIC(10,2),
    grade           VARCHAR(10),
    position        INTEGER,
    class_size      INTEGER,
    remarks         VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at    VARCHAR(20),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT report_cards_tenant_reference_key UNIQUE (tenant_id, reference)
);
CREATE INDEX report_cards_student_idx ON report_cards (tenant_id, student_id, status);
CREATE INDEX report_cards_exam_idx ON report_cards (tenant_id, exam_id, status);

CREATE TABLE staff_leaves (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    teacher_id  UUID        NOT NULL,
    leave_type  VARCHAR(40) NOT NULL,
    start_date  VARCHAR(20) NOT NULL,
    end_date    VARCHAR(20) NOT NULL,
    reason      VARCHAR(500),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(120),
    approved_at VARCHAR(20),
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX staff_leaves_teacher_idx ON staff_leaves (tenant_id, teacher_id, status);

CREATE TABLE school_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    title       VARCHAR(180) NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,
    event_date  VARCHAR(20)  NOT NULL,
    start_time  VARCHAR(10),
    end_time    VARCHAR(10),
    location    VARCHAR(120),
    description VARCHAR(1000),
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX school_events_date_idx ON school_events (tenant_id, event_date, status);
