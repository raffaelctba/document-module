package com.documents.controller;

import com.documents.api.DocumentsApi;
import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentActors;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/documents")
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    private final DocumentsApi documentsApi;

    public DocumentController(DocumentsApi documentsApi) {
        this.documentsApi = documentsApi;
    }

    @PostMapping
    @PreAuthorize("@documentAccess.canWrite(authentication, #request)")
    public ResponseEntity<DocumentResponseDto> create(@RequestBody CreateDocumentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentsApi.create(request, actor()));
    }

    // Record-level checks (tenant, company, owner) run in document-core and answer 404.
    @GetMapping("/{documentId}")
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

    @PostMapping("/batch")
    public List<DocumentResponseDto> listByIds(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body == null ? List.of() : body.getOrDefault("ids", List.of());
        return documentsApi.listByIds(ids, actor());
    }

    @PostMapping("/{documentId}/complete")
    public DocumentResponseDto complete(
            @PathVariable String documentId,
            @RequestBody(required = false) CompleteDocumentRequestDto request) {
        return documentsApi.complete(documentId, request, actor());
    }

    @PatchMapping("/{documentId}")
    public DocumentResponseDto update(
            @PathVariable String documentId,
            @RequestBody UpdateDocumentRequestDto request) {
        return documentsApi.update(documentId, request, actor());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        documentsApi.delete(documentId, actor());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{documentId}/content/bytes", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] contentBytes(@PathVariable String documentId) {
        return documentsApi.contentBytes(documentId, actor());
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<Void> content(@PathVariable String documentId) {
        String url = documentsApi.contentUrl(documentId, actor());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @PostMapping("/jobs/purge-deleted")
    @PreAuthorize("hasRole('HOST_DOCUMENT_WRITE')")
    public Map<String, Integer> purgeDeleted(
            @RequestParam(required = false, defaultValue = "100") int limit) {
        int purged = documentsApi.purgeDeleted(actor(), limit);
        return Map.of("purged", purged);
    }

    private DocumentActor actor() {
        IdentityContext identity = IdentityContext.require();
        return DocumentActors.require(
                identity.userId(),
                identity.companyId(),
                identity.tenantId(),
                identity.roles() == null ? Set.of() : identity.roles());
    }
}
