package com.documents.domain.event;

import java.time.Instant;

public record DocumentDeletedEvent(
        String documentId,
        String tenantId,
        String ownerType,
        String ownerId,
        Instant occurredAt) {
}
