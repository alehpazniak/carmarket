package com.carmarket.importorder.validation;

import java.util.Optional;

/**
 * Accepted upload types, recognised by their magic bytes; the client's Content-Type is never trusted.
 */
public enum FileType {
    PDF("application/pdf", "pdf", false),
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true);

    private final String contentType;
    private final String extension;
    private final boolean image;

    FileType(String contentType, String extension, boolean image) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return image;
    }

    /** Bytes needed by {@link #detect}. */
    public static final int HEADER_LENGTH = 12;

    /** Type of a file from its first bytes, or empty if it isn't one of the accepted types. */
    public static Optional<FileType> detect(byte[] header) {
        if (startsWith(header, 0, 0x25, 0x50, 0x44, 0x46, 0x2D)) return Optional.of(PDF);          // %PDF-
        if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) return Optional.of(JPEG);
        if (startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(PNG);
        if (startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data == null || data.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
