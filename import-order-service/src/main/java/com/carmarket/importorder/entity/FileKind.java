package com.carmarket.importorder.entity;

/** DOCUMENT files use a {@link DocumentCategory}, PHOTO files a {@link PhotoStage} as their category. */
public enum FileKind {
    DOCUMENT,
    PHOTO;

    /** Folder under import-orders/{orderId}/ in the bucket. */
    public String folder() {
        return this == DOCUMENT ? "documents" : "photos";
    }

    public boolean isValidCategory(String category) {
        if (category == null) return false;
        try {
            if (this == DOCUMENT) DocumentCategory.valueOf(category);
            else PhotoStage.valueOf(category);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
