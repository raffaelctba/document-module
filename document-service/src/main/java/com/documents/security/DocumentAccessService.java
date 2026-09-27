package com.documents.security;

import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.config.DocumentsProperties;
import com.myproperty.platform.security.IdentityContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Module-local authorization from gateway identity.
 * Roles are product-agnostic and come from {@link DocumentsProperties}
 * (defaults: ADMIN/MANAGER/OWNER/DELEGATE[/MEMBER]).
 * Hosts map their own role names onto these at the gateway / token layer.
 */
@Service("documentAccess")
public class DocumentAccessService {

    private final DocumentsProperties properties;

    public DocumentAccessService(DocumentsProperties properties) {
        this.properties = properties;
    }

    public boolean canWrite(Authentication authentication, CreateDocumentRequestDto request) {
        IdentityContext identity = IdentityContext.require();
        if (request != null && isProfile(request.ownerType())) {
            return identity.userId().equals(request.ownerId()) || identity.hasAnyRole(writeRoles());
        }
        return identity.hasAnyRole(writeRoles());
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
            return identity.userId().equals(ownerId) || identity.hasAnyRole(readRoles());
        }
        return identity.hasAnyRole(readRoles());
    }

    private String[] writeRoles() {
        return properties.writeRoles().toArray(String[]::new);
    }

    private String[] readRoles() {
        return properties.readRoles().toArray(String[]::new);
    }

    private static boolean isProfile(String ownerType) {
        return ownerType != null && "USER_PROFILE".equalsIgnoreCase(ownerType.trim());
    }
}
