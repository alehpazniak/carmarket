package com.carmarket.car.controller;

import com.carmarket.car.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Images for the public dealer page (logo, banner, "O nas" gallery). They live in the listing-photo
 * bucket under dealers/{userId}/; user-service stores the returned URLs. DEALER or ADMIN only
 * (see SecurityConfig).
 * <p>
 * POST   /cars/dealer-media         → upload one image, returns {"url": ...}
 * DELETE /cars/dealer-media?url=    → delete one of the caller's own images
 */
@RestController
@RequestMapping("/cars/dealer-media")
@RequiredArgsConstructor
public class DealerMediaController {

    private final S3Service s3Service;

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestHeader("X-User-Id") String userId,
                                                      @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("url", s3Service.uploadDealerImage(UUID.fromString(userId), file)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") String userId, @RequestParam String url) {
        if (!s3Service.deleteDealerImage(UUID.fromString(userId), url)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }
}
