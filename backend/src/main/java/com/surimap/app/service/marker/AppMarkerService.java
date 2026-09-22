package com.surimap.app.service.marker;

import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerAccessMapper;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerMutationLegacyRequestBody;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerSupportRequestType;
import com.surimap.domain.marker.MarkerType;
import com.surimap.domain.marker.MarkerWriteAccessData;
import com.surimap.domain.marker.MarkerWriteAccessValidator;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.event.MarkerEventPayload;
import com.surimap.global.event.MarkerEventPublisher;
import com.surimap.global.geometry.GeoJsonPoint;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppMarkerService {

  private static final long INITIAL_VERSION = 1L;

  private final MarkerMapper markerMapper;
  private final MarkerAccessMapper markerAccessMapper;
  private final MarkerWriteAccessValidator markerWriteAccessValidator;
  private final MarkerEventPublisher markerEventPublisher;
  private final PhotoService photoService;
  private final MarkerNotificationService markerNotificationService;
  private final Clock clock = Clock.systemUTC();
  private final IdempotentResponseCache idempotentResponseCache;

  public AppMarkerService(
      MarkerMapper markerMapper,
      MarkerAccessMapper markerAccessMapper,
      MarkerWriteAccessValidator markerWriteAccessValidator,
      MarkerEventPublisher markerEventPublisher,
      PhotoService photoService,
      MarkerNotificationService markerNotificationService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.markerAccessMapper = Objects.requireNonNull(markerAccessMapper);
    this.markerWriteAccessValidator = Objects.requireNonNull(markerWriteAccessValidator);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.photoService = Objects.requireNonNull(photoService);
    this.markerNotificationService = Objects.requireNonNull(markerNotificationService);
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @Transactional
  public MarkerCreateServiceResponse create(MarkerCreateServiceRequest request) {
    validateCreateRequest(request);
    SuriMapAuthentication authentication = request.getAuthentication();
    validateAppAuthentication(authentication);
    validateIdempotencyKey(request.getIdempotencyKey());
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "POST /api/markers",
          request.getIdempotencyKey(),
          request,
          () -> MarkerCreateLegacyRequestBody.format(request),
          201,
          MarkerCreateServiceResponse.class,
          () -> createNewMarker(request),
          this::metadataFor);
    }
    return createNewMarker(request);
  }

  @Transactional
  public MarkerMutationServiceResponse update(MarkerUpdateServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    SuriMapAuthentication authentication = request.getAuthentication();
    validateAppAuthentication(authentication);
    validateIdempotencyKey(request.getIdempotencyKey());
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "PATCH /api/markers/" + request.getMarkerId(),
          request.getIdempotencyKey(),
          request,
          () ->
              MarkerMutationLegacyRequestBody.formatUpdate(
                  request.getMarkerId(),
                  request.getVersion(),
                  request.getLocation(),
                  request.getMemo(),
                  request.getType()),
          200,
          MarkerMutationServiceResponse.class,
          () -> updateMarker(request),
          this::metadataFor);
    }
    return updateMarker(request);
  }

  @Transactional
  public MarkerMutationServiceResponse delete(MarkerDeleteServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    SuriMapAuthentication authentication = request.getAuthentication();
    validateAppAuthentication(authentication);
    validateIdempotencyKey(request.getIdempotencyKey());
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "DELETE /api/markers/" + request.getMarkerId(),
          request.getIdempotencyKey(),
          request,
          () ->
              MarkerMutationLegacyRequestBody.formatDelete(
                  request.getMarkerId(), request.getVersion(), request.getReason()),
          200,
          MarkerMutationServiceResponse.class,
          () -> deleteMarker(request),
          this::metadataFor);
    }
    return deleteMarker(request);
  }

  private void validateCreateRequest(MarkerCreateServiceRequest request) {
    if (request == null
        || request.getIncidentId() == null
        || request.getOpId() == null
        || request.getType() == null
        || request.getLocation() == null
        || request.getClientTs() == null) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
    if (!request.getPhotos().isEmpty() && request.getId() == null) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private void validateAppAuthentication(SuriMapAuthentication authentication) {
    if (authentication == null) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
    if (!"APP".equals(authentication.channel())) {
      throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
    }
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private MarkerCreateServiceResponse createNewMarker(MarkerCreateServiceRequest request) {
    SuriMapAuthentication authentication = request.getAuthentication();
    MarkerWriteAccessData accessData =
        markerAccessMapper.findWriteAccessData(
            request.getIncidentId(), request.getOpId(), authentication.accountId());
    markerWriteAccessValidator.validateCreateAccess(accessData, request.getOpId());
    UUID dutyShiftId = accessData.getActiveDutyShiftId();

    UUID opId = accessData.getCurrentOpId();
    GeoJsonPoint roundedLocation = request.getLocation().roundToSixDecimals();
    Point location = roundedLocation.toPoint();
    Marker.validateLocation(location);

    UUID markerId = request.getId() == null ? UUID.randomUUID() : request.getId();
    Instant serverTs = clock.instant();
    MarkerType markerType = parseMarkerType(request.getType());
    MarkerSupportRequestType supportRequestType =
        parseSupportRequestType(request.getSupportRequestType());

    Marker marker =
        Marker.builder()
            .incidentId(request.getIncidentId())
            .id(markerId)
            .operationalPeriodId(opId)
            .dutyShiftId(dutyShiftId)
            .markerType(markerType)
            .supportRequestType(supportRequestType)
            .location(location)
            .memo(request.getMemo())
            .occurredAt(request.getClientTs())
            .createdByAccountId(authentication.accountId())
            .policePhoneId(authentication.policePhoneId())
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(INITIAL_VERSION)
            .build();
    markerMapper.insertCreate(marker);
    List<PhotoAttachServiceResponse> photos =
        photoService.attachPhotosForMarkerCreation(marker, request.getPhotos());

    MarkerEventPayload eventPayload =
        MarkerEventPayload.builder()
            .id(markerId)
            .incidentId(request.getIncidentId())
            .opId(opId)
            .policePhoneId(authentication.policePhoneId())
            .status(MarkerStatus.ACTIVE.name())
            .version(INITIAL_VERSION)
            .type(markerType.name())
            .location(roundedLocation)
            .clientTs(request.getClientTs())
            .serverTs(serverTs)
            .build();
    markerEventPublisher.publish("MARKER_CREATED", eventPayload);
    MarkerCreateServiceResponse response =
        MarkerCreateServiceResponse.builder()
            .id(markerId)
            .incidentId(request.getIncidentId())
            .opId(opId)
            .policePhoneId(authentication.policePhoneId())
            .status(marker.getStatus())
            .version(marker.getVersion())
            .photos(photos)
            .build();
    markerNotificationService.publishIfNeeded(marker);
    return response;
  }

  private ResponseMetadata metadataFor(MarkerCreateServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }

  private void requireMarkerId(UUID markerId) {
    if (markerId == null) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private void requireRequestVersion(Long version) {
    if (version == null || version <= 0) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private MarkerMutationServiceResponse updateMarker(MarkerUpdateServiceRequest request) {
    UUID markerId = request.getMarkerId();
    Marker current = requireMutationAccess(markerId, request.getAuthentication());
    // 기존 오류 우선순위인 버전 → 유형 → 좌표 → 메모 순서를 유지한다.
    current.requireVersion(request.getVersion());
    Marker.validateType(request.getType());
    GeoJsonPoint location = resolveUpdateLocation(current, request);
    current.update(request.getVersion(), request.getType(), location.toPoint(), request.getMemo());
    int updated = markerMapper.updateMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerEventPayload eventPayload =
        createPublishPayload(
            request.getAuthentication().policePhoneId(),
            current,
            MarkerStatus.UPDATED,
            current.getVersion(),
            current.getMarkerType(),
            location);
    markerEventPublisher.publish("MARKER_UPDATED", eventPayload);

    return MarkerMutationServiceResponse.from(current);
  }

  private ResponseMetadata metadataFor(MarkerMutationServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }

  private MarkerMutationServiceResponse deleteMarker(MarkerDeleteServiceRequest request) {
    UUID markerId = request.getMarkerId();
    Marker current = requireMutationAccess(markerId, request.getAuthentication());
    current.delete(request.getVersion());
    int updated = markerMapper.deleteMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerEventPayload eventPayload =
        createPublishPayload(
            request.getAuthentication().policePhoneId(),
            current,
            MarkerStatus.DELETED,
            current.getVersion(),
            null,
            null);
    markerEventPublisher.publish("MARKER_DELETED", eventPayload);

    return MarkerMutationServiceResponse.from(current);
  }

  private MarkerType parseMarkerType(String value) {
    try {
      return MarkerType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private MarkerSupportRequestType parseSupportRequestType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerSupportRequestType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private Marker requireMutationAccess(UUID markerId, SuriMapAuthentication authentication) {
    if (authentication.policePhoneId() == null) {
      throw new BusinessException(ErrorCode.POLICE_PHONE_REQUIRED);
    }
    Marker marker =
        markerMapper
            .findById(markerId)
            .filter(saved -> !saved.isDeleted())
            .orElseThrow(() -> new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED));
    UUID accountId = authentication.accountId();
    MarkerWriteAccessData accessData =
        markerAccessMapper.findWriteAccessData(
            marker.getIncidentId(), marker.getOperationalPeriodId(), accountId);
    markerWriteAccessValidator.validateIncidentAccess(accessData);
    if (!marker.isFieldMarkerCreatedBy(accountId)) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
    return marker;
  }

  private GeoJsonPoint resolveUpdateLocation(Marker current, MarkerUpdateServiceRequest request) {
    if (request.getLocation() == null) {
      return GeoJsonPoint.from(current.getLocation());
    }
    GeoJsonPoint roundedLocation = request.getLocation().roundToSixDecimals();
    Point location = roundedLocation.toPoint();
    Marker.validateLocation(location);
    return roundedLocation;
  }

  private void requireSingleRowUpdated(int updated) {
    if (updated != 1) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private MarkerEventPayload createPublishPayload(
      UUID requestingPolicePhoneId,
      Marker marker,
      MarkerStatus status,
      long version,
      String markerType,
      GeoJsonPoint location) {
    return MarkerEventPayload.builder()
        .id(marker.getId())
        .incidentId(marker.getIncidentId())
        .opId(marker.getOperationalPeriodId())
        .policePhoneId(resolveEventPolicePhoneId(marker, requestingPolicePhoneId))
        .status(status.name())
        .version(version)
        .type(markerType)
        .location(location)
        .serverTs(clock.instant())
        .build();
  }

  private UUID resolveEventPolicePhoneId(Marker marker, UUID requestingPolicePhoneId) {
    return requestingPolicePhoneId == null ? marker.getPolicePhoneId() : requestingPolicePhoneId;
  }
}
