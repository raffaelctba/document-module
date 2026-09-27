package com.documents.domain.event;

import java.time.Instant;

public record DocumentReadyEvent(
        String documentId,
        String tenantId,
        String ownerType,
        String ownerId,
        String purpose,
        Instant occurredAt) {
}
