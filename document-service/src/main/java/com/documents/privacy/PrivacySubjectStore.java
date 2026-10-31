package com.documents.privacy;

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
 * host fetches bytes it may show). Erasure deletes the profile documents; the purge job removes their bytes after
 * the soft-delete retention. Uploads attached to a property, lease or work order are that record's evidence and
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

    public PrivacySubjectStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        return envelope(subjectId, "data", data);
    }

    @Transactional
    public Map<String, Object> erase(String tenantId, String subjectId) {
        int profile = jdbc.update("""
                UPDATE documents.documents SET deleted_at = now()
                WHERE tenant_id = ? AND owner_type = ? AND owner_id = ? AND deleted_at IS NULL""",
                tenantId, PROFILE, subjectId);
        Integer kept = jdbc.queryForObject("""
                SELECT count(*) FROM documents.documents
                WHERE tenant_id = ? AND created_by = ? AND owner_type <> ? AND deleted_at IS NULL""",
                Integer.class, tenantId, subjectId, PROFILE);
        Map<String, Object> out = envelope(subjectId, "erased", Map.of("profileDocuments", profile));
        out.put("retained", Map.of("uploadedDocuments", kept == null ? 0 : kept,
                "reason", "evidence of the property, lease or work order record they were attached to"));
        return out;
    }

    private static Map<String, Object> envelope(String subjectId, String key, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", SERVICE);
        out.put("subjectId", subjectId);
        out.put("at", Instant.now());
        out.put(key, value);
        return out;
    }
}
