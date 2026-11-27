-- Pocket Money module: schema for the append-only ledger, wallets and the
-- supporting domain records. Amounts are whole TZS shillings stored as BIGINT.
-- The existing tenants/students/app_users tables are reused for schools,
-- students and parents; pm_* tables hold money-specific state only.

CREATE TABLE pm_accounts (
    code            VARCHAR(40) PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL,
    account_type    VARCHAR(30)   NOT NULL,
    currency        VARCHAR(3)       NOT NULL DEFAULT 'TZS',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pm_accounts_type_check
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'REVENUE', 'FLOAT'))
);

INSERT INTO pm_accounts (code, name, account_type) VALUES
    ('PARENT_FUNDS_IN', 'Parent funds in (escrow)', 'LIABILITY'),
    ('STUDENT_WALLET', 'Student Pocket Money account', 'LIABILITY'),
    ('SHOP_SETTLEMENT', 'Shop settlement clearing', 'ASSET'),
    ('PLATFORM_REVENUE', 'Platform revenue', 'REVENUE'),
    ('CASH_OUT_FLOAT', 'Cash desk float', 'FLOAT');

CREATE TABLE pm_schools (
    tenant_id             UUID PRIMARY KEY REFERENCES tenants (id),
    topup_fee_percent     NUMERIC(6,5) NOT NULL DEFAULT 0.02000,
    offline_cap           BIGINT       NOT NULL DEFAULT 10000,
    refund_window_minutes INTEGER      NOT NULL DEFAULT 30,
    daily_cash_limit      BIGINT       NOT NULL DEFAULT 50000,
    currency              VARCHAR(3)      NOT NULL DEFAULT 'TZS',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pm_schools_fee_check
        CHECK (topup_fee_percent >= 0 AND topup_fee_percent <= 1),
    CONSTRAINT pm_schools_amounts_check
        CHECK (offline_cap >= 0 AND daily_cash_limit >= 0 AND refund_window_minutes >= 0)
);

CREATE TABLE pm_idempotency_keys (
    idempotency_key VARCHAR(120) PRIMARY KEY,
    scope           VARCHAR(60)  NOT NULL,
    request_hash    VARCHAR(64),
    result_ref      VARCHAR(80),
    response_json   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);
CREATE INDEX pm_idempotency_scope_idx ON pm_idempotency_keys (scope, created_at);

CREATE TABLE pm_wallets (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    student_id      UUID        NOT NULL REFERENCES students (id),
    account_code    VARCHAR(40) NOT NULL DEFAULT 'STUDENT_WALLET'
                                REFERENCES pm_accounts (code),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'TZS',
    balance         BIGINT      NOT NULL DEFAULT 0,
    status          VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    daily_limit     BIGINT      NOT NULL DEFAULT 0,
    weekly_limit    BIGINT      NOT NULL DEFAULT 0,
    monthly_limit   BIGINT      NOT NULL DEFAULT 0,
    per_txn_limit   BIGINT      NOT NULL DEFAULT 0,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_wallets_student_key UNIQUE (student_id),
    CONSTRAINT pm_wallets_status_check CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT pm_wallets_balance_check CHECK (balance >= 0),
    CONSTRAINT pm_wallets_limits_check CHECK (
        daily_limit >= 0 AND weekly_limit >= 0 AND monthly_limit >= 0 AND per_txn_limit >= 0
    )
);
CREATE INDEX pm_wallets_tenant_idx ON pm_wallets (tenant_id, status);

-- Every money movement is one or more rows here. Rows are never updated or
-- deleted; corrections are new reversing entries with their own keys.
CREATE TABLE pm_ledger_entries (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenants (id),
    wallet_id           UUID        REFERENCES pm_wallets (id),
    reference_id        UUID        NOT NULL,
    type                VARCHAR(30) NOT NULL,
    direction           VARCHAR(6)  NOT NULL,
    amount              BIGINT      NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'TZS',
    balance_after       BIGINT,
    account_code        VARCHAR(40) NOT NULL REFERENCES pm_accounts (code),
    counter_account_code VARCHAR(40) REFERENCES pm_accounts (code),
    idempotency_key     VARCHAR(120) NOT NULL
                        REFERENCES pm_idempotency_keys (idempotency_key),
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata            JSONB,
    CONSTRAINT pm_ledger_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT pm_ledger_amount_check CHECK (amount > 0),
    CONSTRAINT pm_ledger_balance_after_check CHECK (balance_after IS NULL OR balance_after >= 0)
);
CREATE INDEX pm_ledger_wallet_idx ON pm_ledger_entries (wallet_id, created_at);
CREATE INDEX pm_ledger_reference_idx ON pm_ledger_entries (reference_id);
CREATE INDEX pm_ledger_key_idx ON pm_ledger_entries (idempotency_key);

CREATE TABLE pm_cards (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    wallet_id       UUID        NOT NULL REFERENCES pm_wallets (id),
    uid_hash        VARCHAR(64) NOT NULL UNIQUE,
    status          VARCHAR(15) NOT NULL DEFAULT 'ISSUED',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    blocked_at      TIMESTAMPTZ,
    replaced_by     UUID REFERENCES pm_cards (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_cards_status_check
        CHECK (status IN ('ISSUED', 'ACTIVE', 'BLOCKED', 'REPLACED', 'LOST'))
);
CREATE UNIQUE INDEX pm_cards_one_active_per_wallet
    ON pm_cards (wallet_id) WHERE status = 'ACTIVE';
CREATE INDEX pm_cards_wallet_idx ON pm_cards (wallet_id, status);

CREATE TABLE pm_merchants (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID        NOT NULL REFERENCES tenants (id),
    code                    VARCHAR(40) NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    category                VARCHAR(60),
    status                  VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    settlement_account_code VARCHAR(40) NOT NULL DEFAULT 'SHOP_SETTLEMENT'
                            REFERENCES pm_accounts (code),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_merchants_tenant_code_key UNIQUE (tenant_id, code),
    CONSTRAINT pm_merchants_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE pm_terminals (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id),
    merchant_id       UUID        NOT NULL REFERENCES pm_merchants (id),
    code              VARCHAR(40) NOT NULL,
    device_key_hash   VARCHAR(64) NOT NULL,
    status            VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_terminals_tenant_code_key UNIQUE (tenant_id, code),
    CONSTRAINT pm_terminals_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'RETIRED'))
);

CREATE TABLE pm_parent_student_links (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    parent_user_id  UUID        NOT NULL REFERENCES app_users (id),
    student_id      UUID        NOT NULL REFERENCES students (id),
    relationship    VARCHAR(30) NOT NULL DEFAULT 'PARENT',
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_parent_student_link_key UNIQUE (parent_user_id, student_id)
);
CREATE INDEX pm_parent_student_student_idx
    ON pm_parent_student_links (student_id) WHERE active = TRUE;

CREATE TABLE pm_topups (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id),
    parent_user_id    UUID        NOT NULL REFERENCES app_users (id),
    wallet_id         UUID        NOT NULL REFERENCES pm_wallets (id),
    gross_amount      BIGINT      NOT NULL,
    fee_amount        BIGINT      NOT NULL DEFAULT 0,
    net_amount        BIGINT      NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'TZS',
    provider          VARCHAR(30) NOT NULL,
    provider_ref      VARCHAR(100),
    status            VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(120) NOT NULL UNIQUE
                      REFERENCES pm_idempotency_keys (idempotency_key),
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata          JSONB,
    CONSTRAINT pm_topups_amount_check
        CHECK (gross_amount > 0 AND fee_amount >= 0 AND net_amount > 0),
    CONSTRAINT pm_topups_status_check
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED'))
);
CREATE INDEX pm_topups_wallet_idx ON pm_topups (wallet_id, created_at);

CREATE TABLE pm_purchases (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id),
    wallet_id         UUID        NOT NULL REFERENCES pm_wallets (id),
    merchant_id       UUID        NOT NULL REFERENCES pm_merchants (id),
    terminal_id       UUID        NOT NULL REFERENCES pm_terminals (id),
    amount            BIGINT      NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'TZS',
    status            VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    items             JSONB,
    offline           BOOLEAN     NOT NULL DEFAULT FALSE,
    nonce             VARCHAR(80),
    request_timestamp TIMESTAMPTZ,
    idempotency_key   VARCHAR(120) NOT NULL UNIQUE
                      REFERENCES pm_idempotency_keys (idempotency_key),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_purchases_amount_check CHECK (amount > 0),
    CONSTRAINT pm_purchases_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'REVERSED', 'OFFLINE_PENDING'))
);
CREATE INDEX pm_purchases_wallet_idx ON pm_purchases (wallet_id, created_at);

CREATE TABLE pm_spending_rules (
    wallet_id             UUID PRIMARY KEY REFERENCES pm_wallets (id),
    daily_limit           BIGINT   NOT NULL DEFAULT 0,
    per_txn_limit         BIGINT   NOT NULL DEFAULT 0,
    blocked_categories    JSONB,
    pin_threshold         BIGINT   NOT NULL DEFAULT 0,
    daily_cash_limit      BIGINT   NOT NULL DEFAULT 0,
    pin_failed_attempts   INTEGER  NOT NULL DEFAULT 0,
    pin_locked_until      TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_spending_rules_limits_check CHECK (
        daily_limit >= 0 AND per_txn_limit >= 0 AND pin_threshold >= 0
        AND daily_cash_limit >= 0 AND pin_failed_attempts >= 0
    )
);

CREATE TABLE pm_cash_outs (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenants (id),
    wallet_id           UUID        NOT NULL REFERENCES pm_wallets (id),
    amount              BIGINT      NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'TZS',
    approved_by_parent  BOOLEAN     NOT NULL DEFAULT FALSE,
    parent_approval_id  UUID,
    handled_by_bursar   UUID        REFERENCES app_users (id),
    status              VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    payout_reference    VARCHAR(60),
    idempotency_key     VARCHAR(120) NOT NULL UNIQUE
                        REFERENCES pm_idempotency_keys (idempotency_key),
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pm_cash_outs_amount_check CHECK (amount > 0),
    CONSTRAINT pm_cash_outs_status_check
        CHECK (status IN ('REQUESTED', 'APPROVED', 'PAID', 'DECLINED', 'REVERSED'))
);
CREATE INDEX pm_cash_outs_wallet_idx ON pm_cash_outs (wallet_id, status);

CREATE TABLE pm_audit_log (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    actor_user_id UUID        REFERENCES app_users (id),
    action        VARCHAR(80) NOT NULL,
    target_type   VARCHAR(60),
    target_id     VARCHAR(80),
    metadata      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX pm_audit_tenant_idx ON pm_audit_log (tenant_id, created_at);

CREATE OR REPLACE FUNCTION pm_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pm_ledger_entries_no_update_delete
    BEFORE UPDATE OR DELETE ON pm_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION pm_reject_mutation();
CREATE TRIGGER pm_ledger_entries_no_truncate
    BEFORE TRUNCATE ON pm_ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION pm_reject_mutation();
CREATE TRIGGER pm_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON pm_audit_log
    FOR EACH ROW EXECUTE FUNCTION pm_reject_mutation();
CREATE TRIGGER pm_audit_log_no_truncate
    BEFORE TRUNCATE ON pm_audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION pm_reject_mutation();

REVOKE UPDATE, DELETE, TRUNCATE ON pm_ledger_entries FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON pm_audit_log FROM PUBLIC;
