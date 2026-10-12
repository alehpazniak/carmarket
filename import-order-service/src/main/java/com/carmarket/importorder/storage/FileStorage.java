package com.carmarket.importorder.storage;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The private import-orders bucket. Objects live under import-orders/{orderId}/{documents|photos}/,
 * the only prefix the IAM user may touch. Downloads go only through short-lived presigned GET URLs;
 * no public object URL is ever built.
 */
@Slf4j
@Service
public class FileStorage {

    public static final String ROOT_PREFIX = "import-orders/";
    private static final int DELETE_BATCH = 1000; // DeleteObjects limit

    private final ImportOrderProperties.S3 config;
    private final S3Client s3;
    private final S3Presigner presigner;

    public FileStorage(ImportOrderProperties properties,
                       ObjectProvider<S3Client> s3,
                       ObjectProvider<S3Presigner> presigner) {
        this.config = properties.s3();
        this.s3 = s3.getIfAvailable();
        this.presigner = presigner.getIfAvailable();
        if (isEnabled()) {
            log.info("Import order storage enabled (region {})", config.region());
        } else {
            log.warn("Import order storage is not configured (IMPORT_ORDER_S3_* / IMPORT_ORDER_AWS_*): file endpoints answer 503");
        }
    }

    public boolean isEnabled() {
        return config.isConfigured() && s3 != null && presigner != null;
    }

    /** @throws ApiException 503 while storage is not configured */
    public void requireEnabled() {
        if (!isEnabled()) throw ApiException.storageNotConfigured();
    }

    public static String orderPrefix(UUID orderId) {
        return ROOT_PREFIX + orderId + "/";
    }

    public static String newKey(UUID orderId, FileKind kind, String extension) {
        return orderPrefix(orderId) + kind.folder() + "/" + UUID.randomUUID() + "." + extension;
    }

    public void put(String key, InputStream content, long size, String contentType) {
        requireEnabled();
        checkKey(key);
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build(),
                RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            throw storageError("upload", key, e);
        }
    }

    public void delete(String key) {
        requireEnabled();
        checkKey(key);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(config.bucket()).key(key).build());
        } catch (SdkException e) {
            throw storageError("delete", key, e);
        }
    }

    /**
     * Deletes the given keys plus anything else left under the order's prefix (e.g. an object
     * whose database row was never written because the upload failed half-way).
     */
    public void deleteOrder(UUID orderId, Collection<String> knownKeys) {
        requireEnabled();
        String prefix = orderPrefix(orderId);
        Set<String> keys = new LinkedHashSet<>(knownKeys);
        try {
            String continuationToken = null;
            do {
                ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(config.bucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
                page.contents().stream().map(S3Object::key).forEach(keys::add);
                continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (continuationToken != null);
            List<String> all = new ArrayList<>(keys);
            all.forEach(FileStorage::checkKey);
            for (int i = 0; i < all.size(); i += DELETE_BATCH) {
                List<ObjectIdentifier> batch = all.subList(i, Math.min(i + DELETE_BATCH, all.size())).stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
                s3.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(config.bucket())
                    .delete(Delete.builder().objects(batch).quiet(true).build())
                    .build());
            }
        } catch (SdkException e) {
            throw storageError("delete", prefix, e);
        }
    }

    /**
     * A GET URL valid for {@code import-order.s3.presign-ttl}. Documents download as an attachment
     * under their original name, photos open inline.
     */
    public PresignedUrl presignGet(String key, String originalName, String contentType, boolean attachment) {
        requireEnabled();
        checkKey(key);
        Duration ttl = config.presignTtl();
        String disposition = attachment
            ? "attachment; filename*=UTF-8''" + encodeRfc5987(originalName)
            : "inline";
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .responseContentDisposition(disposition)
                    .responseContentType(contentType)
                    .build())
                .build());
            return new PresignedUrl(presigned.url().toString(), presigned.expiration());
        } catch (SdkException e) {
            throw storageError("presign", key, e);
        }
    }

    public Duration presignTtl() {
        return config.presignTtl();
    }

    /** Percent-encoding for filename* (RFC 5987): URLEncoder plus %20 instead of +. */
    static String encodeRfc5987(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Defence in depth: never touch anything outside import-orders/ (the IAM policy denies it anyway). */
    private static void checkKey(String key) {
        if (key == null || !key.startsWith(ROOT_PREFIX) || key.contains("..")) {
            throw new IllegalArgumentException("Refusing to use object key outside " + ROOT_PREFIX);
        }
    }

    private static ApiException storageError(String action, String key, SdkException e) {
        log.error("S3 {} failed for {}: {}", action, key, e.getMessage());
        return new ApiException(HttpStatus.BAD_GATEWAY, "Storage error, please try again later");
    }
}
