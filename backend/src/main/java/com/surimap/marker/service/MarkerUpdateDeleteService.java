package com.surimap.marker.service;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerDeleteRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.repository.MarkerUpdateRecord;
import com.surimap.marker.service.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;
import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerUpdateDeleteService {

  private static final int MAX_MEMO_LENGTH = 2000;

  private final MarkerRepository markerRepository;
  private final MarkerLocationValidator markerLocationValidator;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerEventPublisher markerEventPublisher;
  private final Clock clock;
  private final IdempotentResponseCache idempotentResponseCache;

  @Autowired
  public MarkerUpdateDeleteService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        markerRepository,
        markerLocationValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        Clock.systemUTC(),
        idempotentResponseCacheProvider.getIfAvailable());
  }

  public MarkerUpdateDeleteService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      Clock clock) {
    this(
        markerRepository,
        markerLocationValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        clock,
        null);
  }

  private MarkerUpdateDeleteService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      Clock clock,
      IdempotentResponseCache idempotentResponseCache) {
    this.markerRepository = Objects.requireNonNull(markerRepository);
    this.markerLocationValidator = Objects.requireNonNull(markerLocationValidator);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.clock = Objects.requireNonNull(clock);
    this.idempotentResponseCache = idempotentResponseCache;
  }

  @Transactional
  public MarkerMutationServiceResponse update(MarkerUpdateServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireWriteContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "PATCH /api/markers/" + request.getMarkerId(),
          context.idempotencyKey(),
          request,
          () -> MarkerMutationLegacyRequestBody.formatUpdate(request),
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
    MarkerRecord current = findOpenMarker(markerId);
    requireVersion(current, request.getVersion());

    MarkerType markerType = nextMarkerType(current, request.getType());
    MarkerGeoJsonPoint location = nextLocation(mutationContext.incidentId(), current, request);
    String memo = nextMemo(current, request.getMemo());
    long nextVersion = current.getVersion() + 1L;

    int updated =
        markerRepository.updateMarker(
            new MarkerUpdateRecord(
                markerId,
                current.getVersion(),
                markerType,
                location.toPoint(),
                memo,
                MarkerStatus.UPDATED,
                nextVersion));
    requireSingleRowUpdated(updated);

    MarkerPublishRequest publishRequest =
        publishRequest(
            "MARKER_UPDATED",
            mutationContext,
            current,
            MarkerStatus.UPDATED,
            nextVersion,
            markerType.name(),
            location);
    markerEventPublisher.publish(publishRequest);

    return MarkerMutationServiceResponse.builder()
        .id(markerId)
        .status(MarkerStatus.UPDATED.name())
        .version(nextVersion)
        .build();
  }

  @Transactional
  public MarkerMutationServiceResponse delete(MarkerDeleteServiceRequest request) {
    requireMarkerId(request == null ? null : request.getMarkerId());
    requireRequestVersion(request.getVersion());
    MarkerRequestContext context = request.getContext();
    requireWriteContext(context);
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "DELETE /api/markers/" + request.getMarkerId(),
          context.idempotencyKey(),
          request,
          () -> MarkerMutationLegacyRequestBody.formatDelete(request),
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
    MarkerRecord current = findOpenMarker(markerId);
    requireVersion(current, request.getVersion());
    long nextVersion = current.getVersion() + 1L;

    int updated =
        markerRepository.deleteMarker(
            new MarkerDeleteRecord(
                markerId, current.getVersion(), MarkerStatus.DELETED, nextVersion));
    requireSingleRowUpdated(updated);

    MarkerPublishRequest publishRequest =
        publishRequest(
            "MARKER_DELETED",
            mutationContext,
            current,
            MarkerStatus.DELETED,
            nextVersion,
            null,
            null);
    markerEventPublisher.publish(publishRequest);

    return MarkerMutationServiceResponse.builder()
        .id(markerId)
        .status(MarkerStatus.DELETED.name())
        .version(nextVersion)
        .build();
  }

  private MarkerRecord findOpenMarker(UUID markerId) {
    MarkerRecord marker =
        markerRepository.findById(markerId).orElseThrow(() -> conflict("write_conflict"));
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      throw conflict("write_conflict");
    }
    return marker;
  }

  private void requireMarkerId(UUID markerId) {
    if (markerId == null) {
      throw conflict("write_conflict");
    }
  }

  private void requireRequestVersion(Long version) {
    if (version == null || version <= 0) {
      throw conflict("write_conflict");
    }
  }

  private void requireVersion(MarkerRecord current, long expectedVersion) {
    if (current.getVersion() != expectedVersion) {
      throw conflict("write_conflict");
    }
  }

  private void requireWriteContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.authentication().channel())
        && !"WEB".equals(context.authentication().channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
      throw conflict("write_conflict");
    }
  }

  private MarkerType nextMarkerType(MarkerRecord current, String requestedType) {
    if (requestedType == null || requestedType.isBlank()) {
      return MarkerType.valueOf(current.getMarkerType());
    }
    try {
      return MarkerType.valueOf(requestedType);
    } catch (IllegalArgumentException exception) {
      throw conflict("write_conflict");
    }
  }

  private MarkerGeoJsonPoint nextLocation(
      UUID incidentId, MarkerRecord current, MarkerUpdateServiceRequest request) {
    if (request.getLocation() == null) {
      return MarkerGeoJsonPoint.from(current.getLocation());
    }
    MarkerGeoJsonPoint canonicalLocation = request.getLocation().canonical();
    Point location = canonicalLocation.toPoint();
    markerLocationValidator.validate(incidentId, location);
    return canonicalLocation;
  }

  private String nextMemo(MarkerRecord current, String requestedMemo) {
    if (requestedMemo == null) {
      return current.getMemo();
    }
    if (requestedMemo.length() > MAX_MEMO_LENGTH) {
      throw conflict("write_conflict");
    }
    return requestedMemo;
  }

  private void requireSingleRowUpdated(int updated) {
    if (updated != 1) {
      throw conflict("write_conflict");
    }
  }

  private void requireMutationContext(UUID markerId, MarkerMutationContext mutationContext) {
    if (mutationContext == null
        || mutationContext.incidentId() == null
        || mutationContext.opId() == null
        || !markerId.equals(mutationContext.markerId())) {
      throw conflict("write_conflict");
    }
  }

  private MarkerPublishRequest publishRequest(
      String type,
      MarkerMutationContext mutationContext,
      MarkerRecord marker,
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
            eventPolicePhoneId(marker, mutationContext),
            status.name(),
            version,
            markerType,
            location,
            null,
            serverTs()));
  }

  private UUID eventPolicePhoneId(MarkerRecord marker, MarkerMutationContext mutationContext) {
    return mutationContext.policePhoneId() == null
        ? marker.getPolicePhoneId()
        : mutationContext.policePhoneId();
  }

  private Instant serverTs() {
    return clock.instant();
  }

  private MarkerApiException conflict(String error) {
    return new MarkerApiException(error, HttpStatus.CONFLICT);
  }

  private ResponseMetadata metadataFor(MarkerMutationServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus(),
        response.getVersion(),
        response.getVersion());
  }
}
