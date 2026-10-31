-- Subjects erased in a tenant. Erasure writes the row under an exclusive advisory lock; every write by the subject
-- takes the same lock shared and refuses once the row exists, so an erasure cannot race a live write.
CREATE TABLE IF NOT EXISTS documents.privacy_erasures (
    tenant_id  VARCHAR(128) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    erased_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, subject_id)
);
