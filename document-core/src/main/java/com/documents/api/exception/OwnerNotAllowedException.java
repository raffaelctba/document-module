package com.documents.api.exception;

public class OwnerNotAllowedException extends RuntimeException {

    public OwnerNotAllowedException(String product, String ownerType) {
        super("Owner type " + ownerType + " is not allowed for product " + product);
    }

    public OwnerNotAllowedException(String message) {
        super(message);
    }
}
