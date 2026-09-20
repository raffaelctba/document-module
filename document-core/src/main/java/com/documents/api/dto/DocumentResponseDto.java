package com.documents.api.dto;

import java.time.Instant;
import java.util.Set;

public record DocumentResponseDto(
        String id,
        String product,
        String tenantId,
        String companyId,
        String ownerType,
        String ownerId,
        String purpose,
        int version,
        String supersedesDocumentId,
        String storageKey,
        String originalName,
        String contentType,
        Long sizeBytes,
        String checksum,
        String status,
        Set<String> tags,
        String createdBy,
        Instant createdAt,
        Instant deletedAt,
        String uploadUrl,
        Instant uploadUrlExpiresAt,
        String contentUrl,
        Instant contentUrlExpiresAt) {
}
