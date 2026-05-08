package com.surimap.offlinepackage.service;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OfflinePackageService implements OfflinePackageInstallationQuery {

  static final String EVENT_TYPE = "OFFLINE_PACKAGE_INSTALLATION_CHANGED";
  static final String SOURCE_ENTITY_TYPE = "offline_package_installation";

  private final OfflinePackageRepository repository;
  private final EventHub eventHub;
  private final Map<String, IdempotencySnapshot> idempotencySnapshots = new ConcurrentHashMap<>();

  public OfflinePackageService(
      OfflinePackageRepository repository, ObjectProvider<EventHub> eventHub) {
    this.repository = repository;
    this.eventHub = eventHub.getIfAvailable(() -> request -> {});
  }

  public OfflinePackageManifestResponse manifest(String incidentId, String policePhoneId) {
    return repository.manifest(incidentId, policePhoneId);
  }

  public OfflinePackageInstallationResponse reportInstallation(
      String incidentId, String idempotencyKey, OfflinePackageInstallationReportRequest request) {
    IdempotencySnapshot existing = idempotencySnapshots.get(idempotencyKey);
    String requestHash = requestHash(incidentId, request);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new OfflinePackageApiException("idempotency_mismatch", HttpStatus.CONFLICT);
      }
      return existing.response();
    }

    OfflinePackageInstallationStatus status = repository.saveStatus(incidentId, request);
    eventHub.publish(publishRequest(status));
    OfflinePackageInstallationResponse response =
        new OfflinePackageInstallationResponse(
            status.id(),
            status.status(),
            status.version(),
            status.manifestVersion(),
            status.readyForOfflineUse(),
            OfflinePackageRepository.SERVER_TS);
    idempotencySnapshots.put(idempotencyKey, new IdempotencySnapshot(requestHash, response));
    return response;
  }

  @Override
  public List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
    return repository.byIncident(incidentId);
  }

  private static PublishRequest publishRequest(OfflinePackageInstallationStatus status) {
    return new PublishRequest(
        stableUuid("event:" + EVENT_TYPE + ":" + status.id()),
        stableUuid("incident:" + status.incidentId()),
        EVENT_TYPE,
        1,
        SOURCE_ENTITY_TYPE,
        stableUuid(SOURCE_ENTITY_TYPE + ":" + status.id()),
        OfflinePackageRepository.SERVER_TS.toInstant(),
        payload(status));
  }

  private static Map<String, Object> payload(OfflinePackageInstallationStatus status) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", status.id());
    payload.put("status", status.status());
    payload.put("version", Math.toIntExact(status.version()));
    payload.put("incidentId", status.incidentId());
    payload.put("policePhoneId", status.policePhoneId());
    payload.put("manifestVersion", status.manifestVersion());
    payload.put("sequence", Math.toIntExact(status.sequence()));
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

  private record IdempotencySnapshot(String requestHash, OfflinePackageInstallationResponse response) {}
}
