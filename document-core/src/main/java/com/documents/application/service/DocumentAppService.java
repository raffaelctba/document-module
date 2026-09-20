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
import com.documents.api.OwnerLookupPort;
import com.documents.config.DocumentsProperties;
import com.documents.domain.model.Document;
import com.documents.domain.model.OwnerRef;
import com.documents.domain.service.DocumentDomainService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@Transactional
public class DocumentAppService {

    private static final String[] WRITE_ROLES = {
            "ADMIN", "PROPERTY_MANAGER", "PROPERTY_OWNER", "PROPERTY_DELEGATE"};
    private static final String[] READ_ROLES = {
            "ADMIN", "PROPERTY_MANAGER", "PROPERTY_OWNER", "PROPERTY_DELEGATE", "PROPERTY_TENANT"};

    private final DocumentRepositoryPort documents;
    private final ObjectStoragePort objectStorage;
    private final OwnerLookupPort ownerLookup;
    private final ClockPort clock;
    private final DocumentDomainService domain;
    private final DocumentsProperties properties;

    public DocumentAppService(DocumentRepositoryPort documents,
                              ObjectStoragePort objectStorage,
                              OwnerLookupPort ownerLookup,
                              ClockPort clock,
                              DocumentDomainService domain,
                              DocumentsProperties properties) {
        this.documents = documents;
        this.objectStorage = objectStorage;
        this.ownerLookup = ownerLookup;
        this.clock = clock;
        this.domain = domain;
        this.properties = properties;
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
        } catch (IllegalArgumentException ex) {
            throw new OwnerNotAllowedException(ex.getMessage());
        }
        verifyOwner(product, owner, actor);

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
        Document saved = documents.save(document);
        return toDto(saved, putUrl(saved), null);
    }

    public DocumentResponseDto upload(CreateDocumentRequestDto request, byte[] content, DocumentActor actor) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content is required");
        }
        DocumentResponseDto created = create(request, actor);
        Document document = documents.findById(created.id())
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
        document.markReady(body.sizeBytes(), body.checksum(), body.contentType());
        return toDtoWithUrls(documents.save(document));
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

    private Document requireVisible(String documentId, DocumentActor actor) {
        requireActor(actor);
        Document document = documents.findById(documentId)
                .filter(candidate -> !candidate.deleted())
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        assertSameTenant(actor, document.tenantId());
        assertSameCompany(actor, document.companyId());
        assertCanRead(actor, document.owner());
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
        if (owner.profile()) {
            if (owner.id().equals(actor.userId()) || actor.hasAnyRole(WRITE_ROLES)) {
                return;
            }
            throw new AccessDeniedException("Not allowed to attach to this profile");
        }
        if (!actor.hasAnyRole(WRITE_ROLES)) {
            throw new AccessDeniedException("Not allowed to attach documents to " + owner.type());
        }
    }

    private void assertCanRead(DocumentActor actor, OwnerRef owner) {
        if (owner.profile()) {
            if (owner.id().equals(actor.userId()) || actor.hasAnyRole(READ_ROLES)) {
                return;
            }
            throw new AccessDeniedException("Not allowed to read this profile document");
        }
        if (!actor.hasAnyRole(READ_ROLES)) {
            throw new AccessDeniedException("Not allowed to read documents for " + owner.type());
        }
    }

    private void requireActor(DocumentActor actor) {
        if (actor == null) {
            throw new AccessDeniedException("tenant_id is required");
        }
        if (actor.tenantId() == null || actor.tenantId().isBlank()) {
            throw new AccessDeniedException("tenant_id is required");
        }
    }

    private static void assertSameTenant(DocumentActor actor, String resourceTenantId) {
        if (resourceTenantId == null || resourceTenantId.isBlank() || !actor.tenantId().equals(resourceTenantId)) {
            throw new AccessDeniedException("Tenant mismatch");
        }
    }

    private static void assertSameCompany(DocumentActor actor, String resourceCompanyId) {
        if (actor.companyId() == null || actor.companyId().isBlank()
                || resourceCompanyId == null || resourceCompanyId.isBlank()) {
            return;
        }
        if (!actor.companyId().equals(resourceCompanyId)) {
            throw new AccessDeniedException("Company mismatch");
        }
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
