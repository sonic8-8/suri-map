package com.surimap.marker.photo.service;

import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.dto.PhotoAttachResult;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.repository.PhotoRepository;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoService {

  public static final long MAX_SIZE_BYTES = 10_485_760L;
  public static final int MAX_PHOTOS_PER_MARKER = 10;
  private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<PhotoStatus> COUNTED_STATUSES =
      Set.of(PhotoStatus.PENDING_UPLOAD, PhotoStatus.ATTACHED);

  private final ObjectStoragePort storagePort;
  private final PhotoRepository photoRepository;
  private final PhotoWriteGuardPort photoWriteGuardPort;
  private final PhotoEventPublisher photoEventPublisher;
  private final Clock clock;
  private final ObjectKeyGenerator objectKeyGenerator = new ObjectKeyGenerator();
  private final IdempotentResponseCache idempotentResponseCache;

  @Autowired
  public PhotoService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      PhotoWriteGuardPort photoWriteGuardPort,
      PhotoEventPublisher photoEventPublisher,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        storagePort,
        photoRepository,
        photoWriteGuardPort,
        photoEventPublisher,
        Clock.systemUTC(),
        idempotentResponseCacheProvider.getIfAvailable());
  }

  public PhotoService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      PhotoWriteGuardPort photoWriteGuardPort,
      PhotoEventPublisher photoEventPublisher,
      Clock clock) {
    this(storagePort, photoRepository, photoWriteGuardPort, photoEventPublisher, clock, null);
  }

  private PhotoService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      PhotoWriteGuardPort photoWriteGuardPort,
      PhotoEventPublisher photoEventPublisher,
      Clock clock,
      IdempotentResponseCache idempotentResponseCache) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.photoRepository = Objects.requireNonNull(photoRepository);
    this.photoWriteGuardPort = Objects.requireNonNull(photoWriteGuardPort);
    this.photoEventPublisher = Objects.requireNonNull(photoEventPublisher);
    this.clock = Objects.requireNonNull(clock);
    this.idempotentResponseCache = idempotentResponseCache;
  }

  @Transactional
  public PhotoUploadUrlResponse createUploadUrl(
      UUID markerId, PhotoUploadUrlRequest request, PhotoRequestContext context) {
    requireMarkerId(markerId);
    requireWriteContext(context);
    validateUploadRequest(request);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "POST /api/markers/" + markerId + "/photos/upload-url",
          context.idempotencyKey(),
          fingerprint("upload-url:" + markerId, request),
          201,
          PhotoUploadUrlResponse.class,
          () -> createNewUploadUrl(markerId, request, context),
          this::metadataForUpload);
    }
    return createNewUploadUrl(markerId, request, context);
  }

  private PhotoUploadUrlResponse createNewUploadUrl(
      UUID markerId, PhotoUploadUrlRequest request, PhotoRequestContext context) {
    PhotoMarkerContext markerContext =
        photoWriteGuardPort.requireUploadUrlAccess(markerId, context);
    requirePhotoSlot(markerId);

    UUID photoId = UUID.randomUUID();
    Instant expiresAt = clock.instant().plus(UPLOAD_URL_TTL);
    String objectKey =
        objectKeyGenerator.generate(
            markerContext.incidentId(), markerId, photoId, request.contentType());
    String uploadUrl =
        storagePort
            .generatePresignedUrl(
                objectKey,
                request.contentType(),
                request.sizeBytes(),
                request.checksumSha256(),
                UPLOAD_URL_TTL)
            .uploadUrl();
    MarkerPhoto photo =
        new MarkerPhoto(
            photoId,
            markerId,
            objectKey,
            request.contentType(),
            request.sizeBytes(),
            request.checksumSha256(),
            expiresAt);
    photoRepository.save(photo);

    return new PhotoUploadUrlResponse(
        photo.id(), uploadUrl, expiresAt, MAX_SIZE_BYTES, photo.version());
  }

  @Transactional
  public PhotoAttachResult attach(
      UUID markerId, UUID photoId, PhotoAttachRequest request, PhotoRequestContext context) {
    requireMarkerId(markerId);
    requirePhotoId(photoId);
    requireWriteContext(context);
    validateAttachRequest(request);
    if (idempotentResponseCache != null) {
      AtomicReference<PhotoAttachResult> attachedResult = new AtomicReference<>();
      PhotoAttachResponse response =
          idempotentResponseCache.replayOrRun(
              "POST /api/markers/" + markerId + "/photos/" + photoId + "/attach",
              context.idempotencyKey(),
              fingerprint("attach:" + markerId + ":" + photoId, request),
              200,
              PhotoAttachResponse.class,
              () -> {
                PhotoAttachResult result = attachUploadedPhoto(markerId, photoId, request, context);
                attachedResult.set(result);
                return result.response();
              },
              this::metadataForAttach);
      return attachedResult.get() == null
          ? new PhotoAttachResult(response, null)
          : attachedResult.get();
    }
    return attachUploadedPhoto(markerId, photoId, request, context);
  }

  private PhotoAttachResult attachUploadedPhoto(
      UUID markerId, UUID photoId, PhotoAttachRequest request, PhotoRequestContext context) {
    PhotoMarkerContext markerContext =
        photoWriteGuardPort.requireAttachAccess(markerId, photoId, context);

    MarkerPhoto photo =
        photoRepository.findById(photoId).orElseThrow(() -> conflict("write_conflict"));
    requireAttachableMarker(markerId, photo);
    requireOpenUploadUrl(photo);
    ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
    requireMatchingMetadata(photo, request, objectMetadata);

    photo.attach(clock.instant(), request.width(), request.height());
    photoRepository.save(photo);
    long markerVersion = markerContext.markerVersion() + 1L;
    var response =
        new PhotoAttachResponse(
            photo.id(), photo.status().name(), photo.version(), markerId, markerVersion);
    PublishRequest publishRequest = publishRequest(markerContext, markerVersion, photo);
    photoEventPublisher.publish(publishRequest);
    return new PhotoAttachResult(response, publishRequest);
  }

  private void validateUploadRequest(PhotoUploadUrlRequest request) {
    if (request == null || !ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw conflict("write_conflict");
    }
    if (request.sizeBytes() <= 0 || request.sizeBytes() > MAX_SIZE_BYTES) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private void validateAttachRequest(PhotoAttachRequest request) {
    if (request == null || !ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw conflict("write_conflict");
    }
    if (request.sizeBytes() <= 0 || request.sizeBytes() > MAX_SIZE_BYTES) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private void requireMarkerId(UUID markerId) {
    if (markerId == null) {
      throw conflict("write_conflict");
    }
  }

  private void requirePhotoId(UUID photoId) {
    if (photoId == null) {
      throw conflict("write_conflict");
    }
  }

  private void requireWriteContext(PhotoRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
      throw conflict("write_conflict");
    }
  }

  private void requirePhotoSlot(UUID markerId) {
    long count = photoRepository.countByMarkerIdAndStatusIn(markerId, COUNTED_STATUSES);
    if (count >= MAX_PHOTOS_PER_MARKER) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private void requireAttachableMarker(UUID markerId, MarkerPhoto photo) {
    if (!photo.markerId().equals(markerId) || !photo.isOpenForAttach()) {
      throw conflict("write_conflict");
    }
  }

  private void requireOpenUploadUrl(MarkerPhoto photo) {
    if (!photo.uploadUrlExpiresAt().isAfter(clock.instant())) {
      photo.fail();
      photoRepository.save(photo);
      throw conflict("write_conflict");
    }
  }

  private void requireMatchingMetadata(
      MarkerPhoto photo,
      PhotoAttachRequest request,
      ObjectStoragePort.ObjectMetadata objectMetadata) {
    if (photo.sizeBytes() != request.sizeBytes()
        || !photo.contentType().equals(request.contentType())
        || !checksumMatches(
            photo.checksumSha256(), request.checksumSha256(), objectMetadata.checksumSha256())
        || !metadataMatches(photo, objectMetadata)) {
      photo.fail();
      photoRepository.save(photo);
      throw conflict("write_conflict");
    }
  }

  private boolean metadataMatches(
      MarkerPhoto photo, ObjectStoragePort.ObjectMetadata objectMetadata) {
    return photo.objectKey().equals(objectMetadata.objectKey())
        && photo.contentType().equals(objectMetadata.contentType())
        && photo.sizeBytes() == objectMetadata.sizeBytes();
  }

  private boolean checksumMatches(String expected, String requested, String uploaded) {
    if (expected != null
        && (!Objects.equals(expected, requested) || !Objects.equals(expected, uploaded))) {
      return false;
    }
    if (requested != null || uploaded != null) {
      return Objects.equals(requested, uploaded);
    }
    return true;
  }

  private ObjectStoragePort.ObjectMetadata requireUploadedObject(MarkerPhoto photo) {
    return storagePort.headObject(photo.objectKey()).orElseThrow(() -> conflict("write_conflict"));
  }

  private PublishRequest publishRequest(
      PhotoMarkerContext markerContext, long markerVersion, MarkerPhoto photo) {
    return new PublishRequest(
        "MARKER_UPDATED",
        new PublishRequestPayload(
            markerContext.markerId(),
            markerContext.incidentId(),
            markerContext.opId(),
            markerContext.policePhoneId(),
            markerContext.markerStatus(),
            markerVersion,
            new PhotoDelta(photo.id(), photo.status().name(), photo.version())));
  }

  private PhotoApiException conflict(String error) {
    return new PhotoApiException(error, HttpStatus.CONFLICT);
  }

  private ResponseMetadata metadataForUpload(PhotoUploadUrlResponse response) {
    return new ResponseMetadata(
        response.photoId().toString(), PhotoStatus.PENDING_UPLOAD.name(), response.version(), response.version());
  }

  private ResponseMetadata metadataForAttach(PhotoAttachResponse response) {
    return new ResponseMetadata(
        response.photoId().toString(), response.status(), response.version(), response.markerVersion());
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
