package com.carmarket.importorder.service;

import com.carmarket.importorder.dto.ImportOrderDtos.FilePatchRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.exception.ApiException;
import com.carmarket.importorder.kafka.ImportOrderEventProducer;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.security.Actor;
import com.carmarket.importorder.storage.FileStorage;
import com.carmarket.importorder.validation.FileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Documents and photos of an order. Types are recognised by magic bytes, never by the client's
 * Content-Type. Every endpoint answers 503 while storage is not configured.
 */
@Slf4j
@Service
public class OrderFileService {

    static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    static final long MAX_PHOTO_BYTES = 15L * 1024 * 1024;
    static final int MAX_PHOTOS_PER_UPLOAD = 30;
    static final int MAX_DOCUMENTS_PER_UPLOAD = 10;
    private static final int MAX_NAME_LENGTH = 120;

    private final ImportOrderFileRepository files;
    private final OrderLookup lookup;
    private final FileStorage storage;
    private final TransactionTemplate transaction;
    private final ImportOrderEventProducer eventProducer;

    public OrderFileService(ImportOrderFileRepository files, OrderLookup lookup, FileStorage storage,
                            PlatformTransactionManager transactionManager, ImportOrderEventProducer eventProducer) {
        this.files = files;
        this.lookup = lookup;
        this.storage = storage;
        this.eventProducer = eventProducer;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    record ValidatedFile(MultipartFile file, FileType type, String originalName) {
    }

    /**
     * Validates every file first, uploads them to S3, then writes the rows in one transaction.
     * If anything fails after the first upload, the objects already uploaded are deleted again.
     */
    public List<FileView> upload(Actor actor, UUID orderId, List<MultipartFile> uploads, FileKind kind,
                                 String category, boolean clientVisible) {
        storage.requireEnabled();
        ImportOrder order = transaction.execute(status -> lookup.forWrite(actor, orderId));
        if (!kind.isValidCategory(category)) throw ApiException.badRequest("Invalid category for " + kind);
        List<ValidatedFile> validated = validate(uploads, kind);

        List<String> uploadedKeys = new ArrayList<>();
        List<ImportOrderFile> rows = new ArrayList<>();
        try {
            for (ValidatedFile v : validated) {
                String key = FileStorage.newKey(order.getId(), kind, v.type().extension());
                try (InputStream in = v.file().getInputStream()) {
                    storage.put(key, in, v.file().getSize(), v.type().contentType());
                }
                uploadedKeys.add(key);
                rows.add(ImportOrderFile.builder()
                    .orderId(order.getId())
                    .kind(kind)
                    .category(category)
                    .s3Key(key)
                    .originalName(v.originalName())
                    .contentType(v.type().contentType())
                    .sizeBytes(v.file().getSize())
                    .clientVisible(clientVisible)
                    .uploadedBy(actor.userId())
                    .build());
            }
            List<ImportOrderFile> saved = transaction.execute(status -> {
                ImportOrder current = lookup.forWrite(actor, orderId); // still there and still ours
                List<ImportOrderFile> stored = files.saveAll(rows);
                if (kind == FileKind.DOCUMENT && clientVisible) {
                    eventProducer.publishClientUpdate(current, ImportOrderEventProducer.Reason.DOCUMENT);
                }
                return stored;
            });
            log.info("{} {} file(s) uploaded to import order {} by {}", saved.size(), kind, orderId, actor.userId());
            return saved.stream().map(OrderViews::file).toList();
        } catch (IOException e) {
            cleanUp(uploadedKeys);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            cleanUp(uploadedKeys);
            throw e;
        }
    }

    @Transactional
    public FileView patch(Actor actor, UUID orderId, UUID fileId, FilePatchRequest request) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        ImportOrderFile file = files.findByIdAndOrderId(fileId, orderId).orElseThrow(ApiException::notFound);
        boolean wasVisible = file.isClientVisible();
        if (request.category() != null) {
            if (!file.getKind().isValidCategory(request.category())) {
                throw ApiException.badRequest("Invalid category for " + file.getKind());
            }
            file.setCategory(request.category());
        }
        if (request.clientVisible() != null) file.setClientVisible(request.clientVisible());
        // A document the dealer just shared is news for the client, like a fresh upload
        if (file.getKind() == FileKind.DOCUMENT && file.isClientVisible() && !wasVisible) {
            eventProducer.publishClientUpdate(order, ImportOrderEventProducer.Reason.DOCUMENT);
        }
        return OrderViews.file(file);
    }

    /** Object first, then the row: a failed S3 call leaves everything as it was. */
    @Transactional
    public void delete(Actor actor, UUID orderId, UUID fileId) {
        storage.requireEnabled();
        lookup.forWrite(actor, orderId);
        ImportOrderFile file = files.findByIdAndOrderId(fileId, orderId).orElseThrow(ApiException::notFound);
        storage.delete(file.getS3Key());
        files.delete(file);
    }

    @Transactional(readOnly = true)
    public PresignedUrl downloadUrl(Actor actor, UUID orderId, UUID fileId) {
        storage.requireEnabled();
        lookup.forRead(actor, orderId);
        return presign(files.findByIdAndOrderId(fileId, orderId).orElseThrow(ApiException::notFound));
    }

    public PresignedUrl presign(ImportOrderFile file) {
        return storage.presignGet(file.getS3Key(), file.getOriginalName(), file.getContentType(),
            file.getKind() == FileKind.DOCUMENT);
    }

    // ── validation ──

    List<ValidatedFile> validate(List<MultipartFile> uploads, FileKind kind) {
        if (uploads == null || uploads.isEmpty()) throw ApiException.badRequest("No files");
        int maxCount = kind == FileKind.PHOTO ? MAX_PHOTOS_PER_UPLOAD : MAX_DOCUMENTS_PER_UPLOAD;
        if (uploads.size() > maxCount) throw ApiException.badRequest("At most " + maxCount + " files per upload");
        long maxBytes = kind == FileKind.PHOTO ? MAX_PHOTO_BYTES : MAX_DOCUMENT_BYTES;

        List<ValidatedFile> result = new ArrayList<>();
        for (MultipartFile upload : uploads) {
            String name = sanitizeName(upload.getOriginalFilename());
            if (upload.isEmpty()) throw ApiException.badRequest(name + ": empty file");
            if (upload.getSize() > maxBytes) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    name + ": larger than " + maxBytes / (1024 * 1024) + " MB");
            }
            FileType type = FileType.detect(header(upload))
                .filter(t -> kind == FileKind.DOCUMENT || t.isImage())
                .orElseThrow(() -> ApiException.badRequest(name + ": " + (kind == FileKind.PHOTO
                    ? "only JPEG, PNG or WEBP photos" : "only PDF, JPEG, PNG or WEBP documents")));
            result.add(new ValidatedFile(upload, type, withExtension(name, type)));
        }
        return result;
    }

    private static byte[] header(MultipartFile upload) {
        try (InputStream in = upload.getInputStream()) {
            return in.readNBytes(FileType.HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Base name only (no client path), without control characters and characters that are
     * unsafe in file names or headers; letters of any alphabet (e.g. Polish) are kept.
     */
    static String sanitizeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
            .replaceAll("[\\p{Cntrl}\"*:<>?|;%]", "_")
            .replaceAll("\\s+", " ")
            .trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isBlank()) name = "plik";
        if (name.length() > MAX_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME_LENGTH - ext.length()) + ext;
        }
        return name;
    }

    /** Makes the extension match the detected type, so a renamed file downloads as what it is. */
    static String withExtension(String name, FileType type) {
        String lower = name.toLowerCase();
        boolean matches = lower.endsWith("." + type.extension())
            || type == FileType.JPEG && lower.endsWith(".jpeg");
        if (matches) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "." + type.extension();
    }

    private void cleanUp(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not remove {} after a failed upload: {}", key, e.getMessage());
            }
        }
    }
}
