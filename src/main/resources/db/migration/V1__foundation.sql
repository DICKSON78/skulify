-- Foundation schema: tenants, users, authentication, audit.
-- All tenant-owned tables carry tenant_id and are protected by Hibernate's
-- @TenantId discriminator multi-tenancy (defence in depth).

CREATE TABLE tenants (
    id                UUID PRIMARY KEY,
    code              VARCHAR(30)  NOT NULL UNIQUE,
    name              VARCHAR(150) NOT NULL,
    short_name        VARCHAR(80),
    logo_url          VARCHAR(500),
    address           VARCHAR(255),
    phone             VARCHAR(20),
    email             VARCHAR(150),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    o_level_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    a_level_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT tenants_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

-- Reserved tenant that owns platform (KADE) users. It is a Hibernate "root"
-- tenant: platform users may read and manage data for every other tenant.
INSERT INTO tenants (id, code, name, status, o_level_enabled, a_level_enabled, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'SYSTEM', 'KADE System', 'ACTIVE', FALSE, FALSE, now(), now());

CREATE TABLE app_users (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL REFERENCES tenants (id),
    username             VARCHAR(60)  NOT NULL,
    phone                VARCHAR(20),
    email                VARCHAR(150),
    full_name            VARCHAR(150) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    mfa_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret           VARCHAR(255),
    last_login_at        TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT app_users_tenant_username_key UNIQUE (tenant_id, username)
);

-- Phone numbers are the parent-portal login. Keep them unique system-wide so
-- that "login by phone" is never ambiguous across schools.
CREATE UNIQUE INDEX app_users_phone_key ON app_users (phone) WHERE phone IS NOT NULL;
CREATE INDEX app_users_tenant_idx ON app_users (tenant_id);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by_hash VARCHAR(64),
    created_ip       VARCHAR(45),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    requested_ip VARCHAR(45),
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

CREATE TABLE tenant_modules (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    module_code VARCHAR(40) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT tenant_modules_key UNIQUE (tenant_id, module_code)
);

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    actor_id       UUID,
    actor_username VARCHAR(60),
    action         VARCHAR(80)  NOT NULL,
    entity_type    VARCHAR(80),
    entity_id      VARCHAR(100),
    details        JSONB,
    ip_address     VARCHAR(45),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_by     UUID
);
CREATE INDEX audit_logs_tenant_created_idx ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX audit_logs_action_idx ON audit_logs (action);

-- Audit records are append-only, even for the application database role.
CREATE OR REPLACE FUNCTION audit_logs_reject_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_mutation();

CREATE TRIGGER audit_logs_no_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_mutation();
