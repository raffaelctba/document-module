package com.documents.api.dto;

import static com.myproperty.platform.common.text.Require.optionalLength;

/**
 * Optional confirmation after the client has PUT the object to the signed URL.
 */
public record CompleteDocumentRequestDto(
        String contentType,
        Long sizeBytes,
        String checksum) {

    public CompleteDocumentRequestDto {
        contentType = optionalLength("contentType", contentType, 127);
        checksum = optionalLength("checksum", checksum, 128);
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }
}
