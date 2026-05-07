package com.surimap.marker.service;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.service.MarkerNotificationContext;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerCreateService {

  private static final long INITIAL_VERSION = 1L;

  private final MarkerRepository markerRepository;
  private final MarkerLocationValidator markerLocationValidator;
  private final MarkerOpBindingValidator markerOpBindingValidator;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerEventPublisher markerEventPublisher;
  private final MarkerNotificationService markerNotificationService;
  private final Clock clock;
  private final Supplier<UUID> markerIdSupplier;

  @Autowired
  public MarkerCreateService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerNotificationService markerNotificationService) {
    this(
        markerRepository,
        markerLocationValidator,
        markerOpBindingValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        markerNotificationService,
        Clock.systemUTC(),
        UUID::randomUUID);
  }

  public MarkerCreateService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      Clock clock,
      Supplier<UUID> markerIdSupplier) {
    this(
        markerRepository,
        markerLocationValidator,
        markerOpBindingValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        null,
        clock,
        markerIdSupplier);
  }

  public MarkerCreateService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerNotificationService markerNotificationService,
      Clock clock,
      Supplier<UUID> markerIdSupplier) {
    this.markerRepository = Objects.requireNonNull(markerRepository);
    this.markerLocationValidator = Objects.requireNonNull(markerLocationValidator);
    this.markerOpBindingValidator = Objects.requireNonNull(markerOpBindingValidator);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.markerNotificationService = markerNotificationService;
    this.clock = Objects.requireNonNull(clock);
    this.markerIdSupplier = Objects.requireNonNull(markerIdSupplier);
  }

  @Transactional
  public MarkerCreateResult create(MarkerCreateRequest request, MarkerRequestContext context) {
    requireRequest(request);
    requireAppContext(context);
    markerWriteGuardPort.requireCreateAccess(request.incidentId(), request.opId(), context);

    UUID opId = validateOpBinding(request.incidentId(), request.opId());
    MarkerGeoJsonPoint canonicalLocation = request.location().canonical();
    Point location = canonicalLocation.toPoint();
    markerLocationValidator.validate(request.incidentId(), location);

    UUID markerId = markerIdSupplier.get();
    Instant serverTs = clock.instant();
    MarkerType markerType = markerType(request.type());
    MarkerSupportRequestType supportRequestType = supportRequestType(request.supportRequestType());

    MarkerCreateRecord record =
        new MarkerCreateRecord(
            request.incidentId(),
            markerId,
            opId,
            null,
            markerType,
            supportRequestType,
            location,
            request.memo(),
            request.clientTs(),
            context.authentication().accountId(),
            context.authentication().policePhoneId(),
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            INITIAL_VERSION);
    markerRepository.insertCreate(record);

    MarkerCreateResponse response =
        new MarkerCreateResponse(
            markerId,
            request.incidentId(),
            opId,
            context.authentication().policePhoneId(),
            MarkerStatus.ACTIVE.name(),
            INITIAL_VERSION);
    MarkerPublishRequest publishRequest =
        new MarkerPublishRequest(
            "MARKER_CREATED",
            new MarkerPublishRequestPayload(
                markerId,
                request.incidentId(),
                opId,
                context.authentication().policePhoneId(),
                MarkerStatus.ACTIVE.name(),
                INITIAL_VERSION,
                markerType.name(),
                canonicalLocation,
                request.clientTs(),
                serverTs));
    markerEventPublisher.publish(publishRequest);
    publishNotificationIfNeeded(
        markerId,
        request.incidentId(),
        opId,
        context,
        markerType,
        supportRequestType,
        canonicalLocation);
    return new MarkerCreateResult(response, publishRequest);
  }

  private void publishNotificationIfNeeded(
      UUID markerId,
      UUID incidentId,
      UUID opId,
      MarkerRequestContext context,
      MarkerType markerType,
      MarkerSupportRequestType supportRequestType,
      MarkerGeoJsonPoint location) {
    if (markerNotificationService == null) {
      return;
    }
    markerNotificationService.publishIfNeeded(
        new MarkerNotificationContext(
            markerId,
            incidentId,
            opId,
            context.authentication().policePhoneId(),
            markerType,
            supportRequestType,
            location,
            INITIAL_VERSION));
  }

  private void requireRequest(MarkerCreateRequest request) {
    if (request == null
        || request.incidentId() == null
        || request.opId() == null
        || request.type() == null
        || request.location() == null
        || request.clientTs() == null) {
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
}
