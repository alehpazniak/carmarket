package com.carmarket.importorder.service;

import java.util.List;
import java.util.Map;

/**
 * Reads photo URLs out of a stored auction snapshot: Apibara's media.items / media.thumbs, or a
 * synced lot's imageUrls. Same rules as the frontend's auctionSnapshotPhotos.
 */
final class AuctionSnapshots {

    private AuctionSnapshots() {
    }

    /** The first photo (its thumbnail when there is one), or null. Only http(s) URLs. */
    static String firstPhotoUrl(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        if (snapshot.get("media") instanceof Map<?, ?> media) {
            if (media.get("items") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Object type = m.get("type");
                    if (type != null && !"image".equals(type) && !"photo".equals(type)) continue;
                    String url = firstHttp(m.get("thumb"), m.get("large"), m.get("url"));
                    if (url != null) return url;
                }
            }
            if (media.get("thumbs") instanceof List<?> thumbs) {
                for (Object thumb : thumbs) {
                    String url = firstHttp(thumb);
                    if (url != null) return url;
                }
            }
        }
        if (snapshot.get("imageUrls") instanceof List<?> urls) {
            for (Object u : urls) {
                String url = firstHttp(u);
                if (url != null) return url;
            }
        }
        return null;
    }

    private static String firstHttp(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof String s && (s.startsWith("https://") || s.startsWith("http://"))) return s;
        }
        return null;
    }
}
