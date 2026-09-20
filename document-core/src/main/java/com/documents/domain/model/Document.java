package com.documents.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Document {

    private final String id;
    private final String product;
    private final String tenantId;
    private final String companyId;
    private final OwnerRef owner;
    private String purpose;
    private final int version;
    private final String supersedesDocumentId;
    private final String storageKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String checksum;
    private DocumentStatus status;
    private Set<String> tags;
    private final String createdBy;
    private final Instant createdAt;
    private Instant deletedAt;

    private Document(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.product = requireNonBlank(builder.product, "product is required").toLowerCase(Locale.ROOT);
        this.tenantId = requireNonBlank(builder.tenantId, "tenantId is required");
        this.companyId = blankToNull(builder.companyId);
        this.owner = Objects.requireNonNull(builder.owner, "owner is required");
        this.purpose = requireNonBlank(builder.purpose, "purpose is required").toUpperCase(Locale.ROOT);
        this.version = builder.version < 1 ? 1 : builder.version;
        this.supersedesDocumentId = blankToNull(builder.supersedesDocumentId);
        this.storageKey = requireNonBlank(builder.storageKey, "storageKey is required");
        this.originalName = blankToNull(builder.originalName);
        this.contentType = blankToNull(builder.contentType);
        this.sizeBytes = builder.sizeBytes;
        if (this.sizeBytes != null && this.sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        this.checksum = blankToNull(builder.checksum);
        this.status = builder.status != null ? builder.status : DocumentStatus.PENDING_UPLOAD;
        this.tags = builder.tags == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
        this.createdBy = requireNonBlank(builder.createdBy, "createdBy is required");
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.deletedAt = builder.deletedAt;
    }

    public static Document create(String product,
                                  String tenantId,
                                  String companyId,
                                  OwnerRef owner,
                                  String purpose,
                                  String storageKey,
                                  String originalName,
                                  String contentType,
                                  Long sizeBytes,
                                  Set<String> tags,
                                  String createdBy,
                                  Instant createdAt) {
        return create(product, tenantId, companyId, owner, purpose, storageKey, originalName,
                contentType, sizeBytes, tags, createdBy, createdAt, 1, null);
    }

    public static Document create(String product,
                                  String tenantId,
                                  String companyId,
                                  OwnerRef owner,
                                  String purpose,
                                  String storageKey,
                                  String originalName,
                                  String contentType,
                                  Long sizeBytes,
                                  Set<String> tags,
                                  String createdBy,
                                  Instant createdAt,
                                  int version,
                                  String supersedesDocumentId) {
        return builder()
                .withProduct(product)
                .withTenantId(tenantId)
                .withCompanyId(companyId)
                .withOwner(owner)
                .withPurpose(purpose)
                .withVersion(version)
                .withSupersedesDocumentId(supersedesDocumentId)
                .withStorageKey(storageKey)
                .withOriginalName(originalName)
                .withContentType(contentType)
                .withSizeBytes(sizeBytes)
                .withTags(tags)
                .withCreatedBy(createdBy)
                .withCreatedAt(createdAt)
                .withStatus(DocumentStatus.PENDING_UPLOAD)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markReady(Long sizeBytes, String checksum, String contentType) {
        assertActive();
        if (sizeBytes != null) {
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
            this.sizeBytes = sizeBytes;
        }
        if (checksum != null && !checksum.isBlank()) {
            this.checksum = checksum.trim();
        }
        if (contentType != null && !contentType.isBlank()) {
            this.contentType = contentType.trim();
        }
        this.status = DocumentStatus.READY;
    }

    public void reclassify(String purpose, Set<String> tags) {
        assertActive();
        if (purpose != null && !purpose.isBlank()) {
            this.purpose = purpose.trim().toUpperCase(Locale.ROOT);
        }
        if (tags != null) {
            this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        }
    }

    public void softDelete(Instant deletedAt) {
        assertActive();
        this.deletedAt = deletedAt != null ? deletedAt : Instant.now();
    }

    public boolean deleted() {
        return deletedAt != null;
    }

    public boolean pendingUpload() {
        return status == DocumentStatus.PENDING_UPLOAD && !deleted();
    }

    public boolean ready() {
        return status == DocumentStatus.READY && !deleted();
    }

    public String id() {
        return id;
    }

    public String product() {
        return product;
    }

    public String tenantId() {
        return tenantId;
    }

    public String companyId() {
        return companyId;
    }

    public OwnerRef owner() {
        return owner;
    }

    public String purpose() {
        return purpose;
    }

    public int version() {
        return version;
    }

    public String supersedesDocumentId() {
        return supersedesDocumentId;
    }

    public String storageKey() {
        return storageKey;
    }

    public String originalName() {
        return originalName;
    }

    public String contentType() {
        return contentType;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }

    public String checksum() {
        return checksum;
    }

    public DocumentStatus status() {
        return status;
    }

    public Set<String> tags() {
        return tags;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    private void assertActive() {
        if (deleted()) {
            throw new IllegalStateException("Document is deleted: " + id);
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Document that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static final class Builder {
        private String id;
        private String product;
        private String tenantId;
        private String companyId;
        private OwnerRef owner;
        private String purpose;
        private int version = 1;
        private String supersedesDocumentId;
        private String storageKey;
        private String originalName;
        private String contentType;
        private Long sizeBytes;
        private String checksum;
        private DocumentStatus status;
        private Set<String> tags = Set.of();
        private String createdBy;
        private Instant createdAt;
        private Instant deletedAt;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withProduct(String product) {
            this.product = product;
            return this;
        }

        public Builder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder withCompanyId(String companyId) {
            this.companyId = companyId;
            return this;
        }

        public Builder withOwner(OwnerRef owner) {
            this.owner = owner;
            return this;
        }

        public Builder withPurpose(String purpose) {
            this.purpose = purpose;
            return this;
        }

        public Builder withVersion(int version) {
            this.version = version;
            return this;
        }

        public Builder withSupersedesDocumentId(String supersedesDocumentId) {
            this.supersedesDocumentId = supersedesDocumentId;
            return this;
        }

        public Builder withStorageKey(String storageKey) {
            this.storageKey = storageKey;
            return this;
        }

        public Builder withOriginalName(String originalName) {
            this.originalName = originalName;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withSizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder withChecksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder withStatus(DocumentStatus status) {
            this.status = status;
            return this;
        }

        public Builder withTags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder withCreatedBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withDeletedAt(Instant deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public Document build() {
            return new Document(this);
        }
    }
}
