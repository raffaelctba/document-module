package com.documents.api.dto;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static com.myproperty.platform.common.text.Require.optionalLength;
import static com.myproperty.platform.common.text.Require.requireLength;

/**
 * Upload metadata. Tenant and company are taken from gateway identity, not
 * from this body. The client then PUTs bytes to the returned signed URL.
 * {@code supersedesDocumentId} is optional and creates the next version of
 * that document.
 */
public record CreateDocumentRequestDto(
        String ownerType,
        String ownerId,
        String purpose,
        String product,
        String filename,
        String contentType,
        Long sizeBytes,
        Set<String> tags,
        String supersedesDocumentId) {

    public CreateDocumentRequestDto(
            String ownerType,
            String ownerId,
            String purpose,
            String product,
            String filename,
            String contentType,
            Long sizeBytes,
            Set<String> tags) {
        this(ownerType, ownerId, purpose, product, filename, contentType, sizeBytes, tags, null);
    }

    public CreateDocumentRequestDto {
        ownerType = requireLength("ownerType", ownerType, 64).toUpperCase(Locale.ROOT);
        ownerId = requireLength("ownerId", ownerId, 64);
        purpose = requireLength("purpose", purpose, 64).toUpperCase(Locale.ROOT);
        product = optionalLength("product", product, 64);
        if (product != null) {
            product = product.toLowerCase(Locale.ROOT);
        }
        filename = requireLength("filename", filename, 255);
        contentType = optionalLength("contentType", contentType, 127);
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        if (tags == null) {
            tags = Set.of();
        } else {
            Set<String> normalized = new LinkedHashSet<>();
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String value = tag.trim();
                if (value.length() > 64) {
                    throw new IllegalArgumentException("tag must be at most 64 characters");
                }
                normalized.add(value);
            }
            tags = Set.copyOf(normalized);
        }
        supersedesDocumentId = optionalLength("supersedesDocumentId", supersedesDocumentId, 36);
    }
}
