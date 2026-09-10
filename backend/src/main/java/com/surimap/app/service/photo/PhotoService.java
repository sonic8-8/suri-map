package com.surimap.app.service.photo;

import com.surimap.app.service.photo.request.MarkerCreatePhotoServiceRequest;
import com.surimap.app.service.photo.request.MarkerCreatePhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.request.PhotoAttachServiceRequest;
import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.client.storage.ObjectKeyGenerator;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerAccessMapper;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerWriteAccessData;
import com.surimap.domain.marker.MarkerWriteAccessValidator;
import com.surimap.domain.photo.MarkerPhoto;
import com.surimap.domain.photo.PhotoMapper;
import com.surimap.domain.photo.PhotoStatus;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

  private final ObjectStoragePort storagePort;
  private final MarkerAccessMapper markerAccessMapper;
  private final EventHub eventHub;
  private final MarkerMapper markerMapper;
  private final Clock clock = Clock.systemUTC();
  private final ObjectKeyGenerator objectKeyGenerator = new ObjectKeyGenerator();
  private final IdempotentResponseCache idempotentResponseCache;
  private final TransactionTemplate photoFailureTransaction;
  private final PhotoMapper photoMapper;
  private final MarkerWriteAccessValidator markerWriteAccessValidator;

  public PhotoService(
      ObjectStoragePort storagePort,
      MarkerAccessMapper markerAccessMapper,
      EventHub eventHub,
      MarkerMapper markerMapper,
      PlatformTransactionManager transactionManager,
      PhotoMapper photoMapper,
      IdempotentResponseCache idempotentResponseCache,
      MarkerWriteAccessValidator markerWriteAccessValidator) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.markerAccessMapper = Objects.requireNonNull(markerAccessMapper);
    this.eventHub = Objects.requireNonNull(eventHub);
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.idempotentResponseCache = Objects.requireNonNull(idempotentResponseCache);
    this.photoMapper = Objects.requireNonNull(photoMapper);
    this.markerWriteAccessValidator = Objects.requireNonNull(markerWriteAccessValidator);
    this.photoFailureTransaction = new TransactionTemplate(transactionManager);
    this.photoFailureTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional
  public PhotoUploadUrlServiceResponse createUploadUrl(PhotoUploadUrlServiceRequest request) {
    if (request == null) {
      throw conflict();
    }
    UUID markerId = request.getMarkerId();
    PhotoRequestContext context = request.getContext();
    requireMarkerId(markerId);
    requireWriteContext(context);
    validatePhotoMetadata(request.getContentType(), request.getSizeBytes());
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
    return createPendingUploadUrl(
        marker.getIncidentId(),
        markerId,
        request.getContentType(),
        request.getSizeBytes(),
        request.getChecksumSha256());
  }

  @Transactional
  public PhotoUploadUrlServiceResponse createUploadUrlBeforeMarkerCreation(
      MarkerCreatePhotoUploadUrlServiceRequest request) {
    if (request == null
        || request.getMarkerId() == null
        || request.getIncidentId() == null
        || request.getOpId() == null) {
      throw conflict();
    }
    validatePhotoMetadata(request.getContentType(), request.getSizeBytes());
    PhotoRequestContext context = request.getContext();
    // 생성 전 업로드는 기존 흐름처럼 본문, 앱 인증, 요청 키 순서로 검사한다.
    requireAppContext(context);
    requireWriteContext(context);
    return idempotentResponseCache.replayOrRun(
        "POST /api/markers/photos/upload-url",
        context.getIdempotencyKey(),
        request,
        () -> formatLegacyUploadBeforeMarkerCreationRequestBody(request),
        201,
        PhotoUploadUrlServiceResponse.class,
        () -> {
          MarkerWriteAccessData accessData =
              markerAccessMapper.findWriteAccessData(
                  request.getIncidentId(),
                  request.getOpId(),
                  context.getAuthentication().accountId());
          markerWriteAccessValidator.validateUploadBeforeMarkerCreationAccess(
              accessData, request.getOpId());
          return createPendingUploadUrl(
              request.getIncidentId(),
              request.getMarkerId(),
              request.getContentType(),
              request.getSizeBytes(),
              request.getChecksumSha256());
        },
        this::metadataForUpload);
  }

  private PhotoUploadUrlServiceResponse createPendingUploadUrl(
      UUID incidentId, UUID markerId, String contentType, long sizeBytes, String checksumSha256) {
    requirePhotoSlot(markerId);

    UUID photoId = UUID.randomUUID();
    Instant expiresAt = clock.instant().plus(UPLOAD_URL_TTL);
    String objectKey = objectKeyGenerator.generate(incidentId, markerId, photoId, contentType);
    String uploadUrl =
        storagePort
            .generatePresignedUrl(objectKey, contentType, sizeBytes, checksumSha256, UPLOAD_URL_TTL)
            .uploadUrl();
    MarkerPhoto photo =
        MarkerPhoto.builder()
            .id(photoId)
            .markerId(markerId)
            .objectKey(objectKey)
            .contentType(contentType)
            .sizeBytes(sizeBytes)
            .checksumSha256(checksumSha256)
            .uploadUrlExpiresAt(expiresAt)
            .build();
    photoMapper.upsert(photo);

    return PhotoUploadUrlServiceResponse.from(photo, uploadUrl, MAX_SIZE_BYTES);
  }

  @Transactional
  public PhotoAttachServiceResponse attach(PhotoAttachServiceRequest request) {
    if (request == null) {
      throw conflict();
    }
    UUID markerId = request.getMarkerId();
    UUID photoId = request.getPhotoId();
    PhotoRequestContext context = request.getContext();
    requireMarkerId(markerId);
    requirePhotoId(photoId);
    requireWriteContext(context);
    validatePhotoMetadata(request.getContentType(), request.getSizeBytes());
    return idempotentResponseCache.replayOrRun(
        "POST /api/markers/" + markerId + "/photos/" + photoId + "/attach",
        context.getIdempotencyKey(),
        request,
        () -> formatLegacyAttachRequestBody(markerId, photoId, request),
        200,
        PhotoAttachServiceResponse.class,
        () ->
            attachUploadedPhoto(
                requirePhotoAccess(markerId, context),
                request,
                context.getAuthentication().policePhoneId()),
        this::metadataForAttach);
  }

  // 마커 생성 서비스가 권한 검사와 마커 저장을 마친 뒤, 같은 트랜잭션에서 호출한다.
  public List<PhotoAttachServiceResponse> attachPhotosForMarkerCreation(
      Marker marker, List<MarkerCreatePhotoServiceRequest> photos) {
    if (photos == null || photos.isEmpty()) {
      return List.of();
    }
    validatePhotosForMarkerCreation(photos);

    List<PhotoAttachServiceResponse> responses = new ArrayList<>();
    for (MarkerCreatePhotoServiceRequest photo : photos) {
      responses.add(
          attachUploadedPhoto(
              marker,
              photo.toPhotoAttachServiceRequest(marker.getId()),
              marker.getPolicePhoneId()));
    }
    return List.copyOf(responses);
  }

  private void validatePhotosForMarkerCreation(List<MarkerCreatePhotoServiceRequest> photos) {
    if (photos.size() > MAX_PHOTOS_PER_MARKER) {
      throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
    }
    var photoIds = new HashSet<UUID>();
    for (MarkerCreatePhotoServiceRequest photo : photos) {
      if (photo == null || photo.getPhotoId() == null || !photoIds.add(photo.getPhotoId())) {
        throw conflict();
      }
      validatePhotoMetadata(photo.getContentType(), photo.getSizeBytes());
      if ((photo.getWidth() != null && photo.getWidth() <= 0)
          || (photo.getHeight() != null && photo.getHeight() <= 0)) {
        throw conflict();
      }
    }
  }

  private PhotoAttachServiceResponse attachUploadedPhoto(
      Marker marker, PhotoAttachServiceRequest request, UUID policePhoneId) {
    UUID markerId = marker.getId();
    MarkerPhoto photo = photoMapper.findById(request.getPhotoId()).orElseThrow(() -> conflict());
    requireAttachableMarker(markerId, photo);
    requireOpenUploadUrl(photo);
    ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
    requireMatchingMetadata(photo, request, objectMetadata);

    long expectedPhotoVersion = photo.getVersion();
    photo.attach(clock.instant(), request.getWidth(), request.getHeight());
    if (photoMapper.attachPendingPhoto(photo, expectedPhotoVersion) != 1) {
      throw conflict();
    }
    long expectedMarkerVersion = marker.getVersion();
    marker.markUpdated(expectedMarkerVersion);
    int updated =
        markerMapper.updateMarkerStatusVersion(
            markerId, expectedMarkerVersion, marker.getStatus(), marker.getVersion());
    if (updated != 1) {
      throw conflict();
    }
    var response =
        PhotoAttachServiceResponse.builder()
            .photoId(photo.getId())
            .status(photo.getStatus().name())
            .version(photo.getVersion())
            .markerId(markerId)
            .markerVersion(marker.getVersion())
            .build();
    publishMarkerPhotoUpdate(marker, policePhoneId, photo);
    return response;
  }

  private void validatePhotoMetadata(String contentType, long sizeBytes) {
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(ErrorCode.INVALID_PHOTO_CONTENT_TYPE);
    }
    if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
      throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
    }
  }

  private void requireMarkerId(UUID markerId) {
    if (markerId == null) {
      throw conflict();
    }
  }

  private void requirePhotoId(UUID photoId) {
    if (photoId == null) {
      throw conflict();
    }
  }

  private void requireWriteContext(PhotoRequestContext context) {
    if (context == null || context.getAuthentication() == null) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
    if (context.getIdempotencyKey() == null || context.getIdempotencyKey().isBlank()) {
      throw conflict();
    }
  }

  private Marker requirePhotoAccess(UUID markerId, PhotoRequestContext context) {
    requireAppContext(context);
    Marker marker = findActiveMarker(markerId);
    UUID accountId = context.getAuthentication().accountId();

    MarkerWriteAccessData accessData =
        markerAccessMapper.findWriteAccessData(
            marker.getIncidentId(), marker.getOperationalPeriodId(), accountId);
    markerWriteAccessValidator.validatePhotoAccess(accessData, marker.getOperationalPeriodId());
    requireAppOwnFieldMarker(marker, accountId);

    return marker;
  }

  private void requireAppContext(PhotoRequestContext context) {
    if (context == null || context.getAuthentication() == null) {
      throw denied();
    }
    if (!"APP".equals(context.getAuthentication().channel())) {
      throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
    }
  }

  private Marker findActiveMarker(UUID markerId) {
    requireMarkerId(markerId);
    Marker marker = markerMapper.findById(markerId).orElseThrow(() -> conflict());
    if (marker.isDeleted()) {
      throw conflict();
    }
    return marker;
  }

  private void requireAppOwnFieldMarker(Marker marker, UUID accountId) {
    if (!marker.isFieldMarkerCreatedBy(accountId)) {
      throw denied();
    }
  }

  private static BusinessException denied() {
    return new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
  }

  private void requirePhotoSlot(UUID markerId) {
    long count = photoMapper.countByMarkerIdAndStatusIn(markerId, PhotoStatus.countedStatuses());
    if (count >= MAX_PHOTOS_PER_MARKER) {
      throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
    }
  }

  private void requireAttachableMarker(UUID markerId, MarkerPhoto photo) {
    if (!photo.getMarkerId().equals(markerId) || !photo.isOpenForAttach()) {
      throw conflict();
    }
  }

  private void requireOpenUploadUrl(MarkerPhoto photo) {
    if (!photo.getUploadUrlExpiresAt().isAfter(clock.instant())) {
      failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict();
    }
  }

  private void requireMatchingMetadata(
      MarkerPhoto photo,
      PhotoAttachServiceRequest request,
      ObjectStoragePort.ObjectMetadata objectMetadata) {
    if (photo.getSizeBytes() != request.getSizeBytes()
        || !photo.getContentType().equals(request.getContentType())
        || !checksumMatches(
            photo.getChecksumSha256(), request.getChecksumSha256(), objectMetadata.checksumSha256())
        || !metadataMatches(photo, objectMetadata)) {
      failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict();
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
    return storagePort.headObject(photo.getObjectKey()).orElseThrow(() -> conflict());
  }

  public void publishMarkerPhotoUpdate(Marker marker, UUID policePhoneId, MarkerPhoto photo) {
    if (marker == null
        || photo == null
        || marker.getId() == null
        || marker.getIncidentId() == null
        || marker.getVersion() <= 0) {
      throw conflict();
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

  private BusinessException conflict() {
    return new BusinessException(ErrorCode.WRITE_CONFLICT);
  }

  private ResponseMetadata metadataForUpload(PhotoUploadUrlServiceResponse response) {
    return new ResponseMetadata(
        response.getPhotoId().toString(),
        PhotoStatus.PENDING_UPLOAD.name(),
        response.getVersion(),
        response.getVersion());
  }

  private ResponseMetadata metadataForAttach(PhotoAttachServiceResponse response) {
    return new ResponseMetadata(
        response.getPhotoId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getMarkerVersion());
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
      UUID markerId, UUID photoId, PhotoAttachServiceRequest request) {
    return ("attach:%s:%s:PhotoAttachRequest[sizeBytes=%s, contentType=%s, "
            + "width=%s, height=%s, checksumSha256=%s]")
        .formatted(
            markerId,
            photoId,
            request.getSizeBytes(),
            request.getContentType(),
            request.getWidth(),
            request.getHeight(),
            request.getChecksumSha256());
  }

  // ponytail: 과거 마커 생성 전 업로드 해시 비교 전용이다. 해당 처리 기록이 없어지면 제거한다.
  private String formatLegacyUploadBeforeMarkerCreationRequestBody(
      MarkerCreatePhotoUploadUrlServiceRequest request) {
    return ("marker-create-photo-upload-url:MarkerCreatePhotoUploadUrlRequest[markerId=%s, "
            + "incidentId=%s, opId=%s, contentType=%s, sizeBytes=%s, checksumSha256=%s]")
        .formatted(
            request.getMarkerId(),
            request.getIncidentId(),
            request.getOpId(),
            request.getContentType(),
            request.getSizeBytes(),
            request.getChecksumSha256());
  }
}
