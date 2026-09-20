package com.documents.api.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Caller identity for in-process and HTTP use. Built from gateway headers in
 * standalone mode; hosts construct it from their own security context.
 *
 * <p>{@code tenantId} / {@code companyId} here are the source of truth. They
 * are never read from the document JSON body.
 */
public record DocumentActor(String userId, String companyId, String tenantId, Set<String> roles) {

    public DocumentActor {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        userId = userId.trim();
        companyId = blankToNull(companyId);
        tenantId = blankToNull(tenantId);
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String expected = normalizeRole(role);
        return roles.stream().map(DocumentActor::normalizeRole).anyMatch(expected::equals);
    }

    public boolean hasAnyRole(String... candidates) {
        if (candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (hasRole(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRole(String role) {
        String value = role.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring(5) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
