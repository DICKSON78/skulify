-- Online computer-based tests and automatically generated parent letters.
-- Column names and types mirror the JPA entities so ddl-auto=validate succeeds.

CREATE TABLE online_tests (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    code             VARCHAR(40)  NOT NULL,
    access_code      VARCHAR(60)  NOT NULL,
    title            VARCHAR(160) NOT NULL,
    description      VARCHAR(500),
    subject_id       UUID         NOT NULL,
    class_id         UUID         NOT NULL,
    start_at         VARCHAR(20),
    end_at           VARCHAR(20),
    duration_minutes INTEGER      NOT NULL DEFAULT 30,
    questions_json   VARCHAR(20000) NOT NULL DEFAULT '[]',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT online_tests_tenant_code_key UNIQUE (tenant_id, code)
);
CREATE INDEX online_tests_class_idx ON online_tests (tenant_id, class_id, status);
CREATE INDEX online_tests_teacher_idx ON online_tests (tenant_id, created_by, status);

CREATE TABLE test_attempts (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    test_id         UUID         NOT NULL,
    student_id      UUID         NOT NULL,
    registration_no VARCHAR(40)  NOT NULL,
    answers_json    VARCHAR(20000) NOT NULL DEFAULT '{}',
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_score       DOUBLE PRECISION NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      VARCHAR(20),
    submitted_at    VARCHAR(20),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT test_attempts_student_key UNIQUE (tenant_id, test_id, student_id)
);
CREATE INDEX test_attempts_test_idx ON test_attempts (tenant_id, test_id, status);
CREATE INDEX test_attempts_student_idx ON test_attempts (tenant_id, student_id);

CREATE TABLE parent_letters (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    reference       VARCHAR(40)   NOT NULL,
    student_id      UUID          NOT NULL,
    report_card_id  UUID,
    class_id        UUID,
    term            VARCHAR(30),
    title           VARCHAR(160)  NOT NULL,
    content         VARCHAR(20000) NOT NULL,
    requirements    VARCHAR(20000) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'GENERATED',
    generated_at    VARCHAR(20),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT parent_letters_tenant_reference_key UNIQUE (tenant_id, reference)
);
CREATE INDEX parent_letters_student_idx ON parent_letters (tenant_id, student_id, generated_at);
CREATE INDEX parent_letters_card_idx ON parent_letters (tenant_id, report_card_id);
