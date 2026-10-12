package com.documents.security;

import com.documents.api.dto.CreateDocumentRequestDto;
import com.myproperty.platform.security.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Owner-level pre-checks on the HTTP routes (create, list). */
class DocumentAccessServiceTest {

    private final DocumentAccessService access = new DocumentAccessService();

    @AfterEach
    void clear() {
        IdentityContext.clear();
    }

    @Test
    void endUserListsAndCreatesOnlyOnOwnProfile() {
        as("user-a");
        assertThat(access.canList(null, "USER_PROFILE", "user-a")).isTrue();
        assertThat(access.canList(null, "user_profile", "user-b")).isFalse();
        assertThat(access.canList(null, "PROPERTY", "1")).isFalse();
        assertThat(access.canWrite(null, request("USER_PROFILE", "user-a"))).isTrue();
        assertThat(access.canWrite(null, request("USER_PROFILE", "user-b"))).isFalse();
        assertThat(access.canWrite(null, request("PROPERTY", "1"))).isFalse();
    }

    @Test
    void endUserApplicationRolesGrantNothing() {
        as("user-a", "ADMIN", "MANAGER", "OWNER", "DELEGATE", "MEMBER");
        assertThat(access.canList(null, "PROPERTY", "1")).isFalse();
        assertThat(access.canList(null, "USER_PROFILE", "user-b")).isFalse();
        assertThat(access.canWrite(null, request("PROPERTY", "1"))).isFalse();
    }

    @Test
    void hostCapabilitiesAllowAnyOwner() {
        as("user-a", "HOST_DOCUMENT_READ");
        assertThat(access.canList(null, "PROPERTY", "1")).isTrue();
        assertThat(access.canWrite(null, request("PROPERTY", "1"))).isFalse();

        as("user-a", "HOST_DOCUMENT_WRITE");
        assertThat(access.canList(null, "PROPERTY", "1")).isTrue();
        assertThat(access.canWrite(null, request("PROPERTY", "1"))).isTrue();
    }

    @Test
    void missingOwnerIsOnlyForTrustedCallers() {
        as("user-a");
        assertThat(access.canList(null, null, null)).isFalse();
        as("user-a", "HOST_DOCUMENT_READ");
        assertThat(access.canList(null, " ", null)).isTrue();
    }

    private static void as(String userId, String... roles) {
        IdentityContext.set(new IdentityContext(userId, "co-1", "myproperty", Set.of(roles)));
    }

    private static CreateDocumentRequestDto request(String ownerType, String ownerId) {
        return new CreateDocumentRequestDto(ownerType, ownerId, "AVATAR", "myproperty", "a.png", "image/png", 1L, Set.of());
    }
}
