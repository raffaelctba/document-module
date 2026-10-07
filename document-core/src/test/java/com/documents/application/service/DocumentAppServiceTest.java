package com.documents.application.service;

import com.documents.api.dto.CompleteDocumentRequestDto;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.api.dto.UpdateDocumentRequestDto;
import com.documents.api.exception.DocumentNotFoundException;
import com.documents.api.exception.OwnerNotAllowedException;
import com.documents.api.exception.OwnerVerificationException;
import com.documents.application.port.DocumentRepositoryPort;
import com.documents.api.OwnerLookupPort;
import com.documents.config.DocumentsProperties;
import com.documents.domain.model.Document;
import com.documents.domain.service.DocumentDomainService;
import com.documents.domain.service.DocumentDomainServiceTest;
import com.documents.infrastructure.storage.InMemoryObjectStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.documents.api.exception.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryDocuments documents;
    private InMemoryObjectStorageAdapter storage;
    private RecordingOwnerLookup ownerLookup;
    private DocumentAppService service;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocuments();
        storage = new InMemoryObjectStorageAdapter();
        ownerLookup = new RecordingOwnerLookup();
        DocumentsProperties properties = DocumentDomainServiceTest.properties();
        service = new DocumentAppService(
                documents,
                storage,
                ownerLookup,
                () -> NOW,
                new DocumentDomainService(properties),
                properties);
    }

    @Test
    void createUsesIdentityTenantAndReturnsUploadUrl() {
        DocumentResponseDto response = service.create(galleryRequest("myproperty"), manager());

        assertEquals("myproperty", response.product());
        assertEquals("ten-1", response.tenantId());
        assertEquals("co-1", response.companyId());
        assertEquals("PROPERTY", response.ownerType());
        assertEquals("prop-123", response.ownerId());
        assertEquals("GALLERY", response.purpose());
        assertEquals(1, response.version());
        assertNull(response.supersedesDocumentId());
        assertEquals("PENDING_UPLOAD", response.status());
        assertEquals("user-1", response.createdBy());
        assertTrue(response.storageKey().startsWith("docs/myproperty/ten-1/PROPERTY/prop-123/"));
        assertNotNull(response.uploadUrl());
        assertNull(response.contentUrl());
    }

    @Test
    void createDoesNotReadTenantFromBody() {
        DocumentResponseDto response = service.create(galleryRequest("myproperty"), manager());
        assertEquals("ten-1", response.tenantId());
        assertEquals("co-1", response.companyId());
    }

    @Test
    void cleaningCannotAttachToProperty() {
        assertThrows(OwnerNotAllowedException.class,
                () -> service.create(galleryRequest("mycleaning"), manager()));
    }

    @Test
    void tenantIsRequired() {
        DocumentActor noTenant = new DocumentActor("user-1", "co-1", null, Set.of("ADMIN"));
        assertThrows(AccessDeniedException.class, () -> service.create(galleryRequest("myproperty"), noTenant));
    }

    @Test
    void tenantCannotWritePropertyGallery() {
        DocumentActor tenant = new DocumentActor("user-1", "co-1", "ten-1", Set.of("MEMBER"));
        assertThrows(AccessDeniedException.class, () -> service.create(galleryRequest("myproperty"), tenant));
    }

    @Test
    void userCanUploadOwnAvatar() {
        CreateDocumentRequestDto request = new CreateDocumentRequestDto(
                "USER_PROFILE", "user-77", "AVATAR", "myproperty", "me.png", "image/png", 10L, Set.of());
        DocumentActor self = new DocumentActor("user-77", "co-1", "ten-1", Set.of());
        DocumentResponseDto response = service.create(request, self);
        assertEquals("USER_PROFILE", response.ownerType());
        assertEquals("user-77", response.ownerId());
    }

    @Test
    void userCannotUploadSomeoneElsesAvatar() {
        CreateDocumentRequestDto request = new CreateDocumentRequestDto(
                "USER_PROFILE", "user-77", "AVATAR", "myproperty", "me.png", "image/png", 10L, Set.of());
        DocumentActor other = new DocumentActor("user-1", "co-1", "ten-1", Set.of());
        assertThrows(AccessDeniedException.class, () -> service.create(request, other));
    }

    @Test
    void conversationOwnerAndLeasePurposeAreAllowed() {
        CreateDocumentRequestDto request = new CreateDocumentRequestDto(
                "CONVERSATION", "conv-1", "CHAT_ATTACHMENT", "myproperty",
                "leak.jpg", "image/jpeg", 10L, Set.of());
        DocumentResponseDto response = service.create(request, manager());
        assertEquals("CONVERSATION", response.ownerType());
        assertEquals("CHAT_ATTACHMENT", response.purpose());

        DocumentResponseDto first = service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "LEASE", "myproperty",
                "lease.pdf", "application/pdf", 20L, Set.of()), manager());
        assertEquals("LEASE", first.purpose());
        assertEquals(1, first.version());
    }

    @Test
    void supersedingCreatesNextVersion() {
        DocumentResponseDto first = service.create(galleryRequest("myproperty"), manager());
        DocumentResponseDto second = service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool-v2.jpg", "image/jpeg", 12L,
                Set.of("pool"), first.id()), manager());
        assertEquals(2, second.version());
        assertEquals(first.id(), second.supersedesDocumentId());
    }

    @Test
    void listReturnsLatestVersionByDefault() {
        byte[] bytes = new byte[] {1};
        DocumentResponseDto first = service.upload(galleryRequest("myproperty"), bytes, manager());
        DocumentResponseDto second = service.upload(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool-v2.jpg", "image/jpeg", 12L,
                Set.of("pool"), first.id()), bytes, manager());
        service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "COVER", "myproperty", "cover.jpg", "image/jpeg", 8L, Set.of()), manager());

        Page<DocumentResponseDto> latest = service.list(
                "PROPERTY", "prop-123", null, "myproperty", PageRequest.of(0, 20), manager());
        assertEquals(2, latest.getTotalElements());
        assertTrue(latest.getContent().stream().anyMatch(doc -> doc.id().equals(second.id())));
        assertTrue(latest.getContent().stream().noneMatch(doc -> doc.id().equals(first.id())));

        Page<DocumentResponseDto> all = service.list(
                "PROPERTY", "prop-123", null, "myproperty", true, PageRequest.of(0, 20), manager());
        assertEquals(3, all.getTotalElements());
        assertTrue(all.getContent().stream().anyMatch(doc -> doc.id().equals(first.id())));
    }

    @Test
    void pendingSuccessorDoesNotHideReadyPrevious() {
        DocumentResponseDto first = service.upload(galleryRequest("myproperty"), new byte[] {1}, manager());
        DocumentResponseDto pending = service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool-v2.jpg", "image/jpeg", 12L,
                Set.of("pool"), first.id()), manager());

        Page<DocumentResponseDto> latest = service.list(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", PageRequest.of(0, 20), manager());
        assertEquals(2, latest.getTotalElements());
        assertTrue(latest.getContent().stream().anyMatch(doc -> doc.id().equals(first.id())));
        assertTrue(latest.getContent().stream().anyMatch(doc -> doc.id().equals(pending.id())));
    }

    @Test
    void supersedeRequiresSamePurpose() {
        DocumentResponseDto first = service.create(galleryRequest("myproperty"), manager());
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "COVER", "myproperty", "cover.jpg", "image/jpeg", 8L,
                Set.of(), first.id()), manager()));
    }

    @Test
    void cannotSupersedeTheSameDocumentTwice() {
        DocumentResponseDto first = service.create(galleryRequest("myproperty"), manager());
        service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool-v2.jpg", "image/jpeg", 12L,
                Set.of("pool"), first.id()), manager());
        assertThrows(IllegalStateException.class, () -> service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool-v3.jpg", "image/jpeg", 12L,
                Set.of("pool"), first.id()), manager()));
    }

    @Test
    void supersedingMissingDocumentFails() {
        assertThrows(DocumentNotFoundException.class, () -> service.create(
                new CreateDocumentRequestDto(
                        "PROPERTY", "prop-123", "GALLERY", "myproperty", "pool.jpg", "image/jpeg", 12L,
                        Set.of(), "missing-document-id-000000000001"), manager()));
    }

    @Test
    void listIsScopedToOwnerAndTenant() {
        service.create(galleryRequest("myproperty"), manager());
        service.create(new CreateDocumentRequestDto(
                "PROPERTY", "prop-other", "GALLERY", "myproperty", "x.jpg", "image/jpeg", 1L, Set.of()), manager());

        Page<DocumentResponseDto> page = service.list(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", PageRequest.of(0, 20), manager());
        assertEquals(1, page.getTotalElements());
        assertEquals("prop-123", page.getContent().getFirst().ownerId());
    }

    @Test
    void otherTenantCannotRead() {
        DocumentResponseDto created = service.create(galleryRequest("myproperty"), manager());
        DocumentActor otherTenant = new DocumentActor("user-9", "co-9", "ten-9", Set.of("ADMIN"));
        // Lookups are tenant-scoped (findByIdAndTenantId): another tenant's document is
        // indistinguishable from a missing one, so existence is not leaked.
        assertThrows(DocumentNotFoundException.class, () -> service.get(created.id(), otherTenant));
    }

    @Test
    void softDeleteHidesDocument() {
        DocumentResponseDto created = service.create(galleryRequest("myproperty"), manager());
        service.delete(created.id(), manager());
        assertThrows(DocumentNotFoundException.class, () -> service.get(created.id(), manager()));
        Page<DocumentResponseDto> page = service.list(
                "PROPERTY", "prop-123", "GALLERY", "myproperty", PageRequest.of(0, 20), manager());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void completeMarksReadyAndIssuesContentUrl() {
        DocumentResponseDto created = service.create(galleryRequest("myproperty"), manager());
        DocumentResponseDto ready = service.complete(
                created.id(), new CompleteDocumentRequestDto("image/jpeg", 99L, "abc"), manager());
        assertEquals("READY", ready.status());
        assertEquals(99L, ready.sizeBytes());
        assertEquals("abc", ready.checksum());
        assertNotNull(ready.contentUrl());
        assertNull(ready.uploadUrl());
        assertTrue(service.contentUrl(created.id(), manager()).startsWith("memory://get/"));
    }

    @Test
    void uploadStoresBytesAndMarksReady() {
        byte[] bytes = new byte[] {1, 2, 3, 4};
        DocumentResponseDto ready = service.upload(galleryRequest("myproperty"), bytes, manager());
        assertEquals("READY", ready.status());
        assertEquals(4L, ready.sizeBytes());
        assertNotNull(ready.checksum());
        assertNotNull(ready.contentUrl());
        assertNull(ready.uploadUrl());
        assertEquals(bytes.length, service.contentBytes(ready.id(), manager()).length);
    }

    @Test
    void updateChangesPurposeAndTags() {
        DocumentResponseDto created = service.upload(galleryRequest("myproperty"), new byte[] {9}, manager());
        DocumentResponseDto updated = service.update(
                created.id(), new UpdateDocumentRequestDto("COVER", Set.of("front")), manager());
        assertEquals("COVER", updated.purpose());
        assertEquals(Set.of("front"), updated.tags());
        assertEquals("READY", updated.status());
    }

    @Test
    void ownerLookupRejectsMissingOwnerWhenEnabled() {
        DocumentsProperties base = DocumentDomainServiceTest.properties();
        DocumentsProperties properties = new DocumentsProperties(
                base.productDefault(),
                base.owners(),
                base.purposes(),
                base.writeRoles(),
                base.readRoles(),
                base.allowedContentTypes(),
                base.maxSizeBytes(),
                base.softDeleteRetention(),
                base.storage(),
                new DocumentsProperties.OwnerLookup(true),
                base.http());
        service = new DocumentAppService(
                documents, storage, ownerLookup, () -> NOW, new DocumentDomainService(properties), properties);

        assertThrows(OwnerVerificationException.class,
                () -> service.create(galleryRequest("myproperty"), manager()));

        ownerLookup.records.put("PROPERTY/prop-123", new OwnerLookupPort.OwnerRecord("ten-1", "co-1"));
        DocumentResponseDto created = service.create(galleryRequest("myproperty"), manager());
        assertEquals("prop-123", created.ownerId());
    }

    @Test
    void ownerLookupRejectsWrongTenant() {
        DocumentsProperties base = DocumentDomainServiceTest.properties();
        DocumentsProperties properties = new DocumentsProperties(
                base.productDefault(),
                base.owners(),
                base.purposes(),
                base.writeRoles(),
                base.readRoles(),
                base.allowedContentTypes(),
                base.maxSizeBytes(),
                base.softDeleteRetention(),
                base.storage(),
                new DocumentsProperties.OwnerLookup(true),
                base.http());
        service = new DocumentAppService(
                documents, storage, ownerLookup, () -> NOW, new DocumentDomainService(properties), properties);
        ownerLookup.records.put("PROPERTY/prop-123", new OwnerLookupPort.OwnerRecord("other-tenant", "co-1"));
        assertThrows(AccessDeniedException.class, () -> service.create(galleryRequest("myproperty"), manager()));
    }

    private static CreateDocumentRequestDto galleryRequest(String product) {
        return new CreateDocumentRequestDto(
                "PROPERTY", "prop-123", "GALLERY", product, "pool.jpg", "image/jpeg", 12L, Set.of("pool"));
    }

    private static DocumentActor manager() {
        return new DocumentActor("user-1", "co-1", "ten-1", Set.of("MANAGER"));
    }

    private static final class RecordingOwnerLookup implements OwnerLookupPort {
        private final Map<String, OwnerRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<OwnerRecord> find(String product, String ownerType, String ownerId) {
            return Optional.ofNullable(records.get(ownerType + "/" + ownerId));
        }
    }


    private static final class InMemoryDocuments implements DocumentRepositoryPort {
        private final Map<String, Document> byId = new ConcurrentHashMap<>();

        @Override
        public Document save(Document document) {
            byId.put(document.id(), document);
            return document;
        }

        @Override
        public Optional<Document> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Document> findByIdAndTenantId(String id, String tenantId) {
            return findById(id).filter(d -> tenantId.equals(d.tenantId()));
        }

        @Override
        public Optional<Document> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey) {
            return byId.values().stream()
                    .filter(d -> tenantId.equals(d.tenantId())
                            && idempotencyKey != null
                            && idempotencyKey.equals(d.idempotencyKey()))
                    .findFirst();
        }

        @Override
        public boolean existsActiveSuccessor(String documentId) {
            return byId.values().stream()
                    .anyMatch(document -> !document.deleted() && documentId.equals(document.supersedesDocumentId()));
        }

        @Override
        public Page<Document> listActive(String product,
                                         String tenantId,
                                         String ownerType,
                                         String ownerId,
                                         String purpose,
                                         boolean includeVersions,
                                         Pageable pageable) {
            List<Document> matches = byId.values().stream()
                    .filter(document -> !document.deleted())
                    .filter(document -> document.product().equals(product))
                    .filter(document -> document.tenantId().equals(tenantId))
                    .filter(document -> document.owner().type().equals(ownerType))
                    .filter(document -> document.owner().id().equals(ownerId))
                    .filter(document -> purpose == null || purpose.isBlank() || document.purpose().equals(purpose))
                    .filter(document -> includeVersions || byId.values().stream().noneMatch(candidate ->
                            candidate.ready()
                                    && document.id().equals(candidate.supersedesDocumentId())
                                    && candidate.product().equals(document.product())
                                    && candidate.owner().equals(document.owner())
                                    && candidate.purpose().equals(document.purpose())))
                    .sorted(Comparator.comparing(Document::createdAt).thenComparing(Document::id))
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), matches.size());
            List<Document> slice = start >= matches.size() ? List.of() : matches.subList(start, end);
            return new PageImpl<>(new ArrayList<>(slice), pageable, matches.size());
        }

        @Override
        public List<Document> findByIdsAndTenantId(Collection<String> ids, String tenantId) {
            return ids.stream()
                    .map(byId::get)
                    .filter(d -> d != null && tenantId.equals(d.tenantId()) && !d.deleted())
                    .toList();
        }

        @Override
        public List<Document> findDeletedBefore(String tenantId, Instant deletedBefore, int limit) {
            return byId.values().stream()
                    .filter(d -> tenantId.equals(d.tenantId()))
                    .filter(d -> d.deletedAt() != null && d.deletedAt().isBefore(deletedBefore))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void hardDelete(String id) {
            byId.remove(id);
        }
    }
}
