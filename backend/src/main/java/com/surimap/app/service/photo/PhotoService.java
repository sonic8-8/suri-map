package com.surimap.app.service.photo;

import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.repository.PhotoMapper;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PhotoService {

  public static final long MAX_SIZE_BYTES = 10_485_760L;
  public static final int MAX_PHOTOS_PER_MARKER = 10;
  public static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);
  public static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<PhotoStatus> COUNTED_STATUSES =
      Set.of(PhotoStatus.PENDING_UPLOAD, PhotoStatus.ATTACHED);

  private final ObjectStoragePort storagePort;
  private final MarkerRuntimeGuardMapper markerRuntimeGuardMapper;
  private final EventHub eventHub;
  private final MarkerMapper markerMapper;
  private final Clock clock = Clock.systemUTC();
  private final ObjectKeyGenerator objectKeyGenerator = new ObjectKeyGenerator();
  private final IdempotentResponseCache idempotentResponseCache;
  private final TransactionTemplate photoFailureTransaction;
  private final PhotoMapper photoMapper;

  public PhotoService(
      ObjectStoragePort storagePort,
      MarkerRuntimeGuardMapper markerRuntimeGuardMapper,
      EventHub eventHub,
      MarkerMapper markerMapper,
      PlatformTransactionManager transactionManager,
      PhotoMapper photoMapper,
      IdempotentResponseCache idempotentResponseCache) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.markerRuntimeGuardMapper = Objects.requireNonNull(markerRuntimeGuardMapper);
    this.eventHub = Objects.requireNonNull(eventHub);
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.idempotentResponseCache = Objects.requireNonNull(idempotentResponseCache);
    this.photoMapper = Objects.requireNonNull(photoMapper);
    this.photoFailureTransaction = new TransactionTemplate(transactionManager);
    this.photoFailureTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional
  public PhotoUploadUrlServiceResponse createUploadUrl(PhotoUploadUrlServiceRequest request) {
    if (request == null) {
      throw conflict("write_conflict");
    }
    UUID markerId = request.getMarkerId();
    PhotoRequestContext context = request.getContext();
    requireMarkerId(markerId);
    requireWriteContext(context);
    validateUploadRequest(request);
    return idempotentResponseCache.replayOrRun(
        "POST /api/markers/" + markerId + "/photos/upload-url",
        context.getIdempotencyKey(),
        request,
        () -> formatLegacyUploadRequestBody(markerId, request),
        201,
        PhotoUploadUrlServiceResponse.class,
        () -> createNewUploadUrl(request),
        this::metadataForUpload);
  }

  private PhotoUploadUrlServiceResponse createNewUploadUrl(PhotoUploadUrlServiceRequest request) {
    UUID markerId = request.getMarkerId();
    Marker marker = requirePhotoAccess(markerId, request.getContext());
    requirePhotoSlot(markerId);

    UUID photoId = UUID.randomUUID();
    Instant expiresAt = clock.instant().plus(UPLOAD_URL_TTL);
    String objectKey =
        objectKeyGenerator.generate(
            marker.getIncidentId(), markerId, photoId, request.getContentType());
    String uploadUrl =
        storagePort
            .generatePresignedUrl(
                objectKey,
                request.getContentType(),
                request.getSizeBytes(),
                request.getChecksumSha256(),
                UPLOAD_URL_TTL)
            .uploadUrl();
    MarkerPhoto photo =
        MarkerPhoto.builder()
            .id(photoId)
            .markerId(markerId)
            .objectKey(objectKey)
            .contentType(request.getContentType())
            .sizeBytes(request.getSizeBytes())
            .checksumSha256(request.getChecksumSha256())
            .uploadUrlExpiresAt(expiresAt)
            .build();
    photoMapper.upsert(photo);

    return PhotoUploadUrlServiceResponse.from(photo, uploadUrl, MAX_SIZE_BYTES);
  }

  @Transactional
  public PhotoAttachResponse attach(
      UUID markerId, UUID photoId, PhotoAttachRequest request, PhotoRequestContext context) {
    requireMarkerId(markerId);
    requirePhotoId(photoId);
    requireWriteContext(context);
    validateAttachRequest(request);
    return idempotentResponseCache.replayOrRun(
        "POST /api/markers/" + markerId + "/photos/" + photoId + "/attach",
        context.getIdempotencyKey(),
        request,
        () -> formatLegacyAttachRequestBody(markerId, photoId, request),
        200,
        PhotoAttachResponse.class,
        () -> attachUploadedPhoto(markerId, photoId, request, context),
        this::metadataForAttach);
  }

  private PhotoAttachResponse attachUploadedPhoto(
      UUID markerId, UUID photoId, PhotoAttachRequest request, PhotoRequestContext context) {
    Marker marker = requirePhotoAccess(markerId, context);

    MarkerPhoto photo = photoMapper.findById(photoId).orElseThrow(() -> conflict("write_conflict"));
    requireAttachableMarker(markerId, photo);
    requireOpenUploadUrl(photo);
    ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
    requireMatchingMetadata(photo, request, objectMetadata);

    long expectedPhotoVersion = photo.getVersion();
    photo.attach(clock.instant(), request.width(), request.height());
    if (photoMapper.attachPendingPhoto(photo, expectedPhotoVersion) != 1) {
      throw conflict("write_conflict");
    }
    long expectedMarkerVersion = marker.getVersion();
    marker.markUpdated(expectedMarkerVersion);
    int updated =
        markerMapper.updateMarkerStatusVersion(
            markerId, expectedMarkerVersion, marker.getStatus(), marker.getVersion());
    if (updated != 1) {
      throw conflict("write_conflict");
    }
    var response =
        new PhotoAttachResponse(
            photo.getId(),
            photo.getStatus().name(),
            photo.getVersion(),
            markerId,
            marker.getVersion());
    publishMarkerPhotoUpdate(marker, context.getAuthentication().policePhoneId(), photo);
    return response;
  }

  private void validateUploadRequest(PhotoUploadUrlServiceRequest request) {
    if (request == null || !ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
      throw conflict("write_conflict");
    }
    if (request.getSizeBytes() <= 0 || request.getSizeBytes() > MAX_SIZE_BYTES) {
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
    if (context == null || context.getAuthentication() == null) {
      throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (context.getIdempotencyKey() == null || context.getIdempotencyKey().isBlank()) {
      throw conflict("write_conflict");
    }
  }

  private Marker requirePhotoAccess(UUID markerId, PhotoRequestContext context) {
    requireAppContext(context);
    Marker marker = findActiveMarker(markerId);
    UUID accountId = context.getAuthentication().accountId();

    requireOpenIncident(marker.getIncidentId());
    requireAccountAssignment(marker.getIncidentId(), accountId);
    requireCurrentOp(marker);
    requireActiveDutyShift(marker.getOperationalPeriodId(), accountId);
    requireAppOwnFieldMarker(marker, accountId);

    return marker;
  }

  private void requireAppContext(PhotoRequestContext context) {
    if (context == null || context.getAuthentication() == null) {
      throw denied();
    }
    if (!"APP".equals(context.getAuthentication().channel())) {
      throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
  }

  private Marker findActiveMarker(UUID markerId) {
    requireMarkerId(markerId);
    Marker marker = markerMapper.findById(markerId).orElseThrow(() -> conflict("write_conflict"));
    if ("DELETED".equals(marker.getStatus())) {
      throw conflict("write_conflict");
    }
    return marker;
  }

  private void requireOpenIncident(UUID incidentId) {
    String status =
        markerRuntimeGuardMapper.findIncidentStatus(incidentId).orElseThrow(PhotoService::denied);
    if ("OPEN".equals(status)) {
      return;
    }
    if ("CLOSED".equals(status)) {
      throw new PhotoApiException("incident_closed", HttpStatus.CONFLICT);
    }
    throw denied();
  }

  private void requireAccountAssignment(UUID incidentId, UUID accountId) {
    if (markerRuntimeGuardMapper.countActiveAssignmentsByAccountId(accountId) == 0) {
      throw new PhotoApiException("team_not_assigned", HttpStatus.FORBIDDEN);
    }
    if (markerRuntimeGuardMapper.countActiveIncidentAssignment(incidentId, accountId) == 0) {
      throw denied();
    }
  }

  private void requireCurrentOp(Marker marker) {
    UUID currentOpId =
        markerRuntimeGuardMapper
            .findCurrentOpId(marker.getIncidentId())
            .orElseThrow(() -> new PhotoApiException("op_required", HttpStatus.CONFLICT));
    if (!currentOpId.equals(marker.getOperationalPeriodId())) {
      throw new PhotoApiException("op_mismatch", HttpStatus.CONFLICT);
    }
  }

  private void requireActiveDutyShift(UUID opId, UUID accountId) {
    markerRuntimeGuardMapper
        .findActiveDutyShiftIdByAccount(opId, accountId)
        .orElseThrow(
            () -> new PhotoApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN));
  }

  private void requireAppOwnFieldMarker(Marker marker, UUID accountId) {
    if (!"APP".equals(marker.getMarkerSource())
        || !accountId.equals(marker.getCreatedByAccountId())) {
      throw denied();
    }
  }

  private static PhotoApiException denied() {
    return new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private void requirePhotoSlot(UUID markerId) {
    long count = photoMapper.countByMarkerIdAndStatusIn(markerId, COUNTED_STATUSES);
    if (count >= MAX_PHOTOS_PER_MARKER) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
  }

  private void requireAttachableMarker(UUID markerId, MarkerPhoto photo) {
    if (!photo.getMarkerId().equals(markerId) || !photo.isOpenForAttach()) {
      throw conflict("write_conflict");
    }
  }

  private void requireOpenUploadUrl(MarkerPhoto photo) {
    if (!photo.getUploadUrlExpiresAt().isAfter(clock.instant())) {
      failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict("write_conflict");
    }
  }

  private void requireMatchingMetadata(
      MarkerPhoto photo,
      PhotoAttachRequest request,
      ObjectStoragePort.ObjectMetadata objectMetadata) {
    if (photo.getSizeBytes() != request.sizeBytes()
        || !photo.getContentType().equals(request.contentType())
        || !checksumMatches(
            photo.getChecksumSha256(), request.checksumSha256(), objectMetadata.checksumSha256())
        || !metadataMatches(photo, objectMetadata)) {
      failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict("write_conflict");
    }
  }

  private boolean metadataMatches(
      MarkerPhoto photo, ObjectStoragePort.ObjectMetadata objectMetadata) {
    return photo.getObjectKey().equals(objectMetadata.objectKey())
        && photo.getContentType().equals(objectMetadata.contentType())
        && photo.getSizeBytes() == objectMetadata.sizeBytes();
  }

  public void failPendingPhoto(UUID photoId, long expectedVersion) {
    // 같은 클래스에서 호출해도 새 트랜잭션으로 저장하여, 첨부 거부 시 실패 상태까지 롤백되지 않게 한다.
    photoFailureTransaction.executeWithoutResult(
        transaction -> photoMapper.failPendingPhoto(photoId, expectedVersion));
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
    return storagePort
        .headObject(photo.getObjectKey())
        .orElseThrow(() -> conflict("write_conflict"));
  }

  public void publishMarkerPhotoUpdate(Marker marker, UUID policePhoneId, MarkerPhoto photo) {
    if (marker == null
        || photo == null
        || marker.getId() == null
        || marker.getIncidentId() == null
        || marker.getVersion() <= 0) {
      throw conflict("write_conflict");
    }

    Map<String, Object> photoDelta = new LinkedHashMap<>();
    photoDelta.put("photoId", photo.getId().toString());
    photoDelta.put("status", photo.getStatus().name());
    photoDelta.put("version", photo.getVersion());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", marker.getId().toString());
    payload.put("incidentId", marker.getIncidentId().toString());
    payload.put(
        "opId",
        marker.getOperationalPeriodId() == null
            ? null
            : marker.getOperationalPeriodId().toString());
    payload.put("policePhoneId", policePhoneId == null ? null : policePhoneId.toString());
    payload.put("status", marker.getStatus());
    payload.put("version", marker.getVersion());
    payload.put("photoDelta", photoDelta);

    eventHub.publish(
        new PublishRequest(
            MarkerEventIds.eventId("MARKER_UPDATED", marker.getId(), marker.getVersion()),
            marker.getIncidentId(),
            "MARKER_UPDATED",
            1,
            "marker",
            marker.getId(),
            clock.instant(),
            payload));
  }

  private PhotoApiException conflict(String error) {
    return new PhotoApiException(error, HttpStatus.CONFLICT);
  }

  private ResponseMetadata metadataForUpload(PhotoUploadUrlServiceResponse response) {
    return new ResponseMetadata(
        response.getPhotoId().toString(),
        PhotoStatus.PENDING_UPLOAD.name(),
        response.getVersion(),
        response.getVersion());
  }

  private ResponseMetadata metadataForAttach(PhotoAttachResponse response) {
    return new ResponseMetadata(
        response.photoId().toString(),
        response.status(),
        response.version(),
        response.markerVersion());
  }

  // ponytail: 과거 업로드 요청 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
  private String formatLegacyUploadRequestBody(
      UUID markerId, PhotoUploadUrlServiceRequest request) {
    return "upload-url:%s:PhotoUploadUrlRequest[contentType=%s, sizeBytes=%s, checksumSha256=%s]"
        .formatted(
            markerId,
            request.getContentType(),
            request.getSizeBytes(),
            request.getChecksumSha256());
  }

  // ponytail: 과거 첨부 요청 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
  private String formatLegacyAttachRequestBody(
      UUID markerId, UUID photoId, PhotoAttachRequest request) {
    return ("attach:%s:%s:PhotoAttachRequest[sizeBytes=%s, contentType=%s, "
            + "width=%s, height=%s, checksumSha256=%s]")
        .formatted(
            markerId,
            photoId,
            request.sizeBytes(),
            request.contentType(),
            request.width(),
            request.height(),
            request.checksumSha256());
  }
}
