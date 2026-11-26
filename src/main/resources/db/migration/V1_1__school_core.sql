-- Core school tables referenced by V2 and by the JPA entities.
-- Version 1.1 runs after V1 (foundation) and before V2 (workflows), so existing
-- deployments keep their V1/V2 checksums while fresh installs get a complete schema.

CREATE TABLE IF NOT EXISTS students (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL REFERENCES tenants (id),
    student_code      VARCHAR(40)  NOT NULL,
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(80)  NOT NULL,
    email             VARCHAR(150),
    phone             VARCHAR(30),
    gender            VARCHAR(10),
    date_of_birth     VARCHAR(20),
    admission_date    VARCHAR(20),
    class_name        VARCHAR(60),
    guardian_name     VARCHAR(120),
    guardian_phone    VARCHAR(30),
    address           VARCHAR(255),
    user_id           UUID,
    guardian_user_id  UUID,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT students_tenant_code_key UNIQUE (tenant_id, student_code)
);
CREATE INDEX IF NOT EXISTS students_status_idx ON students (tenant_id, status);

CREATE TABLE IF NOT EXISTS teachers (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    employee_code    VARCHAR(40)  NOT NULL,
    first_name       VARCHAR(80)  NOT NULL,
    last_name        VARCHAR(80)  NOT NULL,
    email            VARCHAR(150),
    phone            VARCHAR(30),
    specialization   VARCHAR(120),
    employment_type  VARCHAR(20)  DEFAULT 'FULL_TIME',
    hire_date        VARCHAR(20),
    address          VARCHAR(255),
    user_id          UUID,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT teachers_tenant_code_key UNIQUE (tenant_id, employee_code)
);
CREATE INDEX IF NOT EXISTS teachers_user_idx ON teachers (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS school_classes (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    grade_level      VARCHAR(30),
    section          VARCHAR(20),
    room             VARCHAR(40),
    capacity         INTEGER,
    class_teacher_id UUID,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT school_classes_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS subjects (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    department  VARCHAR(80),
    credits     INTEGER,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT subjects_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    student_id      UUID         NOT NULL,
    attendance_date VARCHAR(20)  NOT NULL,
    status          VARCHAR(15)  NOT NULL DEFAULT 'PRESENT',
    check_in        VARCHAR(10),
    remarks         VARCHAR(500),
    recorded_by     VARCHAR(120),
    teacher_id      UUID,
    class_id        UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE IF NOT EXISTS grade_records (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES tenants (id),
    student_id UUID         NOT NULL,
    subject_id UUID         NOT NULL,
    exam_type  VARCHAR(60)  NOT NULL,
    term       VARCHAR(30),
    score      DOUBLE PRECISION,
    max_score  DOUBLE PRECISION,
    grade      VARCHAR(10),
    remarks    VARCHAR(500),
    teacher_id UUID,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by UUID,
    updated_by UUID
);

CREATE TABLE IF NOT EXISTS fee_records (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    reference   VARCHAR(40)   NOT NULL,
    student_id  UUID          NOT NULL,
    fee_type    VARCHAR(80)   NOT NULL,
    amount      NUMERIC(14,2) NOT NULL,
    paid_amount NUMERIC(14,2) DEFAULT 0,
    due_date    VARCHAR(20),
    paid_date   VARCHAR(20),
    status      VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    remarks     VARCHAR(500),
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX IF NOT EXISTS fee_records_student_idx ON fee_records (tenant_id, student_id);

CREATE TABLE IF NOT EXISTS timetable_entries (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    class_id    UUID         NOT NULL,
    subject_id  UUID         NOT NULL,
    teacher_id  UUID,
    day_of_week VARCHAR(15)  NOT NULL DEFAULT 'MONDAY',
    start_time  VARCHAR(10)  NOT NULL,
    end_time    VARCHAR(10)  NOT NULL,
    room        VARCHAR(40),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX IF NOT EXISTS timetable_class_day_idx ON timetable_entries (tenant_id, class_id, day_of_week);

CREATE TABLE IF NOT EXISTS report_definitions (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenants (id),
    name           VARCHAR(150) NOT NULL,
    report_type    VARCHAR(60)  NOT NULL,
    description    VARCHAR(500),
    format         VARCHAR(20)  DEFAULT 'PDF',
    last_generated VARCHAR(30),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_by     UUID
);

CREATE TABLE IF NOT EXISTS app_settings (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    setting_key  VARCHAR(100)  NOT NULL,
    setting_value VARCHAR(1000),
    category     VARCHAR(60),
    description  VARCHAR(500),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT app_settings_tenant_key_key UNIQUE (tenant_id, setting_key)
);
