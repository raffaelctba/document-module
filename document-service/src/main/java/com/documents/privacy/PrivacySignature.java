package com.documents.privacy;

import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.security.InternalIdentitySigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks the host's privacy signature: an HMAC, under the gateway identity secret, over the method, the path (which
 * subject, export or erasure), the tenant, the subject's address headers and the identity timestamp. The identity
 * filter has already checked that timestamp's skew, so a captured call cannot be pointed at another subject, tenant
 * or address, and stops working after the skew window. A call without a valid signature is refused before any
 * lookup.
 */
@Component
public class PrivacySignature {

    public static final String HEADER = "X-Privacy-Signature";
    public static final String EMAIL = "X-Subject-Email";
    public static final String PHONE = "X-Subject-Phone";

    private final IdentityProperties identity;

    public PrivacySignature(IdentityProperties identity) {
        this.identity = identity;
    }

    public void verify(HttpServletRequest request) {
        String signature = request.getHeader(HEADER);
        String secret = identity.hmacSecret();
        if (signature == null || signature.isBlank() || secret == null || secret.isBlank()
                || !InternalIdentitySigner.matches(secret, canonical(request, identity), signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PRIVACY_SIGNATURE_INVALID");
        }
    }

    public static String canonical(HttpServletRequest request, IdentityProperties identity) {
        return "privacy-v1\n" + request.getMethod() + "\n" + request.getRequestURI() + "\n"
                + nz(request.getHeader(identity.headers().tenantId())) + "\n" + nz(request.getHeader(EMAIL)) + "\n"
                + nz(request.getHeader(PHONE)) + "\n" + nz(request.getHeader(identity.headers().timestamp()));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
