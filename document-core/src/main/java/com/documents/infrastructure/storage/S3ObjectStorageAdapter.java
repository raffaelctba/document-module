package com.documents.infrastructure.storage;

import com.documents.application.port.ObjectStoragePort;
import com.documents.config.DocumentsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "documents.storage", name = "provider", havingValue = "s3")
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final DocumentsProperties.Storage storage;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ObjectStorageAdapter(DocumentsProperties properties) {
        this.storage = properties.storage();
        this.client = buildClient(storage);
        this.presigner = buildPresigner(storage);
    }

    S3ObjectStorageAdapter(DocumentsProperties.Storage storage, S3Client client, S3Presigner presigner) {
        this.storage = storage;
        this.client = client;
        this.presigner = presigner;
    }

    @Override
    public SignedUrl presignPut(String storageKey, String contentType, Duration ttl) {
        PutObjectRequest.Builder put = PutObjectRequest.builder()
                .bucket(storage.bucket())
                .key(storageKey);
        if (contentType != null && !contentType.isBlank()) {
            put.contentType(contentType);
        }
        Instant expiresAt = Instant.now().plus(ttl);
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(put.build())
                        .build())
                .url()
                .toString();
        return new SignedUrl(url, expiresAt);
    }

    @Override
    public SignedUrl presignGet(String storageKey, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(request -> request.bucket(storage.bucket()).key(storageKey))
                        .build())
                .url()
                .toString();
        return new SignedUrl(url, expiresAt);
    }

    @Override
    public void put(String storageKey, byte[] content, String contentType) {
        PutObjectRequest.Builder put = PutObjectRequest.builder()
                .bucket(storage.bucket())
                .key(storageKey);
        if (contentType != null && !contentType.isBlank()) {
            put.contentType(contentType);
        }
        client.putObject(put.build(), RequestBody.fromBytes(content == null ? new byte[0] : content));
    }

    @Override
    public byte[] get(String storageKey) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(storage.bucket())
                            .key(storageKey)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new IllegalStateException("Object not found: " + storageKey, ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IllegalStateException("Object not found: " + storageKey, ex);
            }
            throw ex;
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(storage.bucket())
                    .key(storageKey)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private static S3Client buildClient(DocumentsProperties.Storage storage) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(Boolean.TRUE.equals(storage.pathStyle()))
                        .build());
        applyEndpointAndCredentials(builder, storage);
        return builder.build();
    }

    private static S3Presigner buildPresigner(DocumentsProperties.Storage storage) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(storage.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(Boolean.TRUE.equals(storage.pathStyle()))
                        .build());
        if (storage.endpoint() != null) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        if (storage.accessKey() != null && storage.secretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
        }
        return builder.build();
    }

    private static void applyEndpointAndCredentials(S3ClientBuilder builder, DocumentsProperties.Storage storage) {
        if (storage.endpoint() != null) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        if (storage.accessKey() != null && storage.secretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storage.bucket())
                    .key(storageKey)
                    .build());
        } catch (RuntimeException ignored) {
            // best-effort purge
        }
    } catch (RuntimeException ex) {
            // best-effort purge
        }
    }
}
