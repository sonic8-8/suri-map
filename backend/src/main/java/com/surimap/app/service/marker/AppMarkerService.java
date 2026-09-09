package com.surimap.app.service.marker;

import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.service.MarkerNotificationContext;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.photo.service.MarkerCreatePhotoAttachmentService;
import com.surimap.marker.photo.service.MarkerCreatePhotoAttachmentService.AttachmentResult;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppMarkerService {

  private static final long INITIAL_VERSION = 1L;

  private final MarkerRepository markerRepository;
  private final MarkerLocationValidator markerLocationValidator;
  private final MarkerOpBindingValidator markerOpBindingValidator;
  private final MarkerWriteGuardPort markerWriteGuardPort;
  private final MarkerEventPublisher markerEventPublisher;
  private final MarkerCreatePhotoAttachmentService photoAttachmentService;
  private final MarkerNotificationService markerNotificationService;
  private final Clock clock;
  private final Supplier<UUID> markerIdSupplier;
  private final IdempotentResponseCache idempotentResponseCache;

  @Autowired
  public AppMarkerService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerCreatePhotoAttachmentService photoAttachmentService,
      MarkerNotificationService markerNotificationService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        markerRepository,
        markerLocationValidator,
        markerOpBindingValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        photoAttachmentService,
        markerNotificationService,
        Clock.systemUTC(),
        UUID::randomUUID,
        idempotentResponseCacheProvider.getIfAvailable());
  }

  public AppMarkerService(
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
        null,
        clock,
        markerIdSupplier,
        null);
  }

  public AppMarkerService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerNotificationService markerNotificationService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        markerRepository,
        markerLocationValidator,
        markerOpBindingValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        null,
        markerNotificationService,
        Clock.systemUTC(),
        UUID::randomUUID,
        idempotentResponseCacheProvider.getIfAvailable());
  }

  public AppMarkerService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerNotificationService markerNotificationService,
      Clock clock,
      Supplier<UUID> markerIdSupplier) {
    this(
        markerRepository,
        markerLocationValidator,
        markerOpBindingValidator,
        markerWriteGuardPort,
        markerEventPublisher,
        null,
        markerNotificationService,
        clock,
        markerIdSupplier,
        null);
  }

  private AppMarkerService(
      MarkerRepository markerRepository,
      MarkerLocationValidator markerLocationValidator,
      MarkerOpBindingValidator markerOpBindingValidator,
      MarkerWriteGuardPort markerWriteGuardPort,
      MarkerEventPublisher markerEventPublisher,
      MarkerCreatePhotoAttachmentService photoAttachmentService,
      MarkerNotificationService markerNotificationService,
      Clock clock,
      Supplier<UUID> markerIdSupplier,
      IdempotentResponseCache idempotentResponseCache) {
    this.markerRepository = Objects.requireNonNull(markerRepository);
    this.markerLocationValidator = Objects.requireNonNull(markerLocationValidator);
    this.markerOpBindingValidator = Objects.requireNonNull(markerOpBindingValidator);
    this.markerWriteGuardPort = Objects.requireNonNull(markerWriteGuardPort);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.photoAttachmentService = photoAttachmentService;
    this.markerNotificationService = markerNotificationService;
    this.clock = Objects.requireNonNull(clock);
    this.markerIdSupplier = Objects.requireNonNull(markerIdSupplier);
    this.idempotentResponseCache = idempotentResponseCache;
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
    markerLocationValidator.validate(request.getIncidentId(), location);

    UUID markerId = request.getId() == null ? markerIdSupplier.get() : request.getId();
    Instant serverTs = clock.instant();
    MarkerType markerType = markerType(request.getType());
    MarkerSupportRequestType supportRequestType =
        supportRequestType(request.getSupportRequestType());

    MarkerCreateRecord record =
        new MarkerCreateRecord(
            request.getIncidentId(),
            markerId,
            opId,
            dutyShiftId,
            markerType,
            supportRequestType,
            location,
            request.getMemo(),
            request.getClientTs(),
            context.authentication().accountId(),
            context.authentication().policePhoneId(),
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            INITIAL_VERSION);
    markerRepository.insertCreate(record);

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
        attachStagedPhotos(markerId, request, opId, context.authentication().policePhoneId());
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

  private AttachmentResult attachStagedPhotos(
      UUID markerId, MarkerCreateServiceRequest request, UUID opId, UUID policePhoneId) {
    if (request.getPhotos().isEmpty()) {
      return new AttachmentResult(MarkerStatus.ACTIVE.name(), INITIAL_VERSION, java.util.List.of());
    }
    if (photoAttachmentService == null) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    return photoAttachmentService.attachForCreate(
        request.getIncidentId(),
        opId,
        markerId,
        policePhoneId,
        INITIAL_VERSION,
        request.getPhotos());
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
