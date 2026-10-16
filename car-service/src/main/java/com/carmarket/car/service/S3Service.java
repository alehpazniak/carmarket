package com.carmarket.car.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class S3Service {

    @Value("${aws.s3.bucket}")
    private String bucket;
    @Value("${aws.s3.region}")
    private String region;
    @Value("${aws.s3.secret-key}")
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    private final S3Client s3Client;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<String> uploadImages(UUID carId, List<MultipartFile> files) {
        if (files.size() > 10) {
            throw new IllegalArgumentException("Max 10 images per upload");
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            try {
                String key = "cars/%s/%s%s".formatted(
                    carId,
                    UUID.randomUUID(),
                    getExtension(file)
                );
                s3Client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes())
                );
                String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
                urls.add(url);
                log.info("Uploaded image: {}", url);
            } catch (IOException e) {
                log.error("Failed to upload image: {}", e.getMessage());
            }
        }
        return urls;
    }

    public void deleteImage(String url) {
        String prefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        if (!url.startsWith(prefix)) {
            log.warn("Skipping delete for URL not owned by this bucket: {}", url);
            return;
        }
        String key = url.substring(prefix.length());
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build());
        log.info("Deleted image: {}", url);
    }

    /**
     * A dealer page image (logo, banner, gallery) under dealers/{userId}/. The type comes from the
     * file's magic bytes, not the client's Content-Type.
     */
    public String uploadDealerImage(UUID userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image must be between 1 byte and 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file");
        }
        String[] type = imageType(bytes);
        if (type == null) {
            throw new IllegalArgumentException("Only JPEG, PNG or WEBP images are allowed");
        }
        String key = "dealers/%s/%s.%s".formatted(userId, UUID.randomUUID(), type[1]);
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType(type[0]).build(),
            software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
        String url = publicUrlPrefix() + key;
        log.info("Uploaded dealer image: {}", url);
        return url;
    }

    /** Deletes a dealer page image, but only one under that dealer's own prefix. */
    public boolean deleteDealerImage(UUID userId, String url) {
        String ownPrefix = publicUrlPrefix() + "dealers/" + userId + "/";
        if (url == null || !url.startsWith(ownPrefix) || url.contains("..")) {
            return false;
        }
        deleteImage(url);
        return true;
    }

    private String publicUrlPrefix() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    /** {contentType, extension} from the magic bytes, or null. */
    static String[] imageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new String[]{"image/jpeg", "jpg"};
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new String[]{"image/png", "png"};
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new String[]{"image/webp", "webp"};
        }
        return null;
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large: " + file.getOriginalFilename());
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Invalid file type: " + file.getContentType());
        }
    }

    private String getExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            return original.substring(original.lastIndexOf("."));
        }
        return ".jpg";
    }
}
