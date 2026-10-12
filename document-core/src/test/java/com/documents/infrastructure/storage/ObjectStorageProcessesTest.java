package com.documents.infrastructure.storage;

import com.documents.config.DocumentsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageProcessesTest {

    @TempDir
    Path root;

    @Test
    void memoryIsTheDefaultProcess() {
        assertInstanceOf(InMemoryObjectStorageAdapter.class, ObjectStorageProcesses.open(properties("memory", null)));
    }

    @Test
    void filesystemProcessStoresBytesUnderTheConfiguredDirectory() {
        var storage = (FilesystemObjectStorageAdapter) ObjectStorageProcesses.open(properties("filesystem", root.toString()));
        storage.put("docs/avatar", new byte[] {1, 2}, "image/png");
        assertTrue(storage.exists("docs/avatar"));
        assertEquals(2, storage.get("docs/avatar").length);
        storage.delete("docs/avatar");
        assertThrows(IllegalStateException.class, () -> storage.get("docs/avatar"));
    }

    @Test
    void filesystemRejectsPathEscape() {
        var storage = new FilesystemObjectStorageAdapter(root);
        assertThrows(IllegalArgumentException.class, () -> storage.put("../secret", new byte[] {1}, "text/plain"));
    }

    @Test
    void unknownProcessFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ObjectStorageProcesses.open(properties("azure", null)));
        assertTrue(error.getMessage().contains("filesystem"));
        assertTrue(error.getMessage().contains("s3"));
    }

    private static DocumentsProperties properties(String provider, String directory) {
        return new DocumentsProperties(
                null, null, null, null, null, null,
                new DocumentsProperties.Storage(provider, null, null, null, null, null, null, null, null, directory),
                null,
                null);
    }
}
