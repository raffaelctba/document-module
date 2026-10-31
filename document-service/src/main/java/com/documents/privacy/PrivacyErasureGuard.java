package com.documents.privacy;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps an erasure from racing a live write. Erasure takes an exclusive transaction lock on the subject and leaves
 * a tombstone; every write by the subject takes the same lock shared and refuses once the tombstone exists. So a
 * write either commits before the erasure starts (and is erased by it) or is refused with 410 SUBJECT_ERASED.
 * Both must run inside the caller's transaction: the lock is released at its commit.
 *
 * <p>Where Flyway is off in this process and the tombstone table has not been deployed yet, writes keep working
 * (they still take the lock) and erasure fails with ERASURE_TABLE_MISSING, so the host records the module as failed
 * rather than as erased.
 */
@Component
public class PrivacyErasureGuard {

    private final JdbcTemplate jdbc;

    public PrivacyErasureGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertWritable(String tenantId, String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return;
        }
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock_shared(hashtextextended(?, 0))) l",
                Integer.class, key(tenantId, subjectId));
        if (!tableDeployed()) {
            return;
        }
        Boolean erased = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM documents.privacy_erasures WHERE tenant_id = ? AND subject_id = ?)",
                Boolean.class, nz(tenantId), subjectId);
        if (Boolean.TRUE.equals(erased)) {
            throw new ResponseStatusException(HttpStatus.GONE, "SUBJECT_ERASED");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void beginErasure(String tenantId, String subjectId) {
        if (!tableDeployed()) {
            throw new IllegalStateException("ERASURE_TABLE_MISSING: documents.privacy_erasures is not deployed");
        }
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) l",
                Integer.class, key(tenantId, subjectId));
        jdbc.update("""
                INSERT INTO documents.privacy_erasures (tenant_id, subject_id) VALUES (?, ?)
                ON CONFLICT (tenant_id, subject_id) DO NOTHING""", nz(tenantId), subjectId);
    }

    private boolean tableDeployed() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('documents.privacy_erasures') IS NOT NULL", Boolean.class));
    }

    private static String key(String tenantId, String subjectId) {
        return "privacy-erasure:" + nz(tenantId) + ":" + subjectId;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
