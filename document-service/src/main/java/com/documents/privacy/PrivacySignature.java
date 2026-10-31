package com.documents.privacy;

import com.myproperty.platform.security.IdentityContext;
import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.security.InternalIdentitySigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

/**
 * Checks the host's privacy signature: an HMAC, under the gateway identity secret, over the method, the path (which
 * subject, export or erasure), the tenant, the subject's address headers and the identity timestamp. The identity
 * timestamp's skew is checked here too (a service-token call skips the identity filter's check), and the signed
 * tenant must be the caller's identity tenant, so a captured call cannot be pointed at another subject, tenant or
 * address, and stops working after the skew window. A call without a valid signature is refused before any lookup.
 */
@Component
public class PrivacySignature {

    public static final String HEADER = "X-Privacy-Signature";
    public static final String EMAIL = "X-Subject-Email";
    public static final String PHONE = "X-Subject-Phone";

    private final IdentityProperties identity;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PrivacySignature(IdentityProperties identity) {
        this(identity, Clock.systemUTC());
    }

    PrivacySignature(IdentityProperties identity, Clock clock) {
        this.identity = identity;
        this.clock = clock;
    }

    public void verify(HttpServletRequest request) {
        String signature = request.getHeader(HEADER);
        String secret = identity.hmacSecret();
        if (signature == null || signature.isBlank() || secret == null || secret.isBlank()
                || !InternalIdentitySigner.matches(secret, canonical(request, identity), signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PRIVACY_SIGNATURE_INVALID");
        }
        if (!fresh(request.getHeader(identity.headers().timestamp()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PRIVACY_SIGNATURE_EXPIRED");
        }
        IdentityContext caller = IdentityContext.current();
        if (caller == null || !nz(caller.tenantId()).equals(nz(request.getHeader(identity.headers().tenantId())))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PRIVACY_TENANT_MISMATCH");
        }
    }

    private boolean fresh(String timestamp) {
        try {
            return Math.abs(clock.millis() - Long.parseLong(nz(timestamp).trim())) <= identity.maxSkewMs();
        } catch (NumberFormatException e) {
            return false;
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
