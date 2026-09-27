package com.documents.application;

import com.documents.api.DocumentsApi;
import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
import com.documents.application.service.DocumentAppService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public class DocumentsApiAdapter implements DocumentsApi {

    private final DocumentAppService documents;

    public DocumentsApiAdapter(DocumentAppService documents) {
        this.documents = documents;
    }

    @Override
    public DocumentResponseDto create(CreateDocumentRequestDto request, DocumentActor actor) {
        return documents.create(request, actor);
    }

    @Override
    public DocumentResponseDto upload(CreateDocumentRequestDto request, byte[] content, DocumentActor actor) {
        return documents.upload(request, content, actor);
    }

    @Override
    public DocumentResponseDto get(String documentId, DocumentActor actor) {
        return documents.get(documentId, actor);
    }

    @Override
    public DocumentResponseDto complete(String documentId, CompleteDocumentRequestDto request, DocumentActor actor) {
        return documents.complete(documentId, request, actor);
    }

    @Override
    public DocumentResponseDto update(String documentId, UpdateDocumentRequestDto request, DocumentActor actor) {
        return documents.update(documentId, request, actor);
    }

    @Override
    public void delete(String documentId, DocumentActor actor) {
        documents.delete(documentId, actor);
    }

    @Override
    public Page<DocumentResponseDto> list(String ownerType,
                                          String ownerId,
                                          String purpose,
                                          String product,
                                          boolean includeVersions,
                                          Pageable pageable,
                                          DocumentActor actor) {
        return documents.list(ownerType, ownerId, purpose, product, includeVersions, pageable, actor);
    }

    @Override
    public String contentUrl(String documentId, DocumentActor actor) {
        return documents.contentUrl(documentId, actor);
    }

    @Override
    public byte[] contentBytes(String documentId, DocumentActor actor) {
        return documents.contentBytes(documentId, actor);
    }

    @Override
    public List<DocumentResponseDto> listByIds(Collection<String> ids, DocumentActor actor) {
        return documents.listByIds(ids, actor);
    }

    @Override
    public int purgeDeleted(DocumentActor actor, int limit) {
        return documents.purgeDeleted(actor, limit);
    }
}
