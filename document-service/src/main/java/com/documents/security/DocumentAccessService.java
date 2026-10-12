package com.documents.security;

import com.documents.api.DocumentAccessRules;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentActors;
import com.documents.domain.model.OwnerRef;
import com.myproperty.platform.security.IdentityContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Owner-level pre-checks for the HTTP routes, from the gateway identity. Same rules as
 * document-core ({@link DocumentAccessRules}): a trusted application capability, or the caller's
 * own profile. Record-level checks (get, content, change, delete) happen in document-core, which
 * loads the record and answers 404 for anything the caller may not see.
 */
@Service("documentAccess")
public class DocumentAccessService {

    public boolean canWrite(Authentication authentication, CreateDocumentRequestDto request) {
        DocumentActor actor = actor();
        OwnerRef owner = owner(request == null ? null : request.ownerType(), request == null ? null : request.ownerId());
        return owner == null ? DocumentAccessRules.trustedWriter(actor) : DocumentAccessRules.canWrite(actor, owner);
    }

    public boolean canList(Authentication authentication, String ownerType, String ownerId) {
        DocumentActor actor = actor();
        OwnerRef owner = owner(ownerType, ownerId);
        return owner == null ? DocumentAccessRules.trustedReader(actor) : DocumentAccessRules.canRead(actor, owner);
    }

    private static DocumentActor actor() {
        IdentityContext identity = IdentityContext.require();
        return DocumentActors.of(identity.userId(), identity.companyId(), identity.tenantId(),
                identity.roles() == null ? Set.of() : identity.roles());
    }

    private static OwnerRef owner(String type, String id) {
        try {
            return new OwnerRef(type, id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
