package com.documents.controller;

import com.documents.api.DocumentsApi;
import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
import com.documents.config.DocumentsProperties;
import com.myproperty.platform.security.IdentityContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/documents")
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    private final DocumentsApi documentsApi;
    private final DocumentsProperties properties;

    public DocumentController(DocumentsApi documentsApi, DocumentsProperties properties) {
        this.documentsApi = documentsApi;
        this.properties = properties;
    }

    @PostMapping
    @PreAuthorize("@documentAccess.canWrite(authentication, #request)")
    public ResponseEntity<DocumentResponseDto> create(@RequestBody CreateDocumentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentsApi.create(request, actor()));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("@documentAccess.canRead(authentication, #documentId)")
    public DocumentResponseDto get(@PathVariable String documentId) {
        return documentsApi.get(documentId, actor());
    }

    @GetMapping
    @PreAuthorize("@documentAccess.canList(authentication, #ownerType, #ownerId)")
    public Page<DocumentResponseDto> list(
            @RequestParam String ownerType,
            @RequestParam String ownerId,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String product,
            @RequestParam(required = false, defaultValue = "false") boolean includeVersions,
            @PageableDefault(size = 50) Pageable pageable) {
        return documentsApi.list(ownerType, ownerId, purpose, product, includeVersions, pageable, actor());
    }

    @PostMapping("/{documentId}/complete")
    @PreAuthorize("@documentAccess.canWriteExisting(authentication, #documentId)")
    public DocumentResponseDto complete(
            @PathVariable String documentId,
            @RequestBody(required = false) CompleteDocumentRequestDto request) {
        return documentsApi.complete(documentId, request, actor());
    }

    @PatchMapping("/{documentId}")
    @PreAuthorize("@documentAccess.canWriteExisting(authentication, #documentId)")
    public DocumentResponseDto update(
            @PathVariable String documentId,
            @RequestBody UpdateDocumentRequestDto request) {
        return documentsApi.update(documentId, request, actor());
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("@documentAccess.canWriteExisting(authentication, #documentId)")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        documentsApi.delete(documentId, actor());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}/content")
    @PreAuthorize("@documentAccess.canRead(authentication, #documentId)")
    public ResponseEntity<Void> content(@PathVariable String documentId) {
        String url = documentsApi.contentUrl(documentId, actor());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private DocumentActor actor() {
        IdentityContext identity = IdentityContext.require();
        String tenantId = identity.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = properties.productDefault();
        }
        return new DocumentActor(identity.userId(), identity.companyId(), tenantId, identity.roles());
    }
}
