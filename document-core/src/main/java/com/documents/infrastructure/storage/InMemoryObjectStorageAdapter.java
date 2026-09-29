package com.documents.infrastructure.storage;

import com.documents.application.port.ObjectStoragePort;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process storage process. Selected when {@code documents.storage.provider=memory}
 * (also the default when the provider is omitted).
 */
public class InMemoryObjectStorageAdapter implements ObjectStoragePort {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public SignedUrl presignPut(String storageKey, String contentType, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        objects.putIfAbsent(storageKey, new byte[0]);
        return new SignedUrl(url("put", storageKey, expiresAt), expiresAt);
    }

    @Override
    public SignedUrl presignGet(String storageKey, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        return new SignedUrl(url("get", storageKey, expiresAt), expiresAt);
    }

    @Override
    public void put(String storageKey, byte[] content, String contentType) {
        objects.put(storageKey, content == null ? new byte[0] : content);
    }

    @Override
    public byte[] get(String storageKey) {
        byte[] content = objects.get(storageKey);
        if (content == null) {
            throw new IllegalStateException("Object not found: " + storageKey);
        }
        return content;
    }

    @Override
    public boolean exists(String storageKey) {
        return objects.containsKey(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        objects.remove(storageKey);
    }

    public void remove(String storageKey) {
        delete(storageKey);
    }

    private static String url(String operation, String storageKey, Instant expiresAt) {
        return "memory://" + operation + "/" + storageKey + "?exp=" + expiresAt.toEpochMilli();
    }
}
