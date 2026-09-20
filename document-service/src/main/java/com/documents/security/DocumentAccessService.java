package com.documents.security;

import com.documents.api.dto.CreateDocumentRequestDto;
import com.myproperty.platform.security.IdentityContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Module-local authorization from gateway identity. Property-scoped checks still
 * live on the host for in-process composition — remaining extraction coupling.
 */
@Service("documentAccess")
public class DocumentAccessService {

    private static final String[] WRITE_ROLES = {
            "ADMIN", "PROPERTY_MANAGER", "PROPERTY_OWNER", "PROPERTY_DELEGATE"};
    private static final String[] READ_ROLES = {
            "ADMIN", "PROPERTY_MANAGER", "PROPERTY_OWNER", "PROPERTY_DELEGATE", "PROPERTY_TENANT"};

    public boolean canWrite(Authentication authentication, CreateDocumentRequestDto request) {
        IdentityContext identity = IdentityContext.require();
        if (request != null && isProfile(request.ownerType())) {
            return identity.userId().equals(request.ownerId()) || identity.hasAnyRole(WRITE_ROLES);
        }
        return identity.hasAnyRole(WRITE_ROLES);
    }

    public boolean canWriteExisting(Authentication authentication, String documentId) {
        IdentityContext.require();
        return true;
    }

    public boolean canRead(Authentication authentication, String documentId) {
        IdentityContext.require();
        return true;
    }

    public boolean canList(Authentication authentication, String ownerType, String ownerId) {
        IdentityContext identity = IdentityContext.require();
        if (isProfile(ownerType)) {
            return identity.userId().equals(ownerId) || identity.hasAnyRole(READ_ROLES);
        }
        return identity.hasAnyRole(READ_ROLES);
    }

    private static boolean isProfile(String ownerType) {
        return ownerType != null && "USER_PROFILE".equalsIgnoreCase(ownerType.trim());
    }
}
