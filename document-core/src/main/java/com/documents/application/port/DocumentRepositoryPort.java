package com.documents.application.port;

import com.documents.domain.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepositoryPort {

    Document save(Document document);

    Optional<Document> findById(String id);

    Optional<Document> findByIdAndTenantId(String id, String tenantId);

    Optional<Document> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    boolean existsActiveSuccessor(String documentId);

    Page<Document> listActive(String product,
                              String tenantId,
                              String ownerType,
                              String ownerId,
                              String purpose,
                              boolean includeVersions,
                              Pageable pageable);

    List<Document> findByIdsAndTenantId(Collection<String> ids, String tenantId);

    /** Soft-deleted documents older than {@code deletedBefore} (for purge). */
    List<Document> findDeletedBefore(String tenantId, Instant deletedBefore, int limit);

    void hardDelete(String id);
}
