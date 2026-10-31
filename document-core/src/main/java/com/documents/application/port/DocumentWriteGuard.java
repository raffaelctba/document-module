package com.documents.application.port;

/**
 * Called inside the transaction that creates a document, before anything is written. The service uses it to refuse
 * a new document for a person whose data was erased, so an erasure cannot race an upload.
 */
@FunctionalInterface
public interface DocumentWriteGuard {

    DocumentWriteGuard NONE = (tenantId, ownerType, ownerId) -> {
    };

    void beforeCreate(String tenantId, String ownerType, String ownerId);
}
