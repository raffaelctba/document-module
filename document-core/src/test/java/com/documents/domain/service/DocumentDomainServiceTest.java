package com.documents.domain.service;

import com.documents.config.DocumentsProperties;
import com.documents.domain.model.OwnerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentDomainServiceTest {

    private DocumentDomainService domain;

    @BeforeEach
    void setUp() {
        domain = new DocumentDomainService(properties());
    }

    @Test
    void storageKeyIsProductTenantOwnerDocument() {
        String key = domain.storageKey(
                "myproperty", "ten-1", new OwnerRef("PROPERTY", "prop-123"), "doc-1");
        assertEquals("docs/myproperty/ten-1/PROPERTY/prop-123/doc-1", key);
    }

    @Test
    void storageKeySanitizesPathTraversal() {
        String key = domain.storageKey(
                "myproperty", "../ten", new OwnerRef("PROPERTY", "prop/../../x"), "d1");
        assertEquals("docs/myproperty/.._ten/PROPERTY/prop_.._.._x/d1", key);
    }

    @Test
    void filenameStripsPath() {
        assertEquals("pool.jpg", domain.sanitizeFilename("C:\\\\uploads\\\\pool.jpg"));
        assertEquals("pool.jpg", domain.sanitizeFilename("../../pool.jpg"));
    }

    @Test
    void unknownProductIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> domain.assertOwnerAllowed("otherapp", "PROPERTY"));
    }

    @Test
    void cleaningCannotAttachToProperty() {
        assertThrows(IllegalArgumentException.class,
                () -> domain.assertOwnerAllowed("mycleaning", "PROPERTY"));
        domain.assertOwnerAllowed("mycleaning", "SITE");
    }

    @Test
    void mypropertyOwnersAreAllowed() {
        domain.assertOwnerAllowed("myproperty", "PROPERTY");
        domain.assertOwnerAllowed("myproperty", "AMENITY");
        domain.assertOwnerAllowed("myproperty", "USER_PROFILE");
        domain.assertOwnerAllowed("myproperty", "CONVERSATION");
        domain.assertPurposeAllowed("myproperty", "GALLERY");
        domain.assertPurposeAllowed("myproperty", "LEASE");
        domain.assertPurposeAllowed("myproperty", "INSPECTION");
        domain.assertPurposeAllowed("myproperty", "NOTICE");
        domain.assertPurposeAllowed("myproperty", "APPLICATION");
        domain.assertPurposeAllowed("myproperty", "INCOME");
        domain.assertPurposeAllowed("myproperty", "COI");
        domain.assertPurposeAllowed("myproperty", "CHAT_ATTACHMENT");
        domain.assertPurposeAllowed("myproperty", "SIGNATURE");
        domain.assertPurposeAllowed("myproperty", "MINUTES");
        domain.assertPurposeAllowed("myproperty", "EVICTION");
    }

    @Test
    void unknownPurposeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> domain.assertPurposeAllowed("myproperty", "UNKNOWN"));
    }

    @Test
    void defaultProductIsMyproperty() {
        assertEquals("myproperty", domain.resolveProduct(null));
        assertEquals("mycleaning", domain.resolveProduct("MyCleaning"));
    }

    public static DocumentsProperties properties() {
        return new DocumentsProperties(
                "myproperty",
                Map.of(
                        "myproperty", List.of("PROPERTY", "AMENITY", "USER_PROFILE", "CONVERSATION"),
                        "mycleaning", List.of("SITE", "JOB", "USER_PROFILE", "BEFORE_AFTER")),
                Map.of(
                        "myproperty", List.of(
                                "COVER", "GALLERY", "FLOOR_PLAN", "CONTRACT", "INVOICE", "RECEIPT",
                                "REPORT", "PHOTO", "OTHER", "AVATAR", "ID_DOCUMENT", "LEASE",
                                "INSPECTION", "NOTICE", "APPLICATION", "INCOME", "COI",
                                "CHAT_ATTACHMENT", "SIGNATURE", "MINUTES", "EVICTION"),
                        "mycleaning", List.of("BEFORE_PHOTO", "AFTER_PHOTO", "AVATAR")),
                new DocumentsProperties.Storage(
                        "memory", "property-docs", "us-east-1", null, null, null, true, "docs",
                        Duration.ofMinutes(15)),
                new DocumentsProperties.OwnerLookup(false),
                new DocumentsProperties.Http(100));
    }
}
