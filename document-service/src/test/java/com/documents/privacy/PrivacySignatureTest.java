package com.documents.privacy;

import com.myproperty.platform.security.IdentityContext;
import com.myproperty.platform.security.InternalIdentitySigner;
import com.myproperty.platform.testing.IdentityRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Service-token calls skip the identity filter's skew check, so the privacy signature enforces it and the tenant. */
class PrivacySignatureTest {

    private static final long NOW = 1_800_000_000_000L;
    private final PrivacySignature signature = new PrivacySignature(PrivacySigning.PROPERTIES,
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    @AfterEach
    void clear() {
        IdentityContext.clear();
    }

    @Test
    void aFreshCallForTheCallersOwnTenantPasses() {
        IdentityContext.set(new IdentityContext("mybuilding-api", "", "ten-1", Set.of("HOST_PRIVACY")));
        assertThatCode(() -> signature.verify(signed("ten-1", NOW - 1_000))).doesNotThrowAnyException();
    }

    @Test
    void aCallOutsideTheSkewWindowIsRefusedEvenWithAValidSignature() {
        IdentityContext.set(new IdentityContext("mybuilding-api", "", "ten-1", Set.of("HOST_PRIVACY")));
        assertThatThrownBy(() -> signature.verify(signed("ten-1", NOW - 301_000)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PRIVACY_SIGNATURE_EXPIRED");
    }

    @Test
    void aSignedTenantThatIsNotTheCallersIdentityTenantIsRefused() {
        IdentityContext.set(new IdentityContext("mybuilding-api", "", "ten-2", Set.of("HOST_PRIVACY")));
        assertThatThrownBy(() -> signature.verify(signed("ten-1", NOW)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PRIVACY_TENANT_MISMATCH");
        IdentityContext.clear();
        assertThatThrownBy(() -> signature.verify(signed("ten-1", NOW)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PRIVACY_TENANT_MISMATCH");
    }

    private static MockHttpServletRequest signed(String tenant, long timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x/privacy/subjects/kc-9/erasure");
        request.addHeader(PrivacySigning.PROPERTIES.headers().tenantId(), tenant);
        request.addHeader(PrivacySigning.PROPERTIES.headers().timestamp(), Long.toString(timestamp));
        request.addHeader(PrivacySignature.HEADER, InternalIdentitySigner.sign(IdentityRequests.SECRET,
                PrivacySignature.canonical(request, PrivacySigning.PROPERTIES)));
        return request;
    }
}
