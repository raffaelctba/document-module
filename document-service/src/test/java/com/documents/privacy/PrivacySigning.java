package com.documents.privacy;

import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.security.InternalIdentitySigner;
import com.myproperty.platform.testing.IdentityRequests;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Signs a privacy subject request the way the host does, over whatever headers it carries at that point. */
final class PrivacySigning {

    static final IdentityProperties PROPERTIES = new IdentityProperties(
            IdentityRequests.SECRET, 300_000L, IdentityProperties.Headers.defaults());

    private PrivacySigning() {
    }

    static RequestPostProcessor signed() {
        return request -> {
            request.addHeader(PrivacySignature.HEADER, InternalIdentitySigner.sign(IdentityRequests.SECRET,
                    PrivacySignature.canonical(request, PROPERTIES)));
            return request;
        };
    }
}
