package com.documents.api.dto;

import com.documents.api.DocumentAccessRules;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Host helpers for building {@link DocumentActor} from gateway / security identity. */
public final class DocumentActors {

    private DocumentActors() {
    }

    public static DocumentActor of(String userId, String companyId, String tenantId, Collection<String> roles) {
        return new DocumentActor(userId, companyId, tenantId, normalizeRoles(roles));
    }

    public static DocumentActor require(String userId, String companyId, String tenantId, Collection<String> roles) {
        return of(userId, companyId, tenantId, roles);
    }

    /** The host process itself (scheduled jobs such as purge), with full document capabilities. */
    public static DocumentActor systemForTenant(String tenantId) {
        return of("system", null, tenantId, Set.of(DocumentAccessRules.HOST_WRITE));
    }

    static Set<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String r = role.trim().toUpperCase(Locale.ROOT);
            if (r.startsWith("ROLE_")) {
                r = r.substring(5);
            }
            if (!r.isBlank()) {
                normalized.add(r);
            }
        }
        return Set.copyOf(normalized);
    }
}
