package com.surimap.api.service.marker;

import com.surimap.api.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.api.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.api.service.marker.response.MarkerListServiceResponse;
import com.surimap.api.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerAccessMapper;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerMutationLegacyRequestBody;
import com.surimap.domain.marker.MarkerWriteAccessData;
import com.surimap.domain.marker.MarkerWriteAccessValidator;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.event.MarkerEventPayload;
import com.surimap.global.event.MarkerEventPublisher;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerService {

  private final MarkerMapper markerMapper;
  private final MarkerAccessMapper markerAccessMapper;
  private final MarkerWriteAccessValidator markerWriteAccessValidator;
  private final MarkerEventPublisher markerEventPublisher;
  private final Clock clock;
  private final IdempotentResponseCache idempotentResponseCache;

  private final MarkerQuery markerQuery;

  public MarkerService(
      MarkerQuery markerQuery,
      MarkerMapper markerMapper,
      MarkerAccessMapper markerAccessMapper,
      MarkerWriteAccessValidator markerWriteAccessValidator,
      MarkerEventPublisher markerEventPublisher,
      Clock clock,
      IdempotentResponseCache idempotentResponseCache) {
    this.markerQuery = markerQuery;
    this.markerMapper = markerMapper;
    this.markerAccessMapper = markerAccessMapper;
    this.markerWriteAccessValidator = markerWriteAccessValidator;
    this.markerEventPublisher = markerEventPublisher;
    this.clock = clock;
    this.idempotentResponseCache = idempotentResponseCache;
  }

  @Transactional
  public MarkerMutationServiceResponse update(MarkerUpdateServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireWebContext(context);
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

  private MarkerMutationServiceResponse updateMarker(MarkerUpdateServiceRequest request) {
    UUID markerId = request.getMarkerId();
    Marker current = requireMutationAccess(markerId, request.getContext());
    // 기존 오류 우선순위인 버전 → 유형 → 좌표 → 메모 순서를 유지한다.
    current.requireVersion(request.getVersion());
    Marker.validateType(request.getType());
    MarkerGeoJsonPoint location = resolveUpdateLocation(current, request);
    current.update(request.getVersion(), request.getType(), location.toPoint(), request.getMemo());
    int updated = markerMapper.updateMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerEventPayload eventPayload =
        createPublishPayload(
            request.getContext().authentication().policePhoneId(),
            current,
            MarkerStatus.UPDATED,
            current.getVersion(),
            current.getMarkerType(),
            location);
    markerEventPublisher.publish("MARKER_UPDATED", eventPayload);

    return MarkerMutationServiceResponse.from(current);
  }

  @Transactional
  public MarkerMutationServiceResponse delete(MarkerDeleteServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireWebContext(context);
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

  private MarkerMutationServiceResponse deleteMarker(MarkerDeleteServiceRequest request) {
    UUID markerId = request.getMarkerId();
    Marker current = requireMutationAccess(markerId, request.getContext());
    current.delete(request.getVersion());
    int updated = markerMapper.deleteMarker(current, request.getVersion());
    requireSingleRowUpdated(updated);

    MarkerEventPayload eventPayload =
        createPublishPayload(
            request.getContext().authentication().policePhoneId(),
            current,
            MarkerStatus.DELETED,
            current.getVersion(),
            null,
            null);
    markerEventPublisher.publish("MARKER_DELETED", eventPayload);

    return MarkerMutationServiceResponse.from(current);
  }

  private Marker requireMutationAccess(UUID markerId, MarkerRequestContext context) {
    Marker marker =
        markerMapper
            .findById(markerId)
            .filter(saved -> !saved.isDeleted())
            .orElseThrow(
                () -> new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN));
    MarkerWriteAccessData accessData =
        markerAccessMapper.findWriteAccessData(
            marker.getIncidentId(),
            marker.getOperationalPeriodId(),
            context.authentication().accountId());
    markerWriteAccessValidator.validateIncidentAccess(accessData);
    if (!marker.isReferenceMarker()) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    return marker;
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

  private void requireWebContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"WEB".equals(context.authentication().channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
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

  private MarkerEventPayload createPublishPayload(
      UUID requestingPolicePhoneId,
      Marker marker,
      MarkerStatus status,
      long version,
      String markerType,
      MarkerGeoJsonPoint location) {
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

  private ResponseMetadata metadataFor(MarkerMutationServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }

  @Transactional(readOnly = true)
  public MarkerListServiceResponse list(UUID incidentId, UUID opId, String type, String status) {
    return MarkerListServiceResponse.from(
        markerQuery.byIncident(
            incidentId, new MarkerQueryFilters(opId, parseType(type), parseStatus(status))));
  }

  private MarkerType parseType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw invalidFilter();
    }
  }

  private MarkerStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw invalidFilter();
    }
  }

  private MarkerApiException invalidFilter() {
    return new MarkerApiException("invalid_marker_filter", HttpStatus.BAD_REQUEST);
  }
}
