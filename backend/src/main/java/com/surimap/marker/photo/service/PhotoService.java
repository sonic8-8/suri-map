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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired
  public PhotoService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      PhotoWriteGuardPort photoWriteGuardPort,
      PhotoEventPublisher photoEventPublisher) {
    this(storagePort, photoRepository, photoWriteGuardPort, photoEventPublisher, Clock.systemUTC());
  }

  public PhotoService(
      ObjectStoragePort storagePort,
      PhotoRepository photoRepository,
      PhotoWriteGuardPort photoWriteGuardPort,
      PhotoEventPublisher photoEventPublisher,
      Clock clock) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.photoRepository = Objects.requireNonNull(photoRepository);
    this.photoWriteGuardPort = Objects.requireNonNull(photoWriteGuardPort);
    this.photoEventPublisher = Objects.requireNonNull(photoEventPublisher);
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional
  public PhotoUploadUrlResponse createUploadUrl(
      UUID markerId, PhotoUploadUrlRequest request, PhotoRequestContext context) {
    requireMarkerId(markerId);
    PhotoMarkerContext markerContext =
        photoWriteGuardPort.requireUploadUrlAccess(markerId, context);
    validateUploadRequest(request);
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
    PhotoMarkerContext markerContext =
        photoWriteGuardPort.requireAttachAccess(markerId, photoId, context);
    validateAttachRequest(request);

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
}
