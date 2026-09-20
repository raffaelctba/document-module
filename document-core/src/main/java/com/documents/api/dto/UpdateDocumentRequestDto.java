package com.documents.api.dto;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static com.myproperty.platform.common.text.Require.optionalLength;

/**
 * Optional metadata change after create. {@code null} fields are left unchanged;
 * an empty {@code tags} set clears tags.
 */
public record UpdateDocumentRequestDto(String purpose, Set<String> tags) {

    public UpdateDocumentRequestDto {
        purpose = optionalLength("purpose", purpose, 64);
        if (purpose != null) {
            purpose = purpose.toUpperCase(Locale.ROOT);
        }
        if (tags != null) {
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
        if (purpose == null && tags == null) {
            throw new IllegalArgumentException("purpose or tags is required");
        }
    }
}
