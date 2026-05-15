package com.surimap.marker.photo.service;

import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.photo.repository.PhotoRepository;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerPhotoDraftService {

  private static final Set<PhotoStatus> COUNTED_STATUSES =
      Set.of(PhotoStatus.PENDING_UPLOAD, PhotoStatus.ATTACHED);

  private final ObjectStoragePort storagePort;
  private final PhotoRepository photoRepository;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerOpBindingValidator markerOpBindingValidator;
  private final Clock clock;
  private final java.util.function.Supplier<UUID> photoIdSupplier;
  private final IdempotentResponseCache idempotentResponseCache;
  private final ObjectKeyGenerator objectKeyGenerator = new ObjectKeyGenerator();

  @Autowired
  public MarkerPhotoDraftService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerOpBindingValidator markerOpBindingValidator,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        storagePort,
        photoRepository,
        markerWriteGuardPort,
        markerOpBindingValidator,
        Clock.systemUTC(),
        UUID::randomUUID,
        idempotentResponseCacheProvider.getIfAvailable());
  }

  public MarkerPhotoDraftService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerOpBindingValidator markerOpBindingValidator,
      Clock clock,
      java.util.function.Supplier<UUID> photoIdSupplier,
      IdempotentResponseCache idempotentResponseCache) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.photoRepository = Objects.requireNonNull(photoRepository);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerOpBindingValidator = Objects.requireNonNull(markerOpBindingValidator);
    this.clock = Objects.requireNonNull(clock);
    this.photoIdSupplier = Objects.requireNonNull(photoIdSupplier);
    this.idempotentResponseCache = idempotentResponseCache;
  }

  @Transactional
  public PhotoUploadUrlResponse createUploadUrl(
      MarkerCreatePhotoUploadUrlRequest request, PhotoRequestContext context) {
    requireRequest(request);
    requireWriteContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "POST /api/markers/photos/upload-url",
          context.idempotencyKey(),
          fingerprint("marker-create-photo-upload-url", request),
          201,
          PhotoUploadUrlResponse.class,
          () -> createNewUploadUrl(request, context),
          this::metadataForUpload);
    }
    return createNewUploadUrl(request, context);
  }

  private PhotoUploadUrlResponse createNewUploadUrl(
      MarkerCreatePhotoUploadUrlRequest request, PhotoRequestContext context) {
    markerWriteGuardPort.requireCreateAccess(
        request.incidentId(),
        validateOpBinding(request.incidentId(), request.opId()),
        new MarkerRequestContext(context.authentication(), context.idempotencyKey()));
    requirePhotoSlot(request.markerId());

    UUID photoId = photoIdSupplier.get();
    Instant expiresAt = clock.instant().plus(PhotoService.UPLOAD_URL_TTL);
    String objectKey =
        objectKeyGenerator.generate(
            request.incidentId(), request.markerId(), photoId, request.contentType());
    String uploadUrl =
        storagePort
            .generatePresignedUrl(
                objectKey,
                request.contentType(),
                request.sizeBytes(),
                request.checksumSha256(),
                PhotoService.UPLOAD_URL_TTL)
            .uploadUrl();
    MarkerPhoto photo =
        new MarkerPhoto(
            photoId,
            request.markerId(),
            objectKey,
            request.contentType(),
            request.sizeBytes(),
            request.checksumSha256(),
            expiresAt);
    photoRepository.save(photo);
    return new PhotoUploadUrlResponse(
        photo.id(), uploadUrl, expiresAt, PhotoService.MAX_SIZE_BYTES, photo.version());
  }

  private void requireRequest(MarkerCreatePhotoUploadUrlRequest request) {
    if (request == null
        || request.markerId() == null
        || request.incidentId() == null
        || request.opId() == null
        || !PhotoService.ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw conflict();
    }
    if (request.sizeBytes() <= 0 || request.sizeBytes() > PhotoService.MAX_SIZE_BYTES) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private void requireWriteContext(PhotoRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.authentication().channel())) {
      throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
      throw conflict();
    }
  }

  private void requirePhotoSlot(UUID markerId) {
    long count = photoRepository.countByMarkerIdAndStatusIn(markerId, COUNTED_STATUSES);
    if (count >= PhotoService.MAX_PHOTOS_PER_MARKER) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private UUID validateOpBinding(UUID incidentId, UUID requestedOpId) {
    try {
      return markerOpBindingValidator.validate(incidentId, requestedOpId);
    } catch (OpRequiredException exception) {
      throw new PhotoApiException(exception.errorCode(), HttpStatus.CONFLICT);
    } catch (OpMismatchException exception) {
      throw new PhotoApiException(exception.errorCode(), HttpStatus.CONFLICT);
    }
  }

  private ResponseMetadata metadataForUpload(PhotoUploadUrlResponse response) {
    return new ResponseMetadata(
        response.photoId().toString(),
        PhotoStatus.PENDING_UPLOAD.name(),
        response.version(),
        response.version());
  }

  private PhotoApiException conflict() {
    return new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private String fingerprint(String operation, Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed =
          digest.digest(
              (operation + ":" + String.valueOf(request)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
