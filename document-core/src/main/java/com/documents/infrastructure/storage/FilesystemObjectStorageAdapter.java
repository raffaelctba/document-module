package com.documents.infrastructure.storage;

import com.documents.application.port.ObjectStoragePort;
import com.documents.config.DocumentsProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Local directory storage process. Selected when {@code documents.storage.provider=filesystem}.
 * Bytes live under {@code documents.storage.directory} (default {@code document-storage}).
 */
public class FilesystemObjectStorageAdapter implements ObjectStoragePort {

    private final Path root;

    public FilesystemObjectStorageAdapter(DocumentsProperties properties) {
        this(directory(properties));
    }

    FilesystemObjectStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public SignedUrl presignPut(String storageKey, String contentType, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        return new SignedUrl(url("put", storageKey, expiresAt), expiresAt);
    }

    @Override
    public SignedUrl presignGet(String storageKey, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        return new SignedUrl(url("get", storageKey, expiresAt), expiresAt);
    }

    @Override
    public void put(String storageKey, byte[] content, String contentType) {
        Path path = resolve(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content == null ? new byte[0] : content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store object: " + storageKey, ex);
        }
    }

    @Override
    public byte[] get(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Object not found: " + storageKey);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read object: " + storageKey, ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // best-effort purge
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static Path directory(DocumentsProperties properties) {
        String configured = properties.storage().directory();
        if (configured == null || configured.isBlank()) {
            return Path.of("document-storage");
        }
        return Path.of(configured);
    }

    private static String url(String operation, String storageKey, Instant expiresAt) {
        return "file://" + operation + "/" + storageKey + "?exp=" + expiresAt.toEpochMilli();
    }
}
