package com.documents.application.port;

import com.documents.domain.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface DocumentRepositoryPort {

    Document save(Document document);

    Optional<Document> findById(String id);

    boolean existsActiveSuccessor(String documentId);

    Page<Document> listActive(String product,
                              String tenantId,
                              String ownerType,
                              String ownerId,
                              String purpose,
                              boolean includeVersions,
                              Pageable pageable);
}
