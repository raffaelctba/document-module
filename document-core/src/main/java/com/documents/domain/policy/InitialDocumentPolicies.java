package com.documents.domain.policy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Baseline policies shipped with the module.  They intentionally describe
 * capabilities only; jurisdiction-specific workflow remains in the host.
 */
public final class InitialDocumentPolicies {
    private InitialDocumentPolicies() {
    }

    public static DocumentPolicy brazil() {
        return policy("BR", SignatureLevel.SIMPLE,
                set(InspectionCapability.DOCUMENT_REVIEW, InspectionCapability.ON_SITE_INSPECTION),
                new EvidenceCapabilities(false, false), BilateralSignoff.OPTIONAL,
                set(FiscalExportType.DIMOB));
    }

    public static DocumentPolicy canada() {
        return policy("CA", SignatureLevel.SIMPLE,
                set(InspectionCapability.DOCUMENT_REVIEW, InspectionCapability.REMOTE_INSPECTION),
                new EvidenceCapabilities(false, false), BilateralSignoff.OPTIONAL,
                set(FiscalExportType.CRA_T4A));
    }

    public static DocumentPolicy unitedStates() {
        return policy("US", SignatureLevel.SIMPLE,
                set(InspectionCapability.DOCUMENT_REVIEW, InspectionCapability.REMOTE_INSPECTION),
                new EvidenceCapabilities(false, false), BilateralSignoff.OPTIONAL,
                set(FiscalExportType.IRS_1099));
    }

    public static DocumentPolicy europeanUnion() {
        return policy("EU", SignatureLevel.ADVANCED,
                set(InspectionCapability.DOCUMENT_REVIEW, InspectionCapability.REMOTE_INSPECTION,
                        InspectionCapability.ON_SITE_INSPECTION),
                new EvidenceCapabilities(false, false), BilateralSignoff.OPTIONAL,
                set(FiscalExportType.EU_VAT_LEDGER));
    }

    private static DocumentPolicy policy(String country, SignatureLevel signature,
                                         Set<InspectionCapability> inspections,
                                         EvidenceCapabilities evidence,
                                         BilateralSignoff signoff,
                                         Set<FiscalExportType> exports) {
        return new ImmutableDocumentPolicy(country, signature, inspections, evidence, signoff, exports);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <E extends Enum<E>> Set<E> set(E... values) {
        EnumSet<E> result = EnumSet.noneOf(values[0].getDeclaringClass());
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
    }

    private static final class ImmutableDocumentPolicy implements DocumentPolicy {
        private final String countryCode;
        private final SignatureLevel signatureLevel;
        private final Set<InspectionCapability> inspectionCapabilities;
        private final EvidenceCapabilities evidenceCapabilities;
        private final BilateralSignoff bilateralSignoff;
        private final Set<FiscalExportType> fiscalExportTypes;

        private ImmutableDocumentPolicy(String countryCode, SignatureLevel signatureLevel,
                                        Set<InspectionCapability> inspectionCapabilities,
                                        EvidenceCapabilities evidenceRequirements,
                                        BilateralSignoff bilateralSignoff,
                                        Set<FiscalExportType> fiscalExportTypes) {
            this.countryCode = countryCode;
            this.signatureLevel = signatureLevel;
            this.inspectionCapabilities = Set.copyOf(inspectionCapabilities);
            this.evidenceCapabilities = evidenceRequirements;
            this.bilateralSignoff = bilateralSignoff;
            this.fiscalExportTypes = Set.copyOf(fiscalExportTypes);
        }

        @Override public String countryCode() { return countryCode; }
        @Override public SignatureLevel signatureLevel() { return signatureLevel; }
        @Override public Set<InspectionCapability> inspectionCapabilities() { return inspectionCapabilities; }
        @Override public EvidenceCapabilities evidenceCapabilities() { return evidenceCapabilities; }
        @Override public BilateralSignoff bilateralSignoff() { return bilateralSignoff; }
        @Override public Set<FiscalExportType> fiscalExportTypes() { return fiscalExportTypes; }
    }
}