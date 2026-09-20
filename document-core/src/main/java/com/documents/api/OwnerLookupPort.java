package com.documents.api;

import java.util.Optional;

/**
 * Optional integrity check against the owning bounded context. The document
 * module does not import property (or cleaning) types; a host may implement
 * this port to reject uploads whose owner is missing or in another tenant.
 */
public interface OwnerLookupPort {

    Optional<OwnerRecord> find(String product, String ownerType, String ownerId);

    record OwnerRecord(String tenantId, String companyId) {
    }
}
