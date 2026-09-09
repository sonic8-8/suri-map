package com.surimap.app.service.marker;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.repository.PhotoMapper;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerPhotoDraftService {

  private static final Set<PhotoStatus> COUNTED_STATUSES =
      Set.of(PhotoStatus.PENDING_UPLOAD, PhotoStatus.ATTACHED);

  private final ObjectStoragePort storagePort;
  private final PhotoMapper photoMapper;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerOpBindingValidator markerOpBindingValidator;
  private final Clock clock = Clock.systemUTC();
  private final IdempotentResponseCache idempotentResponseCache;
  private final ObjectKeyGenerator objectKeyGenerator = new ObjectKeyGenerator();

  public MarkerPhotoDraftService(
      ObjectStoragePort storagePort,
      PhotoMapper photoMapper,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerOpBindingValidator markerOpBindingValidator,
      IdempotentResponseCache idempotentResponseCache) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.photoMapper = Objects.requireNonNull(photoMapper);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerOpBindingValidator = Objects.requireNonNull(markerOpBindingValidator);
    this.idempotentResponseCache = Objects.requireNonNull(idempotentResponseCache);
  }

  @Transactional
  public PhotoUploadUrlResponse createUploadUrl(
      MarkerCreatePhotoUploadUrlRequest request, PhotoRequestContext context) {
    requireRequest(request);
    requireWriteContext(context);
    return idempotentResponseCache.replayOrRun(
        "POST /api/markers/photos/upload-url",
        context.getIdempotencyKey(),
        request,
        () -> formatLegacyUploadRequestBody(request),
        201,
        PhotoUploadUrlResponse.class,
        () -> createNewUploadUrl(request, context),
        this::metadataForUpload);
  }

  private PhotoUploadUrlResponse createNewUploadUrl(
      MarkerCreatePhotoUploadUrlRequest request, PhotoRequestContext context) {
    markerWriteGuardPort.requireCreateAccess(
        request.incidentId(),
        validateOpBinding(request.incidentId(), request.opId()),
        new MarkerRequestContext(context.getAuthentication(), context.getIdempotencyKey()));
    requirePhotoSlot(request.markerId());

    UUID photoId = UUID.randomUUID();
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
        MarkerPhoto.builder()
            .id(photoId)
            .markerId(request.markerId())
            .objectKey(objectKey)
            .contentType(request.contentType())
            .sizeBytes(request.sizeBytes())
            .checksumSha256(request.checksumSha256())
            .uploadUrlExpiresAt(expiresAt)
            .build();
    photoMapper.upsert(photo);
    return new PhotoUploadUrlResponse(
        photo.getId(), uploadUrl, expiresAt, PhotoService.MAX_SIZE_BYTES, photo.getVersion());
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
    if (context == null || context.getAuthentication() == null) {
      throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.getAuthentication().channel())) {
      throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (context.getIdempotencyKey() == null || context.getIdempotencyKey().isBlank()) {
      throw conflict();
    }
  }

  private void requirePhotoSlot(UUID markerId) {
    long count = photoMapper.countByMarkerIdAndStatusIn(markerId, COUNTED_STATUSES);
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

  // ponytail: 과거 마커 생성 전 업로드 요청 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
  private String formatLegacyUploadRequestBody(MarkerCreatePhotoUploadUrlRequest request) {
    return ("marker-create-photo-upload-url:MarkerCreatePhotoUploadUrlRequest[markerId=%s, "
            + "incidentId=%s, opId=%s, contentType=%s, sizeBytes=%s, checksumSha256=%s]")
        .formatted(
            request.markerId(),
            request.incidentId(),
            request.opId(),
            request.contentType(),
            request.sizeBytes(),
            request.checksumSha256());
  }
}
