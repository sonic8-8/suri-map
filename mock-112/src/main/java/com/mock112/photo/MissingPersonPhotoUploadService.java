package com.mock112.photo;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MissingPersonPhotoUploadService {

    public static final long MAX_SIZE_BYTES = 10_485_760L;
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final MissingPersonPhotoStorage storage;
    private final Clock clock;

    @Autowired
    public MissingPersonPhotoUploadService(MissingPersonPhotoStorage storage) {
        this(storage, Clock.systemUTC());
    }

    MissingPersonPhotoUploadService(MissingPersonPhotoStorage storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public MissingPersonPhotoUploadResult upload(MultipartFile file) {
        validate(file);
        String contentType = normalizedContentType(file.getContentType());
        String objectKey = objectKey(contentType);
        try (InputStream inputStream = file.getInputStream()) {
            return storage.put(objectKey, contentType, file.getSize(), inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read missing person photo upload", exception);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("photo file is required");
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("photo file must be 1 byte to 10MB");
        }
        String contentType = normalizedContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("photo file must be image/jpeg, image/png, or image/webp");
        }
    }

    private String objectKey(String contentType) {
        String datePath = DATE_PATH.format(LocalDate.now(clock));
        return "mock-112/missing-person/"
                + datePath
                + "/"
                + UUID.randomUUID()
                + "."
                + extension(contentType);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String normalizedContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }
}
