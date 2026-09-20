package com.documents.domain.policy;

/**
 * Evidence formats the document module can retain or process. These are
 * capabilities/preferences, not legal or workflow requirements.
 */
public final class EvidenceCapabilities {
    private final boolean exifSupported;
    private final boolean gpsSupported;

    public EvidenceCapabilities(boolean exifSupported, boolean gpsSupported) {
        this.exifSupported = exifSupported;
        this.gpsSupported = gpsSupported;
    }

    public boolean exifSupported() {
        return exifSupported;
    }

    public boolean gpsSupported() {
        return gpsSupported;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EvidenceCapabilities that)) return false;
        return exifSupported == that.exifSupported && gpsSupported == that.gpsSupported;
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(exifSupported) + Boolean.hashCode(gpsSupported);
    }
}