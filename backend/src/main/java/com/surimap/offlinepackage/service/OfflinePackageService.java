package com.surimap.offlinepackage.service;

import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.event.EventPublisher;
import com.surimap.incident.service.IncidentAccessService;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.policephone.PolicePhonePersistenceService;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflinePackageService implements OfflinePackageInstallationQuery {

  static final String EVENT_TYPE = "OFFLINE_PACKAGE_INSTALLATION_CHANGED";
  static final String SOURCE_ENTITY_TYPE = "offline_package_installation";

  private final OfflinePackageRepository repository;
  private final EventPublisher eventHub;
  private final IdempotentResponseCache idempotentResponseCache;
  private final IncidentAccessService incidentAccessService;
  private final PolicePhonePersistenceService policePhoneService;

  public OfflinePackageService(
      OfflinePackageRepository repository,
      ObjectProvider<EventPublisher> eventHub,
      IdempotentResponseCache idempotentResponseCache,
      IncidentAccessService incidentAccessService,
      PolicePhonePersistenceService policePhoneService) {
    this.repository = repository;
    this.eventHub = eventHub.getIfAvailable(() -> request -> {});
    this.idempotentResponseCache = idempotentResponseCache;
    this.incidentAccessService = incidentAccessService;
    this.policePhoneService = policePhoneService;
  }

  // 사건 권한이 없는 계정이 응답 차이로 패키지 준비나 파기 상태를 알 수 없도록,
  // 사건 권한 확인과 업무폰 확인을 준비 전(409)과 파기(410) 확인보다 앞에 두었다.
  @Transactional
  public OfflinePackageManifestResponse manifestWithIncidentAccess(
      String incidentId, String policePhoneId, SuriMapAuthentication authentication) {
    incidentAccessService.requireIncidentAccess(parseIncidentId(incidentId), authentication);
    // 업무폰 ID가 없으면 지금처럼 고정 업무폰으로 조립된다. 웹 오프라인 패키지 상태 화면의 요청이 이 경우다.
    if (policePhoneId != null) {
      policePhoneService.requireAccountPolicePhone(policePhoneId, authentication.getAccountId());
    }
    return manifest(incidentId, policePhoneId);
  }

  @Transactional
  public OfflinePackageManifestResponse manifest(String incidentId, String policePhoneId) {
    return repository.manifest(incidentId, policePhoneId);
  }

  public OfflinePackageInstallationResponse reportInstallation(
      String incidentId, String idempotencyKey, OfflinePackageInstallationReportRequest request) {
    String requestHash = requestHash(incidentId, request);
    return idempotentResponseCache.replayOrRun(
        "POST /api/incidents/" + incidentId + "/offline-package/installations",
        idempotencyKey,
        requestHash,
        200,
        OfflinePackageInstallationResponse.class,
        () -> {
          OffsetDateTime serverTs = serverNow();
          OfflinePackageInstallationStatus status =
              repository.saveStatus(incidentId, request, serverTs);
          eventHub.publish(publishRequest(status, serverTs.toInstant()));
          return new OfflinePackageInstallationResponse(
              status.id(),
              status.status(),
              status.version(),
              status.manifestVersion(),
              status.readyForOfflineUse(),
              serverTs);
        },
        response ->
            new ResponseMetadata(
                response.id(), response.status(), response.version(), response.version()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
    return repository.byIncident(incidentId);
  }

  @Transactional
  public void consumeSearchAreaChanged(EventPublishRequest event) {
    if (!"SEARCH_AREA_CHANGED".equals(event.getType())) {
      return;
    }
    Map<String, Object> payload = event.getPayload();
    if (payload.containsKey("opId")) {
      return;
    }
    if (!payload.containsKey("id")
        || !payload.containsKey("incidentId")
        || !payload.containsKey("version")) {
      return;
    }

    String incidentId = String.valueOf(payload.get("incidentId"));
    String overallSearchAreaId = String.valueOf(payload.get("id"));
    long overallSearchAreaVersion = Long.parseLong(String.valueOf(payload.get("version")));
    String sourceHash =
        String.valueOf(
            payload.getOrDefault("overallAreaHash", payload.getOrDefault("geometry", "")));
    repository
        .staleReadyAndPartialForOverallAreaChange(
            incidentId, overallSearchAreaId, overallSearchAreaVersion, sourceHash)
        .forEach(status -> eventHub.publish(publishRequest(status, serverNow().toInstant())));
  }

  // 사건 ID는 UUID다. UUID가 아닌 값에는 사건 권한이 있을 수 없으므로 incident_access_denied로 거절된다.
  private static UUID parseIncidentId(String incidentId) {
    try {
      return UUID.fromString(incidentId);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
  }

  private static EventPublishRequest publishRequest(
      OfflinePackageInstallationStatus status, java.time.Instant occurredAt) {
    return EventPublishRequest.builder()
        .eventId(stableUuid("event:" + EVENT_TYPE + ":" + status.id() + ":" + status.version()))
        .incidentId(stableUuid("incident:" + status.incidentId()))
        .type(EVENT_TYPE)
        .payloadFormatVersion(1)
        .sourceEntityType(SOURCE_ENTITY_TYPE)
        .sourceEntityId(stableUuid(SOURCE_ENTITY_TYPE + ":" + status.id()))
        .occurredAt(occurredAt)
        .payload(payload(status))
        .build();
  }

  private static OffsetDateTime serverNow() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private static Map<String, Object> payload(OfflinePackageInstallationStatus status) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", status.id());
    payload.put("status", status.status());
    payload.put("version", status.version());
    payload.put("incidentId", status.incidentId());
    payload.put("policePhoneId", status.policePhoneId());
    payload.put("manifestVersion", status.manifestVersion());
    payload.put("sequence", status.sequence());
    return payload;
  }

  private static UUID stableUuid(String source) {
    return UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String requestHash(
      String incidentId, OfflinePackageInstallationReportRequest request) {
    String canonicalBody =
        incidentId
            + "|"
            + request.policePhoneId()
            + "|"
            + request.manifestId()
            + "|"
            + request.manifestVersion()
            + "|"
            + request.status()
            + "|"
            + request.totalItems()
            + "|"
            + request.completedItems()
            + "|"
            + request.failedItems()
            + "|"
            + request.version()
            + "|"
            + request.resolvedReadyForOfflineUse()
            + "|"
            + request.resolvedFailedItemKeys();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalBody.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }
}
