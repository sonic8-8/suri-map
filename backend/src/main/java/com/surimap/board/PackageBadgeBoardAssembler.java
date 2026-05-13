package com.surimap.board;

import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PackageBadgeBoardAssembler {

  private static final String SLOT = "package_badge";
  private static final String SOURCE_SPEC = "S7";
  private static final String LATEST_EVENT_ID = "evt-s7-package-status-001";
  private static final String SOURCE_HASH = "hash-s7-package-current";
  private static final String PACKAGE_MISSING = "PACKAGE_MISSING";
  private static final String PURGED = "PURGED";
  private static final String RAISE_REASON =
      "S7 package status is MISSING, STALE, EXPIRED, or any required item failed";
  private static final String CLEAR_REASON =
      "S7 package status is COMPLETE for the active manifestVersion";

  private final OfflinePackageInstallationQuery installationQuery;

  public PackageBadgeBoardAssembler(OfflinePackageInstallationQuery installationQuery) {
    this.installationQuery =
        Objects.requireNonNull(installationQuery, "installationQuery must not be null");
  }

  public List<BoardSourceRow> sourceRowsByIncident(String incidentId) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    return installationQuery.byIncident(incidentId).stream().map(this::sourceRow).toList();
  }

  private BoardSourceRow sourceRow(OfflinePackageInstallationStatus status) {
    return new BoardSourceRow(
        SLOT,
        SOURCE_SPEC,
        status.id(),
        "board-package-" + status.id(),
        status.status(),
        status.version(),
        status.sequence(),
        LATEST_EVENT_ID,
        SOURCE_HASH,
        payload(status));
  }

  private static Map<String, Object> payload(OfflinePackageInstallationStatus status) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", status.incidentId());
    payload.put("policePhoneId", status.policePhoneId());
    payload.put("policePhoneCode", status.policePhoneCode());
    payload.put("policePhoneName", status.policePhoneName());
    payload.put("packageStatus", status.status());
    if (PURGED.equals(status.status())) {
      return payload;
    }
    payload.put("manifestVersion", status.manifestVersion());
    payload.put("readyForOfflineUse", status.readyForOfflineUse());
    payload.put("localWarningInput", localWarningInput(status));
    return payload;
  }

  private static Map<String, Object> localWarningInput(OfflinePackageInstallationStatus status) {
    boolean raised = shouldRaisePackageMissing(status);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("warningType", PACKAGE_MISSING);
    input.put("packageStatus", status.status());
    input.put("manifestVersion", status.manifestVersion());
    input.put("activeManifestVersion", status.activeManifestVersion());
    input.put("raised", raised);
    input.put("reason", raised ? RAISE_REASON : CLEAR_REASON);
    return input;
  }

  private static boolean shouldRaisePackageMissing(OfflinePackageInstallationStatus status) {
    return !("READY".equals(status.status())
        && status.readyForOfflineUse()
        && status.manifestVersion() == status.activeManifestVersion());
  }
}
