-- Teacher cash requests approved by a guardian and paid out to the teacher's phone.

CREATE TABLE IF NOT EXISTS cash_withdrawal_requests (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    student_id        UUID          NOT NULL REFERENCES students (id),
    teacher_id        UUID          NOT NULL REFERENCES teachers (id),
    amount            DECIMAL(14,2) NOT NULL,
    reason            VARCHAR(240)  NOT NULL,
    status            VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    requested_at      VARCHAR(30),
    decided_at        VARCHAR(30),
    approved_by       UUID,
    payout_reference  VARCHAR(60),
    agent_code        VARCHAR(60),
    phone_number      VARCHAR(40),
    note              VARCHAR(300),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        UUID,
    updated_by        UUID
);

CREATE INDEX IF NOT EXISTS idx_cash_requests_student
    ON cash_withdrawal_requests (tenant_id, student_id);
CREATE INDEX IF NOT EXISTS idx_cash_requests_teacher
    ON cash_withdrawal_requests (tenant_id, teacher_id);
CREATE INDEX IF NOT EXISTS idx_cash_requests_status
    ON cash_withdrawal_requests (tenant_id, status);
