package com.documents.domain.service;

import com.documents.config.DocumentsProperties;
import com.documents.domain.model.OwnerRef;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class DocumentDomainService {

    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[^A-Za-z0-9._-]+");

    private final DocumentsProperties properties;

    public DocumentDomainService(DocumentsProperties properties) {
        this.properties = properties;
    }

    public String resolveProduct(String requested) {
        if (requested == null || requested.isBlank()) {
            return properties.productDefault();
        }
        return requested.trim().toLowerCase(Locale.ROOT);
    }

    public void assertOwnerAllowed(String product, String ownerType) {
        List<String> allowed = properties.ownersFor(product);
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("Unknown product: " + product);
        }
        String type = ownerType == null ? "" : ownerType.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(type)) {
            throw new IllegalArgumentException("Owner type " + type + " is not allowed for product " + product);
        }
    }

    public void assertPurposeAllowed(String product, String purpose) {
        List<String> allowed = properties.purposesFor(product);
        if (allowed.isEmpty()) {
            return;
        }
        String value = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("Purpose " + value + " is not allowed for product " + product);
        }
    }

    public String storageKey(String product, String tenantId, OwnerRef owner, String documentId) {
        return properties.storage().keyPrefix()
                + "/" + sanitize(product)
                + "/" + sanitize(tenantId)
                + "/" + sanitize(owner.type())
                + "/" + sanitize(owner.id())
                + "/" + sanitize(documentId);
    }

    public void assertContentAllowed(String contentType, Long sizeBytes) {
        if (sizeBytes != null && properties.maxSizeBytes() != null
                && sizeBytes > properties.maxSizeBytes()) {
            throw new IllegalArgumentException(
                    "sizeBytes exceeds max of " + properties.maxSizeBytes());
        }
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        List<String> allowed = properties.allowedContentTypes();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        String base = type.contains(";") ? type.substring(0, type.indexOf(';')).trim() : type;
        boolean ok = allowed.stream().anyMatch(a -> a.equals(base) || a.equals(type)
                || (a.endsWith("/*") && base.startsWith(a.substring(0, a.length() - 1))));
        if (!ok) {
            throw new IllegalArgumentException("contentType not allowed: " + contentType);
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        String name = filename.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("filename is required");
        }
        return name;
    }

    static String sanitize(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("storage key segment is required");
        }
        String cleaned = UNSAFE_SEGMENT.matcher(segment.trim()).replaceAll("_");
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            throw new IllegalArgumentException("storage key segment is invalid");
        }
        return cleaned;
    }
}
