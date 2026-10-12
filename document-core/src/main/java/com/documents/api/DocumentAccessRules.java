package com.documents.api;

import com.documents.api.dto.DocumentActor;
import com.documents.domain.model.OwnerRef;

/**
 * Who may read or write the documents of an owner. There are two kinds of caller:
 *
 * <ul>
 *   <li><b>Trusted application</b> — carries {@link #HOST_READ} and/or {@link #HOST_WRITE}. Through
 *       the gateway these only exist on a service call (an application such as pm-backend signing
 *       its own identity from the internal network); the gateway strips them from every end-user
 *       token. The application has already decided this user may see this record. The module still
 *       scopes every lookup by tenant and company.</li>
 *   <li><b>End user</b> — anything else. Only their own profile documents (owner type
 *       {@code USER_PROFILE}, owner id = their user id). Every other owner is denied. Roles from an
 *       end-user token (ADMIN, MANAGER, ...) grant nothing here: they belong to the application's
 *       vocabulary, not this module's.</li>
 * </ul>
 *
 * <p>In-process hosts can override per owner through {@link DocumentAccessPort}.
 */
public final class DocumentAccessRules {

    /** Read any document of the caller's tenant (and company, when both sides have one). */
    public static final String HOST_READ = "HOST_DOCUMENT_READ";

    /** Create, change and delete documents; implies {@link #HOST_READ}. */
    public static final String HOST_WRITE = "HOST_DOCUMENT_WRITE";

    private DocumentAccessRules() {
    }

    public static boolean canRead(DocumentActor actor, OwnerRef owner) {
        return trustedReader(actor) || ownsProfile(actor, owner);
    }

    public static boolean canWrite(DocumentActor actor, OwnerRef owner) {
        return trustedWriter(actor) || ownsProfile(actor, owner);
    }

    public static boolean trustedReader(DocumentActor actor) {
        return actor.hasAnyRole(HOST_READ, HOST_WRITE);
    }

    public static boolean trustedWriter(DocumentActor actor) {
        return actor.hasRole(HOST_WRITE);
    }

    /** The owner is the caller's own profile. */
    public static boolean ownsProfile(DocumentActor actor, OwnerRef owner) {
        return owner.profile() && owner.id().equals(actor.userId());
    }
}
