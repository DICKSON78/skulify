-- Academic structure, fee ledger, assignments and notification outbox.

ALTER TABLE students ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_user_id UUID;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS teacher_id UUID;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS class_id UUID;
ALTER TABLE grade_records ADD COLUMN IF NOT EXISTS teacher_id UUID;

CREATE INDEX IF NOT EXISTS students_user_idx ON students (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS students_guardian_user_idx ON students (tenant_id, guardian_user_id);
CREATE INDEX IF NOT EXISTS attendance_student_date_idx ON attendance_records (tenant_id, student_id, attendance_date);
CREATE INDEX IF NOT EXISTS grades_student_idx ON grade_records (tenant_id, student_id);

CREATE TABLE IF NOT EXISTS academic_years (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    name        VARCHAR(40)  NOT NULL,
    start_date  VARCHAR(20)  NOT NULL,
    end_date    VARCHAR(20)  NOT NULL,
    is_current  BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID
);

CREATE TABLE IF NOT EXISTS terms (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    academic_year_id UUID        NOT NULL,
    name             VARCHAR(40) NOT NULL,
    term_order       INTEGER     NOT NULL,
    start_date       VARCHAR(20),
    end_date         VARCHAR(20),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID
);

CREATE TABLE IF NOT EXISTS enrolments (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    student_id       UUID        NOT NULL,
    class_id         UUID        NOT NULL,
    academic_year_id UUID        NOT NULL,
    enrolment_date   VARCHAR(20),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID
);
CREATE INDEX IF NOT EXISTS enrolments_student_idx ON enrolments (tenant_id, student_id, status);

CREATE TABLE IF NOT EXISTS teacher_assignments (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    teacher_id       UUID        NOT NULL,
    class_id         UUID,
    subject_id       UUID,
    academic_year_id UUID,
    scope            VARCHAR(25) NOT NULL DEFAULT 'SUBJECT_TEACHER',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID
);
CREATE INDEX IF NOT EXISTS teacher_assignments_teacher_idx ON teacher_assignments (tenant_id, teacher_id, status);

CREATE TABLE IF NOT EXISTS fee_payments (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID           NOT NULL REFERENCES tenants (id),
    fee_id             UUID           NOT NULL,
    student_id         UUID           NOT NULL,
    amount             NUMERIC(14,2)  NOT NULL,
    method             VARCHAR(30)    NOT NULL,
    provider_reference VARCHAR(80),
    paid_at            VARCHAR(30)    NOT NULL,
    recorded_by        VARCHAR(120),
    status             VARCHAR(20)    NOT NULL DEFAULT 'CONFIRMED',
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    created_by         UUID,
    updated_by         UUID
);
CREATE INDEX IF NOT EXISTS fee_payments_fee_idx ON fee_payments (tenant_id, fee_id);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    channel          VARCHAR(15)  NOT NULL DEFAULT 'IN_APP',
    recipient        VARCHAR(200) NOT NULL,
    subject          VARCHAR(200),
    body             VARCHAR(2000) NOT NULL,
    related_resource VARCHAR(40),
    related_id       UUID,
    status           VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID
);
CREATE INDEX IF NOT EXISTS notification_outbox_pending_idx ON notification_outbox (tenant_id, status);
