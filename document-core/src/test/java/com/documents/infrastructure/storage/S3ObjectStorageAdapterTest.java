package com.documents.infrastructure.storage;

import com.documents.config.DocumentsProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ObjectStorageAdapterTest {

    @Test
    void putUsesTheConfiguredBucketAndRegionSettings() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorageAdapter adapter = new S3ObjectStorageAdapter(storage(), client, mock(S3Presigner.class));

        adapter.put("docs/building/front.jpg", new byte[] {9}, "image/jpeg");

        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deleteIsBestEffortWhenTheBucketCallFails() {
        S3Client client = mock(S3Client.class);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("down"));
        S3ObjectStorageAdapter adapter = new S3ObjectStorageAdapter(storage(), client, mock(S3Presigner.class));

        adapter.delete("docs/building/front.jpg");

        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    private static DocumentsProperties.Storage storage() {
        return new DocumentsProperties.Storage(
                "s3",
                "mypropertyappbucket",
                "us-east-1",
                null,
                null,
                null,
                false,
                "docs",
                Duration.ofMinutes(15),
                null);
    }
}
