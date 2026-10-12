package com.carmarket.importorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A document or photo of an order; the bytes are in the private bucket under {@link #s3Key}. */
@Entity
@Table(name = "import_order_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOrderFile {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileKind kind;

    /** {@link DocumentCategory} or {@link PhotoStage} name, depending on {@link #kind}. */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "s3_key", nullable = false, unique = true, length = 300)
    private String s3Key;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "client_visible", nullable = false)
    private boolean clientVisible;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
