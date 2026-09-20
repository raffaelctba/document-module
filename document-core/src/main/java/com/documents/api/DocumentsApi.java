package com.documents.api;

import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Published language of the document module. Hosts must depend only on this type
 * and {@code com.documents.api} DTOs/exceptions.
 *
 * <p>The module owns attachments (pattern A): a document is bytes in object
 * storage, a metadata row, and a polymorphic owner link. Callers know a
 * {@code documentId} (and maybe a purpose like COVER / GALLERY), not buckets
 * or MIME types.
 */
public interface DocumentsApi {

    DocumentResponseDto create(CreateDocumentRequestDto request, DocumentActor actor);

    /**
     * In-process create + store bytes + mark ready. Hosts with a multipart body
     * use this instead of the signed-URL create/complete dance.
     */
    DocumentResponseDto upload(CreateDocumentRequestDto request, byte[] content, DocumentActor actor);

    DocumentResponseDto get(String documentId, DocumentActor actor);

    DocumentResponseDto complete(String documentId, CompleteDocumentRequestDto request, DocumentActor actor);

    DocumentResponseDto update(String documentId, UpdateDocumentRequestDto request, DocumentActor actor);

    void delete(String documentId, DocumentActor actor);

    /**
     * Lists current versions for the owner. Pass {@code includeVersions=true} to
     * include superseded documents in the same owner/purpose chain.
     */
    Page<DocumentResponseDto> list(String ownerType,
                                   String ownerId,
                                   String purpose,
                                   String product,
                                   boolean includeVersions,
                                   Pageable pageable,
                                   DocumentActor actor);

    default Page<DocumentResponseDto> list(String ownerType,
                                           String ownerId,
                                           String purpose,
                                           String product,
                                           Pageable pageable,
                                           DocumentActor actor) {
        return list(ownerType, ownerId, purpose, product, false, pageable, actor);
    }

    String contentUrl(String documentId, DocumentActor actor);

    byte[] contentBytes(String documentId, DocumentActor actor);
}
