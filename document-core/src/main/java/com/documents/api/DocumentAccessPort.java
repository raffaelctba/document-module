package com.documents.api;

import com.documents.api.dto.DocumentActor;
import com.documents.domain.model.OwnerRef;

/**
 * Host SPI for product-specific document authorization beyond role lists.
 * Default: rely on {@link com.documents.config.DocumentsProperties} roles and
 * USER_PROFILE ownership. Hosts implement membership (e.g. workspace members).
 */
public interface DocumentAccessPort {

    /**
     * @return {@code null} to fall through to module default role checks;
     *         {@code true}/{@code false} to allow/deny explicitly.
     */
    default Boolean canWrite(DocumentActor actor, OwnerRef owner) {
        return null;
    }

    default Boolean canRead(DocumentActor actor, OwnerRef owner) {
        return null;
    }
}
