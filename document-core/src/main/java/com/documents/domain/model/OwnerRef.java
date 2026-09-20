package com.documents.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Polymorphic owner. The document module never learns what a property (or
 * amenity, profile, cleaning site, …) is — only that some external resource
 * in a given product/tenant is allowed to own files.
 */
public record OwnerRef(String type, String id) {

    public static final String USER_PROFILE = "USER_PROFILE";

    public OwnerRef {
        type = requireNonBlank(type, "ownerType is required").toUpperCase(Locale.ROOT);
        id = requireNonBlank(id, "ownerId is required");
    }

    public boolean profile() {
        return USER_PROFILE.equals(type);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OwnerRef that)) {
            return false;
        }
        return type.equals(that.type) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
