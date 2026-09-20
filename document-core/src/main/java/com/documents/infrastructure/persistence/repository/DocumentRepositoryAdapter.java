package com.documents.infrastructure.persistence.repository;

import com.documents.application.port.DocumentRepositoryPort;
import com.documents.domain.model.Document;
import com.documents.domain.model.DocumentStatus;
import com.documents.domain.model.OwnerRef;
import com.documents.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final DocumentJpaRepository jpaRepository;

    public DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Document save(Document document) {
        DocumentEntity saved = jpaRepository.save(toEntity(document));
        return toDomain(saved);
    }

    @Override
    public Optional<Document> findById(String id) {
        return jpaRepository.findById(id).map(DocumentRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsActiveSuccessor(String documentId) {
        return jpaRepository.existsBySupersedesDocumentIdAndDeletedAtIsNull(documentId);
    }

    @Override
    public Page<Document> listActive(String product,
                                     String tenantId,
                                     String ownerType,
                                     String ownerId,
                                     String purpose,
                                     boolean includeVersions,
                                     Pageable pageable) {
        String normalizedPurpose = purpose == null || purpose.isBlank() ? null : purpose;
        return jpaRepository.findActive(
                        product, tenantId, ownerType, ownerId, normalizedPurpose, includeVersions, pageable)
                .map(DocumentRepositoryAdapter::toDomain);
    }

    private static DocumentEntity toEntity(Document document) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(document.id());
        entity.setProduct(document.product());
        entity.setTenantId(document.tenantId());
        entity.setCompanyId(document.companyId());
        entity.setOwnerType(document.owner().type());
        entity.setOwnerId(document.owner().id());
        entity.setPurpose(document.purpose());
        entity.setVersion(document.version());
        entity.setSupersedesDocumentId(document.supersedesDocumentId());
        entity.setStorageKey(document.storageKey());
        entity.setOriginalName(document.originalName());
        entity.setContentType(document.contentType());
        entity.setSizeBytes(document.sizeBytes());
        entity.setChecksum(document.checksum());
        entity.setStatus(document.status().name());
        entity.setCreatedBy(document.createdBy());
        entity.setCreatedAt(document.createdAt());
        entity.setDeletedAt(document.deletedAt());
        entity.setTags(document.tags());
        return entity;
    }

    private static Document toDomain(DocumentEntity entity) {
        return Document.builder()
                .withId(entity.getId())
                .withProduct(entity.getProduct())
                .withTenantId(entity.getTenantId())
                .withCompanyId(entity.getCompanyId())
                .withOwner(new OwnerRef(entity.getOwnerType(), entity.getOwnerId()))
                .withPurpose(entity.getPurpose())
                .withVersion(entity.getVersion())
                .withSupersedesDocumentId(entity.getSupersedesDocumentId())
                .withStorageKey(entity.getStorageKey())
                .withOriginalName(entity.getOriginalName())
                .withContentType(entity.getContentType())
                .withSizeBytes(entity.getSizeBytes())
                .withChecksum(entity.getChecksum())
                .withStatus(DocumentStatus.valueOf(entity.getStatus()))
                .withTags(entity.getTags() == null ? Set.of() : new LinkedHashSet<>(entity.getTags()))
                .withCreatedBy(entity.getCreatedBy())
                .withCreatedAt(entity.getCreatedAt())
                .withDeletedAt(entity.getDeletedAt())
                .build();
    }
}
