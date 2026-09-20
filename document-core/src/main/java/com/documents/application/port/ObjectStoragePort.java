package com.documents.application.port;

import java.time.Duration;
import java.time.Instant;

public interface ObjectStoragePort {

    SignedUrl presignPut(String storageKey, String contentType, Duration ttl);

    SignedUrl presignGet(String storageKey, Duration ttl);

    void put(String storageKey, byte[] content, String contentType);

    byte[] get(String storageKey);

    boolean exists(String storageKey);

    record SignedUrl(String url, Instant expiresAt) {
    }
}
