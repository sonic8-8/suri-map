package com.surimap.app.service.searcharea;

import com.surimap.app.service.searcharea.request.SearchAreaBoundaryAlertServiceRequest;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.maparea.boundary.SearchAreaBoundaryAlertContextRow;
import com.surimap.maparea.boundary.SearchAreaBoundaryAlertMapper;
import com.surimap.maparea.boundary.SearchAreaBoundaryAlertPersistenceRecord;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import com.surimap.sync.idempotency.WriteConflictException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSearchAreaBoundaryAlertService {

  private static final Logger log =
      LoggerFactory.getLogger(AppSearchAreaBoundaryAlertService.class);
  private static final String EVENT_TYPE = "SEARCH_AREA_BOUNDARY_EXITED";
  private static final String ALERT_TYPE_OUTSIDE = "OUTSIDE_ASSIGNED_AREA";
  private static final String ALERT_TYPE_REENTERED = "REENTERED_ASSIGNED_AREA";
  private static final String STATUS_RECORDED = "RECORDED";
  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final int COORDINATE_SCALE = 6;

  private final SearchAreaBoundaryAlertMapper mapper;
  private final OperationalPeriodQuery operationalPeriodQuery;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final EventHub eventHub;
  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Clock clock;
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  public AppSearchAreaBoundaryAlertService(
      SearchAreaBoundaryAlertMapper mapper,
      OperationalPeriodQuery operationalPeriodQuery,
      IncidentLifecycleGuard incidentLifecycleGuard,
      EventHub eventHub,
      FcmTokenQuery fcmTokenQuery,
      FcmDispatcherPort fcmDispatcher,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider,
      Clock clock) {
    this.mapper = mapper;
    this.operationalPeriodQuery = operationalPeriodQuery;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.eventHub = eventHub;
    this.fcmTokenQuery = fcmTokenQuery;
    this.fcmDispatcher = fcmDispatcher;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
    this.clock = clock;
  }

  @Transactional
  public SearchAreaBoundaryAlertResult create(SearchAreaBoundaryAlertServiceRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    String fingerprint = fingerprint(request);
    try {
      if (idempotentResponseCache != null) {
        return idempotentResponseCache.replayOrRun(
            "POST /api/search-area-boundary-alerts",
            request.idempotencyKey(),
            fingerprint,
            201,
            SearchAreaBoundaryAlertResult.class,
            () -> createNew(request),
            this::metadataFor);
      }
      return createNew(request);
    } catch (IdempotencyMismatchException exception) {
      throw new SearchAreaBoundaryAlertException("idempotency_mismatch");
    } catch (WriteConflictException exception) {
      throw new SearchAreaBoundaryAlertException("write_conflict");
    }
  }

  private SearchAreaBoundaryAlertResult createNew(SearchAreaBoundaryAlertServiceRequest request) {
    validateRequest(request);
    incidentLifecycleGuard.requireOpen(request.incidentId());
    var currentOp =
        operationalPeriodQuery
            .current(request.incidentId())
            .orElseThrow(() -> new SearchAreaBoundaryAlertException("op_required"));
    if (!currentOp.opId().equals(request.opId())) {
      throw new SearchAreaBoundaryAlertException("op_mismatch");
    }
    SearchAreaBoundaryAlertContextRow context =
        mapper
            .findAssignedTeamAreaContext(request.searchAreaId(), request.accountId())
            .orElseThrow(() -> new SearchAreaBoundaryAlertException("team_not_assigned"));
    if (!context.incidentId().equals(request.incidentId())) {
      throw new SearchAreaBoundaryAlertException("incident_access_denied");
    }
    if (!context.opId().equals(request.opId())) {
      throw new SearchAreaBoundaryAlertException("op_mismatch");
    }

    UUID alertId = UUID.randomUUID();
    UUID eventId = eventIdFor(alertId, 1L);
    Instant now = clock.instant();
    var point =
        geometryFactory.createPoint(
            new Coordinate(request.lon().doubleValue(), request.lat().doubleValue()));
    point.setSRID(4326);

    var record =
        new SearchAreaBoundaryAlertPersistenceRecord(
            alertId,
            request.incidentId(),
            request.opId(),
            request.searchAreaId(),
            request.policePhoneId(),
            request.searchPathId(),
            request.alertType(),
            STATUS_RECORDED,
            point,
            request.clientTs() == null ? now : request.clientTs(),
            now,
            1L,
            now);
    mapper.insert(record);

    SearchAreaBoundaryAlertResult result =
        new SearchAreaBoundaryAlertResult(
            alertId,
            eventId,
            request.incidentId(),
            request.opId(),
            request.searchAreaId(),
            request.policePhoneId(),
            request.alertType(),
            1L,
            STATUS_RECORDED);
    publishEvent(result);
    if (ALERT_TYPE_OUTSIDE.equals(request.alertType())) {
      dispatchFcm(result);
    }
    return result;
  }

  private void validateRequest(SearchAreaBoundaryAlertServiceRequest request) {
    if (request.incidentId() == null
        || request.opId() == null
        || request.searchAreaId() == null
        || request.policePhoneId() == null
        || request.accountId() == null) {
      throw new SearchAreaBoundaryAlertException("write_conflict");
    }
    if (!ALERT_TYPE_OUTSIDE.equals(request.alertType())
        && !ALERT_TYPE_REENTERED.equals(request.alertType())) {
      throw new SearchAreaBoundaryAlertException("write_conflict");
    }
    if (!validCoordinate(request.lon(), request.lat())) {
      throw new SearchAreaBoundaryAlertException("invalid_geometry");
    }
  }

  private boolean validCoordinate(BigDecimal lon, BigDecimal lat) {
    if (lon == null
        || lat == null
        || lon.scale() > COORDINATE_SCALE
        || lat.scale() > COORDINATE_SCALE) {
      return false;
    }
    return lon.compareTo(BigDecimal.valueOf(-180)) >= 0
        && lon.compareTo(BigDecimal.valueOf(180)) <= 0
        && lat.compareTo(BigDecimal.valueOf(-90)) >= 0
        && lat.compareTo(BigDecimal.valueOf(90)) <= 0;
  }

  private void publishEvent(SearchAreaBoundaryAlertResult result) {
    Map<String, Object> payload = payloadFor(result);
    eventHub.publish(
        new PublishRequest(
            result.eventId(),
            result.incidentId(),
            EVENT_TYPE,
            PAYLOAD_FORMAT_VERSION,
            "search_area_boundary_alert",
            result.id(),
            clock.instant(),
            payload));
  }

  private void dispatchFcm(SearchAreaBoundaryAlertResult result) {
    List<FcmTokenRow> tokens = fcmTokenQuery.activeByPolicePhone(result.policePhoneId());
    List<String> recipients =
        tokens.stream()
            .map(FcmTokenRow::tokenCiphertext)
            .map(this::decryptToken)
            .distinct()
            .toList();
    if (recipients.isEmpty()) {
      return;
    }
    try {
      fcmDispatcher.send(recipients, payloadFor(result), result.eventId().toString());
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch boundary alert FCM eventId={}", result.eventId(), exception);
    }
  }

  private Map<String, Object> payloadFor(SearchAreaBoundaryAlertResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", EVENT_TYPE);
    payload.put("eventId", result.eventId().toString());
    payload.put("incidentId", result.incidentId().toString());
    payload.put("opId", result.opId().toString());
    payload.put("searchAreaId", result.searchAreaId().toString());
    payload.put("policePhoneId", result.policePhoneId().toString());
    payload.put("status", result.status());
    payload.put("version", Long.toString(result.version()));
    return payload;
  }

  private ResponseMetadata metadataFor(SearchAreaBoundaryAlertResult result) {
    return new ResponseMetadata(
        result.id().toString(), result.status(), result.version(), result.version());
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchAreaBoundaryAlertException("write_conflict");
    }
  }

  private UUID eventIdFor(UUID alertId, long version) {
    return UUID.nameUUIDFromBytes(
        ("search-area-boundary-alert:" + alertId + ":v" + version)
            .getBytes(StandardCharsets.UTF_8));
  }

  private String decryptToken(String tokenCiphertext) {
    if (tokenCiphertext == null) {
      return "";
    }
    return tokenCiphertext.startsWith("cipher:")
        ? tokenCiphertext.substring("cipher:".length())
        : tokenCiphertext;
  }

  private String fingerprint(SearchAreaBoundaryAlertServiceRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(String.valueOf(request).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
