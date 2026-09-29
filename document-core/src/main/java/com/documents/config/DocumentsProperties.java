package com.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Binds the {@code documents.*} configuration namespace (see {@code documents.yml}).
 */
@ConfigurationProperties(prefix = "documents")
public record DocumentsProperties(
        String productDefault,
        Map<String, List<String>> owners,
        Map<String, List<String>> purposes,
        List<String> writeRoles,
        List<String> readRoles,
        List<String> allowedContentTypes,
        Long maxSizeBytes,
        Duration softDeleteRetention,
        Storage storage,
        OwnerLookup ownerLookup,
        Http http) {

    public DocumentsProperties {
        if (productDefault == null || productDefault.isBlank()) {
            productDefault = "platform";
        } else {
            productDefault = productDefault.trim().toLowerCase(Locale.ROOT);
        }
        owners = normalizeAllowlist(owners);
        purposes = normalizeAllowlist(purposes);
        writeRoles = normalizeRoleList(writeRoles, List.of("ADMIN", "MANAGER", "OWNER", "DELEGATE"));
        readRoles = normalizeRoleList(readRoles, List.of("ADMIN", "MANAGER", "OWNER", "DELEGATE", "MEMBER"));
        if (allowedContentTypes == null) {
            allowedContentTypes = List.of();
        } else {
            allowedContentTypes = allowedContentTypes.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> v.trim().toLowerCase(Locale.ROOT))
                    .toList();
        }
        if (maxSizeBytes != null && maxSizeBytes < 1) {
            maxSizeBytes = null;
        }
        if (softDeleteRetention == null || softDeleteRetention.isNegative() || softDeleteRetention.isZero()) {
            softDeleteRetention = Duration.ofDays(30);
        }
        if (storage == null) {
            storage = Storage.defaults();
        }
        if (ownerLookup == null) {
            ownerLookup = new OwnerLookup(false);
        }
        if (http == null) {
            http = new Http(100);
        }
    }

    private static List<String> normalizeRoleList(List<String> source, List<String> defaults) {
        if (source == null || source.isEmpty()) {
            return List.copyOf(defaults);
        }
        return source.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    public List<String> ownersFor(String product) {
        if (product == null) {
            return List.of();
        }
        return owners.getOrDefault(product.trim().toLowerCase(Locale.ROOT), List.of());
    }

    public List<String> purposesFor(String product) {
        if (product == null) {
            return List.of();
        }
        return purposes.getOrDefault(product.trim().toLowerCase(Locale.ROOT), List.of());
    }

    private static Map<String, List<String>> normalizeAllowlist(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().trim().toLowerCase(Locale.ROOT),
                        entry -> entry.getValue() == null ? List.of() : entry.getValue().stream()
                                .filter(value -> value != null && !value.isBlank())
                                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                                .toList()));
    }

    public record Storage(
            String provider,
            String bucket,
            String region,
            String endpoint,
            String accessKey,
            String secretKey,
            Boolean pathStyle,
            String keyPrefix,
            Duration signedUrlTtl,
            String directory) {

        public Storage {
            if (provider == null || provider.isBlank()) {
                provider = "memory";
            } else {
                provider = provider.trim().toLowerCase(Locale.ROOT);
            }
            if (bucket == null || bucket.isBlank()) {
                bucket = "platform-docs";
            }
            if (region == null || region.isBlank()) {
                region = "us-east-1";
            }
            if (endpoint != null && endpoint.isBlank()) {
                endpoint = null;
            }
            if (accessKey != null && accessKey.isBlank()) {
                accessKey = null;
            }
            if (secretKey != null && secretKey.isBlank()) {
                secretKey = null;
            }
            pathStyle = pathStyle == null || pathStyle;
            if (keyPrefix == null || keyPrefix.isBlank()) {
                keyPrefix = "docs";
            } else {
                keyPrefix = trimSlashes(keyPrefix);
            }
            if (signedUrlTtl == null || signedUrlTtl.isNegative() || signedUrlTtl.isZero()) {
                signedUrlTtl = Duration.ofMinutes(15);
            }
            if (directory != null && directory.isBlank()) {
                directory = null;
            }
        }

        public static Storage defaults() {
            return new Storage("memory", "platform-docs", "us-east-1", null, null, null, true, "docs",
                    Duration.ofMinutes(15), null);
        }

        public boolean memory() {
            return "memory".equals(provider);
        }

        public boolean s3() {
            return "s3".equals(provider);
        }

        private static String trimSlashes(String value) {
            String trimmed = value.trim();
            while (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1);
            }
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed.isBlank() ? "docs" : trimmed;
        }
    }

    public record OwnerLookup(boolean enabled) {
    }

    public record Http(Integer maxPageSize) {
        public Http {
            if (maxPageSize == null || maxPageSize < 1) {
                maxPageSize = 100;
            }
        }
    }
}
