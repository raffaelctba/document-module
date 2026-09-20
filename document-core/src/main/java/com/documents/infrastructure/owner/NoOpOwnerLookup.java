package com.documents.infrastructure.owner;

import com.documents.api.OwnerLookupPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default: no callback into property/cleaning. Hosts may replace this bean to
 * reject uploads whose owner is missing or belongs to another tenant.
 */
@Component
@ConditionalOnMissingBean(OwnerLookupPort.class)
public class NoOpOwnerLookup implements OwnerLookupPort {

    @Override
    public Optional<OwnerRecord> find(String product, String ownerType, String ownerId) {
        return Optional.empty();
    }
}
