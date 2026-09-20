package com.documents.domain.policy;

/**
 * Fiscal outputs are capabilities of a policy, not globally available document
 * types.  A consumer must ask the policy before producing one.
 */
public enum FiscalExportType {
    DIMOB,
    CRA_T4A,
    IRS_1099,
    EU_VAT_LEDGER
}