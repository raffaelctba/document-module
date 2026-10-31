package com.documents.privacy;

import com.documents.application.port.DocumentWriteGuard;
import org.springframework.stereotype.Component;

/** Refuses a new profile document for a subject erased in that tenant (see {@link PrivacyErasureGuard}). */
@Component
public class ProfileDocumentGuard implements DocumentWriteGuard {

    private final PrivacyErasureGuard guard;

    public ProfileDocumentGuard(PrivacyErasureGuard guard) {
        this.guard = guard;
    }

    @Override
    public void beforeCreate(String tenantId, String ownerType, String ownerId) {
        if (PrivacySubjectStore.PROFILE.equalsIgnoreCase(ownerType)) {
            guard.assertWritable(tenantId, ownerId);
        }
    }
}
