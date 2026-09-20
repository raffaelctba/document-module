package com.documents.api.exception;

public class OwnerVerificationException extends RuntimeException {

    public OwnerVerificationException(String ownerType, String ownerId) {
        super("Owner not found: " + ownerType + "/" + ownerId);
    }
}
