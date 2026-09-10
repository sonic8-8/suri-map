package com.surimap.app.service.marker;

import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.app.service.photo.MarkerCreatePhotoAttachmentService;
import com.surimap.app.service.photo.MarkerCreatePhotoAttachmentService.AttachmentResult;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerMutationLegacyRequestBody;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.service.MarkerNotificationContext;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppMarkerService {

  private static final long INITIAL_VERSION = 1L;

  private final MarkerMapper markerMapper;
  private final MarkerOpBindingValidator markerOpBindingValidator;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerEventPublisher markerEventPublisher;
  private final MarkerCreatePhotoAttachmentService photoAttachmentService;
  private final MarkerNotificationService markerNotificationService;
  private final Clock clock = Clock.systemUTC();
  private final IdempotentResponseCache idempotentResponseCache;

  public AppMarkerService(
      MarkerMapper markerMapper,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerCreatePhotoAttachmentService photoAttachmentService,
      MarkerNotificationService markerNotificationService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.markerOpBindingValidator = Objects.requireNonNull(markerOpBindingValidator);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.photoAttachmentService = Objects.requireNonNull(photoAttachmentService);
    this.markerNotificationService = Objects.requireNonNull(markerNotificationService);
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @Transactional
  public MarkerCreateServiceResponse create(MarkerCreateServiceRequest request) {
    requireRequest(request);
    MarkerRequestContext context = request.getContext();
    requireAppContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "POST /api/markers",
          context.idempotencyKey(),
          request,
          () -> MarkerCreateLegacyRequestBody.format(request),
          201,
          MarkerCreateServiceResponse.class,
          () -> createNewMarker(request),
          this::metadataFor);
    }
    return createNewMarker(request);
  }

  private MarkerCreateServiceResponse createNewMarker(MarkerCreateServiceRequest request) {
    MarkerRequestContext context = request.getContext();
    UUID dutyShiftId =
        markerWriteGuardPort.requireCreateAccess(
            request.getIncidentId(), request.getOpId(), context);

    UUID opId = validateOpBinding(request.getIncidentId(), request.getOpId());
    MarkerGeoJsonPoint canonicalLocation = request.getLocation().canonical();
    Point location = canonicalLocation.toPoint();
    Marker.validateLocation(location);

    UUID markerId = request.getId() == null ? UUID.randomUUID() : request.getId();
    Instant serverTs = clock.instant();
    MarkerType markerType = markerType(request.getType());
    MarkerSupportRequestType supportRequestType =
        supportRequestType(request.getSupportRequestType());

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
            .createdByAccountId(context.authentication().accountId())
            .policePhoneId(context.authentication().policePhoneId())
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(INITIAL_VERSION)
            .build();
    markerMapper.insertCreate(marker);

    MarkerPublishRequest publishRequest =
        new MarkerPublishRequest(
            "MARKER_CREATED",
            new MarkerPublishRequestPayload(
                markerId,
                request.getIncidentId(),
                opId,
                context.authentication().policePhoneId(),
                MarkerStatus.ACTIVE.name(),
                INITIAL_VERSION,
                markerType.name(),
                canonicalLocation,
                request.getClientTs(),
                serverTs));
    markerEventPublisher.publish(publishRequest);
    AttachmentResult attachmentResult =
        photoAttachmentService.attachForCreate(marker, request.getPhotos());
    MarkerCreateServiceResponse response =
        MarkerCreateServiceResponse.builder()
            .id(markerId)
            .incidentId(request.getIncidentId())
            .opId(opId)
            .policePhoneId(context.authentication().policePhoneId())
            .status(attachmentResult.markerStatus())
            .version(attachmentResult.markerVersion())
            .photos(attachmentResult.photos())
            .build();
    publishNotificationIfNeeded(
        markerId,
        request.getIncidentId(),
        opId,
        context,
        markerType,
        supportRequestType,
        canonicalLocation,
        request.getClientTs());
    return response;
  }

  private ResponseMetadata metadataFor(MarkerCreateServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }

  private void publishNotificationIfNeeded(
      UUID markerId,
      UUID incidentId,
      UUID opId,
      MarkerRequestContext context,
      MarkerType markerType,
      MarkerSupportRequestType supportRequestType,
      MarkerGeoJsonPoint location,
      Instant clientTs) {
    markerNotificationService.publishIfNeeded(
        new MarkerNotificationContext(
            markerId,
            incidentId,
            opId,
            context.authentication().policePhoneId(),
            markerType,
            supportRequestType,
            location,
            INITIAL_VERSION,
            clientTs));
  }

  private void requireRequest(MarkerCreateServiceRequest request) {
    if (request == null
        || request.getIncidentId() == null
        || request.getOpId() == null
        || request.getType() == null
        || request.getLocation() == null
        || request.getClientTs() == null) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    if (!request.getPhotos().isEmpty() && request.getId() == null) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
  }

  private void requireAppContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.authentication().channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
  }

  private UUID validateOpBinding(UUID incidentId, UUID requestedOpId) {
    try {
      return markerOpBindingValidator.validate(incidentId, requestedOpId);
    } catch (OpRequiredException exception) {
      throw new MarkerApiException(exception.errorCode(), HttpStatus.CONFLICT);
    } catch (OpMismatchException exception) {
      throw new MarkerApiException(exception.errorCode(), HttpStatus.CONFLICT);
    }
  }

  private MarkerType markerType(String value) {
    try {
      return MarkerType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
  }

  private MarkerSupportRequestType supportRequestType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerSupportRequestType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
  }

  @Transactional
  public MarkerMutationServiceResponse update(MarkerUpdateServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireAppContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "PATCH /api/markers/" + request.getMarkerId(),
          context.idempotencyKey(),
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

  private MarkerMutationServiceResponse updateMarker(MarkerUpdateServiceRequest request) {
    UUID markerId = request.getMarkerId();
    MarkerMutationContext mutationContext =
        markerWriteGuardPort.requireUpdateAccess(markerId, request.getContext());
    requireMutationContext(markerId, mutationContext);
    Marker current = findMarker(markerId);
    // 기존 오류 우선순위인 버전 → 유형 → 좌표 → 메모 순서를 유지한다.
    current.requireVersion(request.getVersion());
    Marker.validateType(request.getType());
    MarkerGeoJsonPoint location = resolveUpdateLocation(current, request);
    current.update(request.getVersion(), request.getType(), location.toPoint(), request.getMemo());
    int updated = markerMapper.updateMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerPublishRequest publishRequest =
        createPublishRequest(
            "MARKER_UPDATED",
            mutationContext,
            current,
            MarkerStatus.UPDATED,
            current.getVersion(),
            current.getMarkerType(),
            location);
    markerEventPublisher.publish(publishRequest);

    return MarkerMutationServiceResponse.from(current);
  }

  @Transactional
  public MarkerMutationServiceResponse delete(MarkerDeleteServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireAppContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "DELETE /api/markers/" + request.getMarkerId(),
          context.idempotencyKey(),
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

  private MarkerMutationServiceResponse deleteMarker(MarkerDeleteServiceRequest request) {
    UUID markerId = request.getMarkerId();
    MarkerMutationContext mutationContext =
        markerWriteGuardPort.requireDeleteAccess(markerId, request.getContext());
    requireMutationContext(markerId, mutationContext);
    Marker current = findMarker(markerId);
    current.delete(request.getVersion());
    int updated = markerMapper.deleteMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerPublishRequest publishRequest =
        createPublishRequest(
            "MARKER_DELETED",
            mutationContext,
            current,
            MarkerStatus.DELETED,
            current.getVersion(),
            null,
            null);
    markerEventPublisher.publish(publishRequest);

    return MarkerMutationServiceResponse.from(current);
  }

  private Marker findMarker(UUID markerId) {
    return markerMapper
        .findById(markerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WRITE_CONFLICT));
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

  private MarkerGeoJsonPoint resolveUpdateLocation(
      Marker current, MarkerUpdateServiceRequest request) {
    if (request.getLocation() == null) {
      return MarkerGeoJsonPoint.from(current.getLocation());
    }
    MarkerGeoJsonPoint canonicalLocation = request.getLocation().canonical();
    Point location = canonicalLocation.toPoint();
    Marker.validateLocation(location);
    return canonicalLocation;
  }

  private void requireSingleRowUpdated(int updated) {
    if (updated != 1) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private void requireMutationContext(UUID markerId, MarkerMutationContext mutationContext) {
    if (mutationContext == null
        || mutationContext.incidentId() == null
        || mutationContext.opId() == null
        || !markerId.equals(mutationContext.markerId())) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  private MarkerPublishRequest createPublishRequest(
      String type,
      MarkerMutationContext mutationContext,
      Marker marker,
      MarkerStatus status,
      long version,
      String markerType,
      MarkerGeoJsonPoint location) {
    return new MarkerPublishRequest(
        type,
        new MarkerPublishRequestPayload(
            marker.getId(),
            mutationContext.incidentId(),
            marker.getOperationalPeriodId(),
            resolveEventPolicePhoneId(marker, mutationContext),
            status.name(),
            version,
            markerType,
            location,
            null,
            clock.instant()));
  }

  private UUID resolveEventPolicePhoneId(Marker marker, MarkerMutationContext mutationContext) {
    return mutationContext.policePhoneId() == null
        ? marker.getPolicePhoneId()
        : mutationContext.policePhoneId();
  }

  private ResponseMetadata metadataFor(MarkerMutationServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }
}
