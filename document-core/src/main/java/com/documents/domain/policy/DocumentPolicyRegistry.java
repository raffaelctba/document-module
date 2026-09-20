package com.documents.domain.policy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable lookup registry.  New jurisdictions can be added without
 * changing the policy interface or existing consumers.
 */
public final class DocumentPolicyRegistry {
    private final Map<String, DocumentPolicy> policies;

    public DocumentPolicyRegistry(Collection<? extends DocumentPolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        Map<String, DocumentPolicy> indexed = new LinkedHashMap<>();
        for (DocumentPolicy policy : policies) {
            Objects.requireNonNull(policy, "policy");
            String code = normalize(policy.countryCode());
            if (indexed.put(code, policy) != null) {
                throw new IllegalArgumentException("Duplicate document policy: " + code);
            }
        }
        this.policies = Map.copyOf(indexed);
    }

    public static DocumentPolicyRegistry defaults() {
        return new DocumentPolicyRegistry(java.util.List.of(
                InitialDocumentPolicies.brazil(),
                InitialDocumentPolicies.canada(),
                InitialDocumentPolicies.unitedStates(),
                InitialDocumentPolicies.europeanUnion()));
    }

    public Optional<DocumentPolicy> find(String countryCode) {
        return Optional.ofNullable(policies.get(normalize(countryCode)));
    }

    public DocumentPolicy require(String countryCode) {
        return find(countryCode).orElseThrow(() ->
                new IllegalArgumentException("No document policy registered for country: " + countryCode));
    }

    public Map<String, DocumentPolicy> policies() {
        return policies;
    }

    private static String normalize(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("countryCode must not be blank");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}