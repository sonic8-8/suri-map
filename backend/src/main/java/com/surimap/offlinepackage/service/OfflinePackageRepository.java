package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.AssignedArea;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.IncidentMetadata;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.InitialMarker;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.MissingPerson;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.OperationalPeriod;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.OverallSearchArea;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.PackageItem;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.PolicePhoneContext;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.TileItem;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.offlinepackage.repository.OfflinePackageInstallationRecord;
import com.surimap.offlinepackage.repository.OfflinePackageMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class OfflinePackageRepository {

  public static final String INCIDENT_ID = "inc-precinct-first-001";
  public static final String MANIFEST_ID = "tile-manifest-inc-precinct-001";
  public static final int MANIFEST_VERSION = 1;
  public static final String POLICE_PHONE_ID = "dev-precinct-phone-01";
  public static final String INSTALLATION_ID = "pkg-status-precinct-001";
  public static final int INSTALLATION_VERSION = 3;
  public static final int SEQUENCE = 901;
  public static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T09:00:41+09:00");
  private static final OffsetDateTime EXPIRES_AT =
      OffsetDateTime.parse("2026-04-28T12:00:00+09:00");

  private final OfflinePackageMapper mapper;

  public OfflinePackageRepository(OfflinePackageMapper mapper) {
    this.mapper = mapper;
  }

  public synchronized OfflinePackageManifestResponse manifest(
      String incidentId, String policePhoneId) {
    ensureFixtureManifest();
    PackageItemState itemState = itemStateFor(policePhoneId);
    return new OfflinePackageManifestResponse(
        MANIFEST_ID,
        incidentId,
        MANIFEST_VERSION,
        EXPIRES_AT,
        "sha256:1111111111111111111111111111111111111111111111111111111111111111",
        new PolicePhoneContext(
            POLICE_PHONE_ID, "acct-precinct-team", "TEAM", "team-precinct-jongno", "MEMBER"),
        new IncidentMetadata(incidentId, "OPEN", "CURRENT", "mock-112-incident-001"),
        new MissingPerson(
            incidentId,
            "가상 실종자 001",
            null,
            "남색 점퍼, 회색 등산화",
            "인왕산 북측 산책로 입구",
            OffsetDateTime.parse("2026-04-28T08:30:00+09:00")),
        List.of(new OperationalPeriod("op-precinct-001-op1", incidentId, 1, "ACTIVE", 1L)),
        List.of(
            new AssignedArea(
                "area-precinct-a1", incidentId, "op-precinct-001-op1", "ASSIGNED", 1L)),
        List.of(
            new InitialMarker(
                "mk-precinct-clue-001",
                incidentId,
                "op-precinct-001-op1",
                point("126.956500", "37.571200"),
                "ACTIVE")),
        new OverallSearchArea(
            "osa-precinct-001",
            incidentId,
            "OVERALL",
            "ACTIVE",
            "overall-area-hash-precinct-current",
            overallSearchAreaPolygon()),
        tileItems(),
        packageItems(itemState));
  }

  public synchronized OfflinePackageInstallationStatus saveStatus(
      String incidentId, OfflinePackageInstallationReportRequest request) {
    ensureFixtureManifest();
    OfflinePackageInstallationRecord record =
        OfflinePackageInstallationRecord.from(INSTALLATION_ID, incidentId, request, SERVER_TS);
    mapper.deleteInstallationForPhone(request.manifestId(), request.policePhoneId());
    mapper.insertInstallation(record);
    return new OfflinePackageInstallationStatus(
        record.id(),
        record.incidentId(),
        record.policePhoneId(),
        record.status(),
        record.version(),
        record.sequence(),
        request.manifestVersion(),
        record.readyForOfflineUse());
  }

  public synchronized List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
    ensureFixtureManifest();
    return mapper.findStatusesByIncident(incidentId);
  }

  private void ensureFixtureManifest() {
    if (mapper.countManifest(MANIFEST_ID) > 0) {
      return;
    }
    mapper.insertManifest(MANIFEST_ID, INCIDENT_ID, MANIFEST_VERSION, EXPIRES_AT, SERVER_TS);
    seedStatus("pkg-status-precinct-ready-001", POLICE_PHONE_ID, "READY", 3, true);
    seedStatus("pkg-status-precinct-partial-001", "dev-precinct-phone-02", "PARTIAL", 2, false);
    seedStatus("pkg-status-precinct-stale-001", "dev-precinct-phone-03", "STALE", 4, false);
    seedStatus("pkg-status-precinct-failed-001", "dev-precinct-phone-04", "FAILED", 5, false);
  }

  private void seedStatus(
      String id, String policePhoneId, String status, long version, boolean readyForOfflineUse) {
    mapper.insertInstallation(
        new OfflinePackageInstallationRecord(
            id,
            MANIFEST_ID,
            INCIDENT_ID,
            policePhoneId,
            null,
            status,
            7,
            readyForOfflineUse ? 7 : 5,
            readyForOfflineUse ? 0 : 2,
            List.of(),
            null,
            SERVER_TS,
            version,
            SEQUENCE,
            readyForOfflineUse,
            SERVER_TS,
            SERVER_TS));
  }

  private PackageItemState itemStateFor(String policePhoneId) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      return PackageItemState.pending();
    }
    OfflinePackageInstallationRecord record =
        mapper.findInstallationForPhone(MANIFEST_ID, policePhoneId);
    if (record == null) {
      return PackageItemState.pending();
    }
    if ("READY".equals(record.status())) {
      return PackageItemState.downloaded();
    }
    if (record.failedItemKeys().isEmpty()) {
      return PackageItemState.pending();
    }
    return PackageItemState.partial(new LinkedHashSet<>(record.failedItemKeys()));
  }

  private static List<PackageItem> packageItems(PackageItemState itemState) {
    return List.of(
        item(
            "incident:inc-precinct-first-001",
            "INCIDENT_META",
            itemState,
            "sha256:2222222222222222222222222222222222222222222222222222222222222222"),
        item(
            "missing-person:inc-precinct-first-001",
            "MISSING_PERSON_CACHE",
            itemState,
            "sha256:3333333333333333333333333333333333333333333333333333333333333333"),
        item(
            "op-list:inc-precinct-first-001",
            "OP_LIST",
            itemState,
            "sha256:4444444444444444444444444444444444444444444444444444444444444444"),
        item(
            "assigned-area:area-precinct-a1",
            "ASSIGNED_AREA",
            itemState,
            "sha256:5555555555555555555555555555555555555555555555555555555555555555"),
        item(
            "initial-marker:mk-precinct-clue-001",
            "INITIAL_MARKER",
            itemState,
            "sha256:6666666666666666666666666666666666666666666666666666666666666666"),
        item(
            "overall-search-area:overall-area-hash-precinct-current",
            "OVERALL_SEARCH_AREA",
            itemState,
            "sha256:7777777777777777777777777777777777777777777777777777777777777777"),
        item(
            "tile-manifest:tile-manifest-inc-precinct-001",
            "TILE",
            itemState,
            "sha256:8888888888888888888888888888888888888888888888888888888888888888"));
  }

  private static PackageItem item(
      String itemKey, String itemType, PackageItemState itemState, String sourceHash) {
    String status = itemState.statusOf(itemKey);
    return new PackageItem(itemKey, itemType, status, 1L, sourceHash);
  }

  private static List<TileItem> tileItems() {
    return List.of(
        tile(
            15,
            27925,
            12680,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            18432),
        tile(
            15,
            27926,
            12680,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            20480),
        tile(
            16,
            27925,
            12681,
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            24576));
  }

  private static TileItem tile(int z, int x, int y, String sha256, int bytes) {
    return new TileItem(
        "tile:osm-local:%d:%d:%d".formatted(z, x, y),
        "osm-local",
        z,
        x,
        y,
        "local://tiles/%s/%d/%d/%d.pbf".formatted(INCIDENT_ID, z, x, y),
        "sha256:" + sha256,
        bytes);
  }

  private static List<List<BigDecimal>> overallSearchAreaPolygon() {
    return List.of(
        point("126.948000", "37.565000"),
        point("126.968000", "37.565000"),
        point("126.968000", "37.579000"),
        point("126.948000", "37.579000"),
        point("126.948000", "37.565000"));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }

  private record PackageItemState(boolean allDownloaded, Set<String> failedKeys) {

    static PackageItemState pending() {
      return new PackageItemState(false, Set.of());
    }

    static PackageItemState downloaded() {
      return new PackageItemState(true, Set.of());
    }

    static PackageItemState partial(Set<String> failedKeys) {
      return new PackageItemState(true, Set.copyOf(failedKeys));
    }

    String statusOf(String itemKey) {
      if (!allDownloaded) {
        return "PENDING";
      }
      return failedKeys.contains(itemKey) ? "FAILED" : "DOWNLOADED";
    }
  }
}
