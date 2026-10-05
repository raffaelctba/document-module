package com.documents.infrastructure.owner;

import com.documents.api.OwnerLookupPort;

import java.util.Optional;

/**
 * Default: no callback into property/cleaning. Hosts may replace this bean to
 * reject uploads whose owner is missing or belongs to another tenant.
 *
 * <p>Registered by {@link com.documents.config.DocumentModuleConfig} as a
 * {@code @ConditionalOnMissingBean} fallback. It used to be a component-scanned
 * {@code @Component @ConditionalOnMissingBean}, which matched itself on the second
 * condition pass and removed itself, so a standalone document-service (which has no host
 * adapter) failed to start with "required a bean of type OwnerLookupPort".
 */
public class NoOpOwnerLookup implements OwnerLookupPort {

    @Override
    public Optional<OwnerRecord> find(String product, String ownerType, String ownerId) {
        return Optional.empty();
    }
}
