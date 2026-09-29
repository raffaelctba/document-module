package com.documents.infrastructure.storage;

import com.documents.application.port.ObjectStoragePort;
import com.documents.config.DocumentsProperties;

/**
 * Selects the object-storage process for the document module.
 * Supported processes: {@code memory}, {@code filesystem}, and {@code s3}.
 */
public final class ObjectStorageProcesses {

    public static final String MEMORY = "memory";
    public static final String FILESYSTEM = "filesystem";
    public static final String S3 = "s3";

    private ObjectStorageProcesses() {
    }

    public static ObjectStoragePort open(DocumentsProperties properties) {
        String provider = properties.storage().provider();
        return switch (provider) {
            case MEMORY -> new InMemoryObjectStorageAdapter();
            case FILESYSTEM -> new FilesystemObjectStorageAdapter(properties);
            case S3 -> new S3ObjectStorageAdapter(properties);
            default -> throw new IllegalStateException(
                    "Unsupported documents storage process '" + provider
                            + "'. Supported processes: memory, filesystem, s3");
        };
    }
}
