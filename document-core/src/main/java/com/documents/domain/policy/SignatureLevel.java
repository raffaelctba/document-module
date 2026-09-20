package com.documents.domain.policy;

/**
 * Signature assurance supported by a document policy.  The values are ordered
 * from least to most assuring.
 */
public enum SignatureLevel {
    NONE,
    SIMPLE,
    ADVANCED,
    QUALIFIED
}