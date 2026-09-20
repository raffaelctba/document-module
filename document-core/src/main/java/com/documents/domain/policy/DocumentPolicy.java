package com.documents.domain.policy;

import java.util.Set;

/**
 * Country-neutral contract for document compliance capabilities.
 *
 * <p>Implementations must be immutable.  The returned sets must therefore
 * never be mutable views over implementation state.</p>
 */
public interface DocumentPolicy {
    String countryCode();

    SignatureLevel signatureLevel();

    Set<InspectionCapability> inspectionCapabilities();

    /**
     * Metadata that this jurisdiction can accept or process.  These flags do
     * not state that metadata is legally required.
     */
    EvidenceCapabilities evidenceCapabilities();

    /** @deprecated use {@link #evidenceCapabilities()} */
    @Deprecated
    default EvidenceCapabilities evidenceRequirements() {
        return evidenceCapabilities();
    }

    BilateralSignoff bilateralSignoff();

    Set<FiscalExportType> fiscalExportTypes();

    default boolean supportsInspection(InspectionCapability capability) {
        return inspectionCapabilities().contains(capability);
    }

    default boolean supportsFiscalExport(FiscalExportType exportType) {
        return fiscalExportTypes().contains(exportType);
    }
}