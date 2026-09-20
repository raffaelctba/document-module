package com.documents.domain.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentPolicyRegistryTest {
    @Test
    void shipsInitialPoliciesAndNormalizesLookup() {
        DocumentPolicyRegistry registry = DocumentPolicyRegistry.defaults();

        assertEquals(SignatureLevel.SIMPLE, registry.require("br").signatureLevel());
        assertTrue(registry.require("BR").supportsFiscalExport(FiscalExportType.DIMOB));
        assertFalse(registry.require("US").supportsFiscalExport(FiscalExportType.DIMOB));
        assertEquals(BilateralSignoff.OPTIONAL, registry.require("eu").bilateralSignoff());
    }

    @Test
    void policyCapabilitiesAreImmutable() {
        DocumentPolicy policy = registry().require("BR");

        assertThrows(UnsupportedOperationException.class,
                () -> policy.inspectionCapabilities().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.fiscalExportTypes().clear());
    }

    @Test
    void duplicateAndUnknownPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentPolicyRegistry(List.of(InitialDocumentPolicies.brazil(),
                        InitialDocumentPolicies.brazil())));
        assertThrows(IllegalArgumentException.class, () -> registry().require("ZZ"));
    }

    private static DocumentPolicyRegistry registry() {
        return DocumentPolicyRegistry.defaults();
    }
}