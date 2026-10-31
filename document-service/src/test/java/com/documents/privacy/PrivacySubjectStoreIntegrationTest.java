package com.documents.privacy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PrivacySubjectStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate jdbc;
    static PrivacySubjectStore store;
    static PrivacyErasureGuard guard;
    static final com.documents.infrastructure.storage.InMemoryObjectStorageAdapter storage =
            new com.documents.infrastructure.storage.InMemoryObjectStorageAdapter();

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).schemas("documents").createSchemas(true)
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        guard = new PrivacyErasureGuard(jdbc);
        store = new PrivacySubjectStore(jdbc, guard, storage);
        storage.put("k/avatar", new byte[] {1}, "image/png");
        storage.put("k/avatar-t2", new byte[] {2}, "image/png");
        doc("avatar", "t1", "USER_PROFILE", "kc-9", "kc-9");
        doc("lease", "t1", "PROPERTY", "5", "kc-9");
        doc("other", "t1", "USER_PROFILE", "kc-2", "kc-2");
        doc("avatar-t2", "t2", "USER_PROFILE", "kc-9", "kc-9");
    }

    static void doc(String id, String tenant, String ownerType, String ownerId, String createdBy) {
        jdbc.update("""
                INSERT INTO documents.documents (id, product, tenant_id, owner_type, owner_id, purpose, storage_key,
                                                 status, created_by, created_at)
                VALUES (?, 'mybuilding', ?, ?, ?, 'AVATAR', ?, 'READY', ?, now())""",
                id, tenant, ownerType, ownerId, "k/" + id, createdBy);
    }

    @Test
    void erasesTheProfileDocumentsAndReportsTheRecordsKept() {
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> data =
                (Map<String, List<Map<String, Object>>>) store.export("t1", "kc-9").get("data");
        assertThat(data.get("profileDocuments")).extracting(d -> d.get("id")).containsExactly("avatar");
        assertThat(data.get("uploadedDocuments")).extracting(d -> d.get("id")).containsExactly("lease");
        assertThat(store.export("t1", "kc-9").get("otherTenants")).isEqualTo(List.of("t2"));

        Map<String, Object> erased = store.erase("t1", "kc-9");
        assertThat(erased.get("erased")).isEqualTo(Map.of("profileDocuments", 1));
        assertThat(((Map<?, ?>) erased.get("retained")).get("uploadedDocuments")).isEqualTo(1);
        assertThat(store.erase("t1", "kc-9").get("erased")).isEqualTo(Map.of("profileDocuments", 0));

        // Gone for good, bytes included; the other tenant's copy waits for its own call.
        assertThat(jdbc.queryForList("SELECT id FROM documents.documents ORDER BY id", String.class))
                .containsExactly("avatar-t2", "lease", "other");
        assertThat(storage.exists("k/avatar")).isFalse();
        assertThat(storage.exists("k/avatar-t2")).isTrue();
        // A new profile upload for the erased subject is refused; another tenant is unaffected.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ProfileDocumentGuard(guard).beforeCreate("t1", "USER_PROFILE", "kc-9"))
                .hasMessageContaining("SUBJECT_ERASED");
        new ProfileDocumentGuard(guard).beforeCreate("t2", "USER_PROFILE", "kc-9");
    }
}
