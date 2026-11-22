package com.surimap.offlinepackage.service;

import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.auth.PolicePhoneIdFormat;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.event.EventPublishRequest;
import com.surimap.incident.service.IncidentAccessService;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflinePackageService {

  private final OfflinePackageRepository repository;
  private final IdempotentResponseCache idempotentResponseCache;
  private final IncidentAccessService incidentAccessService;

  public OfflinePackageService(
      OfflinePackageRepository repository,
      IdempotentResponseCache idempotentResponseCache,
      IncidentAccessService incidentAccessService) {
    this.repository = repository;
    this.idempotentResponseCache = idempotentResponseCache;
    this.incidentAccessService = incidentAccessService;
  }

  // 사건 권한이 없는 계정이 응답 차이로 패키지 준비나 파기 상태를 알 수 없도록,
  // 사건 권한 확인과 업무폰 확인을 준비 전(409)과 파기(410) 확인보다 앞에 두었다.
  @Transactional
  public OfflinePackageManifestResponse manifestWithIncidentAccess(
      String incidentId, String policePhoneId, SuriMapAuthentication authentication) {
    incidentAccessService.requireIncidentAccess(parseIncidentId(incidentId), authentication);
    // 업무폰 ID가 없으면 지금처럼 고정 업무폰으로 조립된다. 이 분기는 #56에서 정리한다.
    if (policePhoneId != null) {
      requireUuidPolicePhoneId(policePhoneId);
    }
    return manifest(incidentId, policePhoneId);
  }

  @Transactional
  public OfflinePackageManifestResponse manifest(String incidentId, String policePhoneId) {
    return repository.manifest(incidentId, policePhoneId);
  }

  // 사건 권한이 없는 계정이 같은 Idempotency-Key의 저장된 응답을 받지 못하도록,
  // 작업자는 사건 권한 확인과 업무폰 ID 형식 확인을 저장된 응답을 꺼내기 전에 두었다. 두 확인은 manifest 불일치(409)와 파기(409) 확인보다도 앞이다.
  public OfflinePackageInstallationResponse reportInstallationWithIncidentAccess(
      String incidentId,
      String idempotencyKey,
      OfflinePackageInstallationReportRequest request,
      SuriMapAuthentication authentication) {
    boolean open =
        incidentAccessService.requireIncidentAccess(parseIncidentId(incidentId), authentication);
    requireUuidPolicePhoneId(request.policePhoneId());
    return reportInstallation(incidentId, idempotencyKey, request, open);
  }

  // 인수인계 메모처럼 종료 확인을 저장된 응답을 꺼낸 뒤에 두었다. 종료 전에 저장된 같은 Idempotency-Key의 응답은 종료 뒤에도 그대로 돌아온다.
  // 종료된 사건의 새 보고는 패키지가 파기되기 전이어도 incident_closed로 거절된다.
  public OfflinePackageInstallationResponse reportInstallation(
      String incidentId,
      String idempotencyKey,
      OfflinePackageInstallationReportRequest request,
      boolean incidentOpen) {
    String requestHash = requestHash(incidentId, request);
    return idempotentResponseCache.replayOrRun(
        "POST /api/incidents/" + incidentId + "/offline-package/installations",
        idempotencyKey,
        requestHash,
        200,
        OfflinePackageInstallationResponse.class,
        () -> {
          if (!incidentOpen) {
            throw new BusinessException(ErrorCode.INCIDENT_CLOSED);
          }
          OffsetDateTime serverTs = serverNow();
          OfflinePackageInstallationStatus status =
              repository.saveStatus(incidentId, request, serverTs);
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
    repository.staleReadyAndPartialForOverallAreaChange(
        incidentId, overallSearchAreaId, overallSearchAreaVersion, sourceHash);
  }

  // 사건 ID는 UUID다. UUID가 아닌 값에는 사건 권한이 있을 수 없으므로 incident_access_denied로 거절된다.
  private static UUID parseIncidentId(String incidentId) {
    try {
      return UUID.fromString(incidentId);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
  }

  // 업무폰 인증은 MDM 같은 인프라 계층이 맡는다(ADR-0037). 작업자는 다른 APP 쓰기 API와 같이 UUID 형식만 확인하게 했다.
  private static void requireUuidPolicePhoneId(String policePhoneId) {
    PolicePhoneIdFormat.parse(policePhoneId)
        .orElseThrow(() -> new BusinessException(ErrorCode.POLICE_PHONE_REQUIRED));
  }

  private static OffsetDateTime serverNow() {
    return OffsetDateTime.now(ZoneOffset.UTC);
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
