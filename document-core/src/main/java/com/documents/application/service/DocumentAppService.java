package com.documents.application.service;

import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
import com.documents.api.exception.DocumentNotFoundException;
import com.documents.api.exception.OwnerNotAllowedException;
import com.documents.api.exception.OwnerVerificationException;
import com.myproperty.platform.common.time.ClockPort;
import com.documents.application.port.DocumentRepositoryPort;
import com.documents.application.port.ObjectStoragePort;
import com.documents.api.DocumentAccessPort;
import com.documents.api.DocumentAccessRules;
import com.documents.api.OwnerLookupPort;
import com.documents.domain.event.DocumentDeletedEvent;
import com.documents.domain.event.DocumentReadyEvent;
import com.documents.config.DocumentsProperties;
import com.documents.domain.model.Document;
import com.documents.domain.model.OwnerRef;
import com.documents.domain.service.DocumentDomainService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.documents.api.exception.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Transactional
public class DocumentAppService {

    // Access rules: DocumentAccessRules (trusted application capabilities, or the caller's own profile).

    private final DocumentRepositoryPort documents;
    private final ObjectStoragePort objectStorage;
    private final OwnerLookupPort ownerLookup;
    private final ClockPort clock;
    private final DocumentDomainService domain;
    private final DocumentsProperties properties;
    private final DocumentAccessPort access;
    private final ApplicationEventPublisher events;

    public DocumentAppService(DocumentRepositoryPort documents,
                              ObjectStoragePort objectStorage,
                              OwnerLookupPort ownerLookup,
                              ClockPort clock,
                              DocumentDomainService domain,
                              DocumentsProperties properties) {
        this(documents, objectStorage, ownerLookup, clock, domain, properties, new DocumentAccessPort() {}, null);
    }

    public DocumentAppService(DocumentRepositoryPort documents,
                              ObjectStoragePort objectStorage,
                              OwnerLookupPort ownerLookup,
                              ClockPort clock,
                              DocumentDomainService domain,
                              DocumentsProperties properties,
                              DocumentAccessPort access,
                              ApplicationEventPublisher events) {
        this.documents = documents;
        this.objectStorage = objectStorage;
        this.ownerLookup = ownerLookup;
        this.clock = clock;
        this.domain = domain;
        this.properties = properties;
        this.access = access == null ? new DocumentAccessPort() {} : access;
        this.events = events;
    }

    public DocumentResponseDto create(CreateDocumentRequestDto request, DocumentActor actor) {
        requireActor(actor);
        OwnerRef owner = new OwnerRef(request.ownerType(), request.ownerId());
        assertCanWrite(actor, owner);

        String product;
        try {
            product = domain.resolveProduct(request.product());
            domain.assertOwnerAllowed(product, owner.type());
            domain.assertPurposeAllowed(product, request.purpose());
            domain.assertContentAllowed(request.contentType(), request.sizeBytes());
        } catch (IllegalArgumentException ex) {
            throw new OwnerNotAllowedException(ex.getMessage());
        }
        verifyOwner(product, owner, actor);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = documents.findByTenantIdAndIdempotencyKey(actor.tenantId(), request.idempotencyKey());
            if (existing.isPresent()) {
                Document doc = existing.get();
                if (!doc.deleted()) {
                    return toDtoWithUrls(doc);
                }
            }
        }

        int version = 1;
        String supersedesDocumentId = request.supersedesDocumentId();
        if (supersedesDocumentId != null) {
            Document previous = requireVisible(supersedesDocumentId, actor);
            if (!previous.owner().equals(owner)
                    || !previous.product().equals(product)
                    || !previous.purpose().equals(request.purpose())) {
                throw new IllegalArgumentException(
                        "supersedesDocumentId must refer to the same owner, product, and purpose");
            }
            if (documents.existsActiveSuccessor(previous.id())) {
                throw new IllegalStateException("Document is already superseded: " + previous.id());
            }
            version = previous.version() + 1;
            supersedesDocumentId = previous.id();
        }

        String documentId = UUID.randomUUID().toString();
        String filename = domain.sanitizeFilename(request.filename());
        String storageKey = domain.storageKey(product, actor.tenantId(), owner, documentId);
        Document document = Document.create(
                product,
                actor.tenantId(),
                actor.companyId(),
                owner,
                request.purpose(),
                storageKey,
                filename,
                request.contentType(),
                request.sizeBytes(),
                request.tags(),
                actor.userId(),
                clock.now(),
                version,
                supersedesDocumentId);
        if (request.idempotencyKey() != null) {
            document = Document.builder()
                    .withId(document.id())
                    .withProduct(document.product())
                    .withTenantId(document.tenantId())
                    .withCompanyId(document.companyId())
                    .withOwner(document.owner())
                    .withPurpose(document.purpose())
                    .withVersion(document.version())
                    .withSupersedesDocumentId(document.supersedesDocumentId())
                    .withStorageKey(document.storageKey())
                    .withOriginalName(document.originalName())
                    .withContentType(document.contentType())
                    .withSizeBytes(document.sizeBytes())
                    .withChecksum(document.checksum())
                    .withStatus(document.status())
                    .withTags(document.tags())
                    .withCreatedBy(document.createdBy())
                    .withCreatedAt(document.createdAt())
                    .withDeletedAt(document.deletedAt())
                    .withIdempotencyKey(request.idempotencyKey())
                    .build();
        }
        Document saved = documents.save(document);
        return toDto(saved, putUrl(saved), null);
    }

    public DocumentResponseDto upload(CreateDocumentRequestDto request, byte[] content, DocumentActor actor) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content is required");
        }
        DocumentResponseDto created = create(request, actor);
        Document document = documents.findByIdAndTenantId(created.id(), actor.tenantId())
                .orElseThrow(() -> new DocumentNotFoundException(created.id()));
        String contentType = request.contentType() != null ? request.contentType() : document.contentType();
        objectStorage.put(document.storageKey(), content, contentType);
        return complete(created.id(),
                new CompleteDocumentRequestDto(contentType, (long) content.length, sha256(content)),
                actor);
    }

    public DocumentResponseDto get(String documentId, DocumentActor actor) {
        Document document = requireVisible(documentId, actor);
        return toDtoWithUrls(document);
    }

    public DocumentResponseDto complete(String documentId, CompleteDocumentRequestDto request, DocumentActor actor) {
        Document document = requireWritable(documentId, actor);
        if (!objectStorage.exists(document.storageKey())) {
            throw new IllegalStateException("Object has not been uploaded: " + documentId);
        }
        CompleteDocumentRequestDto body = request == null
                ? new CompleteDocumentRequestDto(null, null, null)
                : request;
        try {
            domain.assertContentAllowed(
                    body.contentType() != null ? body.contentType() : document.contentType(),
                    body.sizeBytes() != null ? body.sizeBytes() : document.sizeBytes());
        } catch (IllegalArgumentException ex) {
            throw new OwnerNotAllowedException(ex.getMessage());
        }
        document.markReady(body.sizeBytes(), body.checksum(), body.contentType());
        Document saved = documents.save(document);
        publish(new DocumentReadyEvent(
                saved.id(), saved.tenantId(), saved.owner().type(), saved.owner().id(),
                saved.purpose(), clock.now()));
        return toDtoWithUrls(saved);
    }

    public DocumentResponseDto update(String documentId, UpdateDocumentRequestDto request, DocumentActor actor) {
        if (request == null) {
            throw new IllegalArgumentException("purpose or tags is required");
        }
        Document document = requireWritable(documentId, actor);
        if (request.purpose() != null) {
            try {
                domain.assertPurposeAllowed(document.product(), request.purpose());
            } catch (IllegalArgumentException ex) {
                throw new OwnerNotAllowedException(ex.getMessage());
            }
        }
        document.reclassify(request.purpose(), request.tags());
        return toDtoWithUrls(documents.save(document));
    }

    public void delete(String documentId, DocumentActor actor) {
        Document document = requireWritable(documentId, actor);
        document.softDelete(clock.now());
        documents.save(document);
        publish(new DocumentDeletedEvent(
                document.id(), document.tenantId(), document.owner().type(), document.owner().id(), clock.now()));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> listByIds(Collection<String> ids, DocumentActor actor) {
        requireActor(actor);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DocumentResponseDto> out = new ArrayList<>();
        for (Document document : documents.findByIdsAndTenantId(ids, actor.tenantId())) {
            if (document.deleted() || !sameCompany(actor, document.companyId())) {
                continue;
            }
            try {
                assertCanRead(actor, document.owner());
                out.add(toDtoWithUrls(document));
            } catch (AccessDeniedException ignored) {
                // skip unauthorized ids silently
            }
        }
        return List.copyOf(out);
    }

    /** Hard-deletes soft-deleted documents older than retention; removes object storage bytes. */
    public int purgeDeleted(DocumentActor actor, int limit) {
        requireActor(actor);
        if (!DocumentAccessRules.trustedWriter(actor)) {
            throw new AccessDeniedException("purgeDeleted requires " + DocumentAccessRules.HOST_WRITE);
        }
        Instant cutoff = clock.now().minus(properties.softDeleteRetention());
        int max = limit < 1 ? 100 : Math.min(limit, 500);
        List<Document> doomed = documents.findDeletedBefore(actor.tenantId(), cutoff, max);
        int count = 0;
        for (Document document : doomed) {
            try {
                objectStorage.delete(document.storageKey());
            } catch (RuntimeException ignored) {
                // continue purge of metadata
            }
            documents.hardDelete(document.id());
            count++;
        }
        return count;
    }

    private void publish(Object event) {
        if (events != null) {
            events.publishEvent(event);
        }
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponseDto> list(String ownerType,
                                          String ownerId,
                                          String purpose,
                                          String product,
                                          Pageable pageable,
                                          DocumentActor actor) {
        return list(ownerType, ownerId, purpose, product, false, pageable, actor);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponseDto> list(String ownerType,
                                          String ownerId,
                                          String purpose,
                                          String product,
                                          boolean includeVersions,
                                          Pageable pageable,
                                          DocumentActor actor) {
        requireActor(actor);
        OwnerRef owner = new OwnerRef(ownerType, ownerId);
        assertCanRead(actor, owner);
        String resolvedProduct;
        try {
            resolvedProduct = domain.resolveProduct(product);
            domain.assertOwnerAllowed(resolvedProduct, owner.type());
            if (purpose != null && !purpose.isBlank()) {
                domain.assertPurposeAllowed(resolvedProduct, purpose);
            }
        } catch (IllegalArgumentException ex) {
            throw new OwnerNotAllowedException(ex.getMessage());
        }
        String normalizedPurpose = purpose == null || purpose.isBlank() ? null : purpose.trim().toUpperCase();
        return documents.listActive(
                        resolvedProduct,
                        actor.tenantId(),
                        owner.type(),
                        owner.id(),
                        normalizedPurpose,
                        includeVersions,
                        clamp(pageable))
                .map(this::toDtoWithUrls);
    }

    public String contentUrl(String documentId, DocumentActor actor) {
        Document document = requireVisible(documentId, actor);
        if (document.pendingUpload() && objectStorage.exists(document.storageKey())) {
            document.markReady(document.sizeBytes(), document.checksum(), document.contentType());
            document = documents.save(document);
        }
        if (!document.ready()) {
            throw new IllegalStateException("Document is not ready: " + documentId);
        }
        return objectStorage.presignGet(document.storageKey(), ttl()).url();
    }

    public byte[] contentBytes(String documentId, DocumentActor actor) {
        Document document = requireVisible(documentId, actor);
        if (document.pendingUpload() && objectStorage.exists(document.storageKey())) {
            document.markReady(document.sizeBytes(), document.checksum(), document.contentType());
            document = documents.save(document);
        }
        if (!document.ready()) {
            throw new IllegalStateException("Document is not ready: " + documentId);
        }
        return objectStorage.get(document.storageKey());
    }

    /**
     * The record exists in the caller's tenant, is not deleted, matches the caller's company and
     * the caller may read it. Otherwise the record is reported as missing, so a caller cannot
     * probe which ids exist.
     */
    private Document requireVisible(String documentId, DocumentActor actor) {
        requireActor(actor);
        Document document = documents.findByIdAndTenantId(documentId, actor.tenantId())
                .filter(candidate -> !candidate.deleted())
                .filter(candidate -> sameCompany(actor, candidate.companyId()))
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        try {
            assertCanRead(actor, document.owner());
        } catch (AccessDeniedException ex) {
            throw new DocumentNotFoundException(documentId);
        }
        return document;
    }

    private Document requireWritable(String documentId, DocumentActor actor) {
        Document document = requireVisible(documentId, actor);
        assertCanWrite(actor, document.owner());
        return document;
    }

    private void verifyOwner(String product, OwnerRef owner, DocumentActor actor) {
        if (!properties.ownerLookup().enabled()) {
            return;
        }
        OwnerLookupPort.OwnerRecord record = ownerLookup.find(product, owner.type(), owner.id())
                .orElseThrow(() -> new OwnerVerificationException(owner.type(), owner.id()));
        if (record.tenantId() != null && !record.tenantId().isBlank()
                && !record.tenantId().equals(actor.tenantId())) {
            throw new AccessDeniedException("Owner belongs to another tenant");
        }
        if (record.companyId() != null && !record.companyId().isBlank()
                && actor.companyId() != null && !actor.companyId().isBlank()
                && !record.companyId().equals(actor.companyId())) {
            throw new AccessDeniedException("Owner belongs to another company");
        }
    }

    private void assertCanWrite(DocumentActor actor, OwnerRef owner) {
        Boolean host = access.canWrite(actor, owner);
        if (Boolean.FALSE.equals(host)) {
            throw new AccessDeniedException("Not allowed to attach documents to " + owner.type());
        }
        if (Boolean.TRUE.equals(host) || DocumentAccessRules.canWrite(actor, owner)) {
            return;
        }
        throw new AccessDeniedException("Not allowed to attach documents to " + owner.type());
    }

    private void assertCanRead(DocumentActor actor, OwnerRef owner) {
        Boolean host = access.canRead(actor, owner);
        if (Boolean.FALSE.equals(host)) {
            throw new AccessDeniedException("Not allowed to read documents for " + owner.type());
        }
        if (Boolean.TRUE.equals(host) || DocumentAccessRules.canRead(actor, owner)) {
            return;
        }
        throw new AccessDeniedException("Not allowed to read documents for " + owner.type());
    }

    private void requireActor(DocumentActor actor) {
        if (actor == null) {
            throw new AccessDeniedException("tenant_id is required");
        }
        if (actor.tenantId() == null || actor.tenantId().isBlank()) {
            throw new AccessDeniedException("tenant_id is required");
        }
    }

    /** A company on both sides must match. A side without a company does not restrict. */
    private static boolean sameCompany(DocumentActor actor, String resourceCompanyId) {
        if (actor.companyId() == null || actor.companyId().isBlank()
                || resourceCompanyId == null || resourceCompanyId.isBlank()) {
            return true;
        }
        return actor.companyId().equals(resourceCompanyId);
    }

    private DocumentResponseDto toDtoWithUrls(Document document) {
        ObjectStoragePort.SignedUrl upload = document.pendingUpload() ? putUrl(document) : null;
        ObjectStoragePort.SignedUrl content = document.ready() ? getUrl(document) : null;
        return toDto(document, upload, content);
    }

    private ObjectStoragePort.SignedUrl putUrl(Document document) {
        return objectStorage.presignPut(document.storageKey(), document.contentType(), ttl());
    }

    private ObjectStoragePort.SignedUrl getUrl(Document document) {
        return objectStorage.presignGet(document.storageKey(), ttl());
    }

    private Duration ttl() {
        return properties.storage().signedUrlTtl();
    }

    private Pageable clamp(Pageable pageable) {
        int max = properties.http().maxPageSize();
        if (pageable == null) {
            return PageRequest.of(0, max);
        }
        int size = Math.min(pageable.getPageSize(), max);
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }

    private static DocumentResponseDto toDto(Document document,
                                             ObjectStoragePort.SignedUrl upload,
                                             ObjectStoragePort.SignedUrl content) {
        return new DocumentResponseDto(
                document.id(),
                document.product(),
                document.tenantId(),
                document.companyId(),
                document.owner().type(),
                document.owner().id(),
                document.purpose(),
                document.version(),
                document.supersedesDocumentId(),
                document.storageKey(),
                document.originalName(),
                document.contentType(),
                document.sizeBytes(),
                document.checksum(),
                document.status().name(),
                document.tags(),
                document.createdBy(),
                document.createdAt(),
                document.deletedAt(),
                upload == null ? null : upload.url(),
                upload == null ? null : upload.expiresAt(),
                content == null ? null : content.url(),
                content == null ? null : content.expiresAt());
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

}
