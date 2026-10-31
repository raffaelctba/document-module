package com.documents.privacy;

import com.myproperty.platform.security.IdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * What the document module holds about one person, for the host's privacy fan-out (access/portability and erasure requests).
 * Service-only: {@code HOST_PRIVACY} is stripped from every end-user token by api-gateway, so only the host,
 * which has already authorized the request, can call it. Always scoped to the caller's tenant.
 */
@RestController
@RequestMapping("/documents/privacy/subjects/{subjectId}")
@PreAuthorize("hasRole('HOST_PRIVACY')")
public class PrivacySubjectController {

    private final PrivacySubjectStore store;
    private final PrivacySignature signature;

    public PrivacySubjectController(PrivacySubjectStore store, PrivacySignature signature) {
        this.store = store;
        this.signature = signature;
    }

    @GetMapping
    public Map<String, Object> export(@PathVariable String subjectId, jakarta.servlet.http.HttpServletRequest request) {
        signature.verify(request);
        return store.export(tenant(), subject(subjectId));
    }

    /** Idempotent: a repeat erases nothing more and answers the same shape. */
    @PostMapping("/erasure")
    public Map<String, Object> erase(@PathVariable String subjectId, jakarta.servlet.http.HttpServletRequest request) {
        signature.verify(request);
        return store.erase(tenant(), subject(subjectId));
    }

    static String tenant() {
        String tenant = IdentityContext.require().tenantId();
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED");
        }
        return tenant;
    }

    static String subject(String subjectId) {
        if (subjectId == null || !subjectId.matches("[A-Za-z0-9._@-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SUBJECT");
        }
        return subjectId;
    }
}
