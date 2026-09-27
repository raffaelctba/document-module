CREATE SCHEMA IF NOT EXISTS documents;

CREATE TABLE IF NOT EXISTS documents.documents (
    id                      VARCHAR(36)  PRIMARY KEY,
    product                 VARCHAR(64)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    company_id              VARCHAR(64),
    owner_type              VARCHAR(64)  NOT NULL,
    owner_id                VARCHAR(64)  NOT NULL,
    purpose                 VARCHAR(64)  NOT NULL,
    version                 INT          NOT NULL DEFAULT 1,
    supersedes_document_id  VARCHAR(36),
    storage_key             VARCHAR(512) NOT NULL,
    original_name           VARCHAR(255),
    content_type            VARCHAR(127),
    size_bytes              BIGINT,
    checksum                VARCHAR(128),
    status                  VARCHAR(32)  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    deleted_at              TIMESTAMPTZ,
    idempotency_key         VARCHAR(128)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_documents_tenant_idempotency
    ON documents.documents (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documents_tenant_owner
    ON documents.documents (tenant_id, owner_type, owner_id, purpose);

CREATE INDEX IF NOT EXISTS idx_documents_tenant_status
    ON documents.documents (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_documents_supersedes
    ON documents.documents (supersedes_document_id)
    WHERE supersedes_document_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documents_deleted
    ON documents.documents (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS documents.document_tags (
    document_id VARCHAR(36) NOT NULL REFERENCES documents.documents (id) ON DELETE CASCADE,
    tag         VARCHAR(64) NOT NULL,
    PRIMARY KEY (document_id, tag)
);
