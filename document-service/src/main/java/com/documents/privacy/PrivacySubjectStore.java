package com.documents.privacy;

import com.documents.application.port.ObjectStoragePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents about a subject, in the caller's tenant: the ones that belong to their profile (owner
 * {@code USER_PROFILE}, e.g. the avatar) and the ones they uploaded elsewhere. Export lists metadata and ids (the
 * host fetches bytes it may show). Erasure hard-deletes the profile documents and their bytes at once, not
 * after the soft-delete retention. Uploads attached to a property, lease or work order are that record's evidence and
 * are kept: they are reported as retained so the privacy officer can answer the person.
 */
@Repository
public class PrivacySubjectStore {

    static final String SERVICE = "document";
    static final String PROFILE = "USER_PROFILE";

    private static final String COLUMNS = """
            id, product, owner_type AS "ownerType", owner_id AS "ownerId", purpose, version,
            original_name AS "originalName", content_type AS "contentType", size_bytes AS "sizeBytes",
            created_at AS "createdAt", deleted_at AS "deletedAt\"""";

    private final JdbcTemplate jdbc;
    private final PrivacyErasureGuard guard;
    private final ObjectStoragePort storage;

    /** How long a signed upload URL stays usable: after it, a pending profile upload can no longer arrive. */
    private final java.time.Duration uploadWindow;

    @org.springframework.beans.factory.annotation.Autowired
    public PrivacySubjectStore(JdbcTemplate jdbc, PrivacyErasureGuard guard, ObjectStoragePort storage,
                               com.documents.config.DocumentsProperties properties) {
        this(jdbc, guard, storage, properties.storage().signedUrlTtl());
    }

    PrivacySubjectStore(JdbcTemplate jdbc, PrivacyErasureGuard guard, ObjectStoragePort storage,
                        java.time.Duration uploadWindow) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.storage = storage;
        this.uploadWindow = uploadWindow;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(String tenantId, String subjectId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profileDocuments", jdbc.queryForList("SELECT " + COLUMNS + """
                 FROM documents.documents WHERE tenant_id = ? AND owner_type = ? AND owner_id = ?
                ORDER BY created_at, id""", tenantId, PROFILE, subjectId));
        data.put("uploadedDocuments", jdbc.queryForList("SELECT " + COLUMNS + """
                 FROM documents.documents WHERE tenant_id = ? AND created_by = ?
                  AND NOT (owner_type = ? AND owner_id = ?)
                ORDER BY created_at, id""", tenantId, subjectId, PROFILE, subjectId));
        return envelope(subjectId, "data", data, otherTenants(tenantId, subjectId));
    }

    /**
     * Removes the subject's profile documents for good: the stored bytes first, then the rows (tags cascade),
     * including rows already soft-deleted and waiting for the purge. A storage failure fails the whole erasure, so
     * the host records it as failed and retries; a repeat finds nothing left. Uploads they made to other records
     * are kept and reported as retained.
     */
    @Transactional
    public Map<String, Object> erase(String tenantId, String subjectId) {
        guard.beginErasure(tenantId, subjectId);
        // A profile document created but not uploaded yet may still receive its bytes through a signed URL handed
        // out before the erasure. Within that window its row stays (soft-deleted), so the late upload finds it and
        // is refused with its bytes removed; the erasure reports it pending and the host calls again later.
        java.sql.Timestamp windowStart = java.sql.Timestamp.from(Instant.now().minus(uploadWindow));
        List<Map<String, Object>> profile = jdbc.queryForList("""
                SELECT id, storage_key AS "storageKey",
                       (status = 'PENDING_UPLOAD' AND created_at > ?) AS "uploadOpen"
                FROM documents.documents WHERE tenant_id = ? AND owner_type = ? AND owner_id = ?""",
                windowStart, tenantId, PROFILE, subjectId);
        int deleted = 0;
        int awaiting = 0;
        for (Map<String, Object> row : profile) {
            storage.delete((String) row.get("storageKey"));
            if (Boolean.TRUE.equals(row.get("uploadOpen"))) {
                awaiting += jdbc.update("""
                        UPDATE documents.documents SET deleted_at = COALESCE(deleted_at, now()), original_name = 'erased'
                        WHERE id = ?""", row.get("id"));
            } else {
                deleted += jdbc.update("DELETE FROM documents.documents WHERE id = ?", row.get("id"));
            }
        }
        Integer kept = jdbc.queryForObject("""
                SELECT count(*) FROM documents.documents
                WHERE tenant_id = ? AND created_by = ? AND owner_type <> ? AND deleted_at IS NULL""",
                Integer.class, tenantId, subjectId, PROFILE);
        Map<String, Object> out = envelope(subjectId, "erased", Map.of("profileDocuments", deleted),
                otherTenants(tenantId, subjectId));
        out.put("pending", Map.of("awaitingUpload", awaiting,
                "reason", "profile uploads whose signed URL is still valid are refused and removed if they arrive"));
        out.put("retained", Map.of("uploadedDocuments", kept == null ? 0 : kept,
                "reason", "evidence of the property, lease or work order record they were attached to"));
        return out;
    }

    /** Other tenants holding the subject's profile documents or uploads: ids only, the host calls each in turn. */
    List<String> otherTenants(String tenantId, String subjectId) {
        return jdbc.queryForList("""
                SELECT DISTINCT tenant_id FROM documents.documents
                WHERE tenant_id <> ? AND ((owner_type = ? AND owner_id = ?) OR created_by = ?)
                ORDER BY 1""", String.class, tenantId, PROFILE, subjectId, subjectId);
    }

    private static Map<String, Object> envelope(String subjectId, String key, Object value, List<String> others) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", SERVICE);
        out.put("subjectId", subjectId);
        out.put("at", Instant.now());
        out.put(key, value);
        out.put("otherTenants", others);
        return out;
    }
}
