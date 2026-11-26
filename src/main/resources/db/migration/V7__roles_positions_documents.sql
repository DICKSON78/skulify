-- Student leadership positions, staff categories and the school document store.

ALTER TABLE students ADD COLUMN student_position VARCHAR(30) DEFAULT 'NONE';
ALTER TABLE teachers ADD COLUMN employee_type VARCHAR(30) DEFAULT 'TEACHER';
ALTER TABLE teachers ADD COLUMN position VARCHAR(80);

CREATE TABLE IF NOT EXISTS school_documents (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    reference       VARCHAR(40)   NOT NULL,
    title           VARCHAR(180)  NOT NULL,
    category        VARCHAR(40),
    description     VARCHAR(1000),
    file_name       VARCHAR(180),
    file_path       VARCHAR(500),
    mime_type       VARCHAR(120),
    file_size       BIGINT,
    document_date   VARCHAR(20),
    uploaded_by_id  UUID,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT school_documents_tenant_reference_key UNIQUE (tenant_id, reference)
);

CREATE INDEX IF NOT EXISTS school_documents_tenant_category_idx
    ON school_documents (tenant_id, category);
CREATE INDEX IF NOT EXISTS school_documents_student_date_idx
    ON school_documents (tenant_id, document_date);
