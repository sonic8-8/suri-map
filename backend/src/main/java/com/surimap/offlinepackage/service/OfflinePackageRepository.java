package com.surimap.offlinepackage.service;

import com.surimap.account.AccountIdentityCatalog;
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
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.offlinepackage.repository.OfflinePackageInstallationRecord;
import com.surimap.offlinepackage.repository.OfflinePackageManifestRecord;
import com.surimap.offlinepackage.repository.OfflinePackageMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class OfflinePackageRepository {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final String INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001";
  public static final String MANIFEST_ALIAS = "tile-manifest-inc-precinct-001";
  public static final String MANIFEST_ID = "77777777-0000-4000-8000-000000000701";
  public static final int MANIFEST_VERSION = 1;
  public static final String POLICE_PHONE_CODE = "dev-precinct-phone-01";
  public static final String POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  public static final String INSTALLATION_ALIAS = "pkg-status-precinct-001";
  public static final String INSTALLATION_ID = "77777777-0000-4000-8000-000000000901";
  public static final int INSTALLATION_VERSION = 3;
  public static final int SEQUENCE = 901;
  public static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T09:00:41+09:00");
  private static final String INCIDENT_DB_ID = INCIDENT_ID;
  private static final String OP_ALIAS = "op-precinct-001-op1";
  private static final String OP_DB_ID = "88888888-8888-8888-8888-888888880001";
  private static final String OVERALL_SEARCH_AREA_ALIAS = "osa-precinct-001";
  private static final String OVERALL_SEARCH_AREA_DB_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001";
  private static final String ASSIGNED_AREA_ALIAS = "area-precinct-a1";
  private static final String ASSIGNED_AREA_DB_ID = "cccccccc-cccc-cccc-cccc-cccccccc0001";
  private static final String MANIFEST_DB_ID = MANIFEST_ID;
  private static final String INSTALLATION_DB_ID = INSTALLATION_ID;
  private static final String PURGED_MANIFEST_HASH =
      "0000000000000000000000000000000000000000000000000000000000000000";
  private static final OffsetDateTime EXPIRES_AT =
      OffsetDateTime.parse("2026-04-28T12:00:00+09:00");
  private static final Map<String, String> KNOWN_DB_IDS_BY_ALIAS =
      Map.ofEntries(
          Map.entry(INCIDENT_ALIAS, INCIDENT_DB_ID),
          Map.entry(OP_ALIAS, OP_DB_ID),
          Map.entry(OVERALL_SEARCH_AREA_ALIAS, OVERALL_SEARCH_AREA_DB_ID),
          Map.entry("osa-precinct-001-v1", OVERALL_SEARCH_AREA_DB_ID),
          Map.entry(ASSIGNED_AREA_ALIAS, ASSIGNED_AREA_DB_ID),
          Map.entry(MANIFEST_ALIAS, MANIFEST_DB_ID),
          Map.entry(INSTALLATION_ALIAS, INSTALLATION_DB_ID),
          Map.entry(POLICE_PHONE_CODE, POLICE_PHONE_ID),
          Map.entry("dev-precinct-cmd-phone-01", "00000000-0000-0000-0000-000000000201"),
          Map.entry("dev-precinct-car-01", "50000000-0000-0000-0000-000000000001"),
          Map.entry("dev-alpha-cmd-phone-01", "00000000-0000-0000-0000-000000000204"),
          Map.entry("dev-alpha-phone-01", "00000000-0000-0000-0000-000000000205"),
          Map.entry("dev-support-cmd-phone-01", "00000000-0000-0000-0000-000000000206"),
          Map.entry("dev-support-car-01", "00000000-0000-0000-0000-000000000207"),
          Map.entry("dev-support-phone-01", "00000000-0000-0000-0000-000000000208"));
  private static final List<String> SEED_INSTALLATION_ALIASES =
      List.of(
          "pkg-status-precinct-ready-001",
          "pkg-status-precinct-partial-001",
          "pkg-status-precinct-downloading-001",
          "pkg-status-precinct-stale-001",
          "pkg-status-precinct-failed-001");
  private static final List<String> SEED_POLICE_PHONE_ALIASES =
      List.of(
          POLICE_PHONE_CODE,
          "dev-precinct-phone-02",
          "dev-precinct-phone-03",
          "dev-precinct-phone-04",
          "dev-precinct-phone-05");

  private final OfflinePackageMapper mapper;

  public OfflinePackageRepository(OfflinePackageMapper mapper) {
    this.mapper = mapper;
  }

  public synchronized OfflinePackageManifestResponse manifest(
      String incidentId, String policePhoneId) {
    ensureFixtureManifest();
    String incidentDbId = incidentDbId(incidentId);
    String policePhoneDbId =
        policePhoneDbId(policePhoneId == null ? POLICE_PHONE_ID : policePhoneId);
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(incidentDbId);
    if (current == null) {
      throw new IllegalArgumentException("offline package manifest not found: " + incidentId);
    }
    if (PURGED_MANIFEST_HASH.equals(current.manifestHash())
        || (mapper.countActiveInstallationsByManifest(current.id()) == 0
            && mapper.countPurgedInstallationsByManifest(current.id()) > 0)) {
      throw new OfflinePackageApiException("package_purged", HttpStatus.GONE);
    }
    String publicIncidentId = incidentPublicId(current.incidentId());
    String publicManifestId = manifestPublicId(current);
    String publicOverallSearchAreaId = searchAreaPublicId(current.overallSearchAreaId());
    PackageItemState itemState = itemStateFor(current.id(), policePhoneDbId);
    return new OfflinePackageManifestResponse(
        publicManifestId,
        publicIncidentId,
        current.manifestVersion(),
        current.expiresAt(),
        "sha256:" + current.manifestHash(),
        new PolicePhoneContext(
            policePhonePublicId(policePhoneDbId),
            AccountIdentityCatalog.PRECINCT_TEAM_ID.toString(),
            "TEAM",
            "team-precinct-jongno",
            "MEMBER"),
        new IncidentMetadata(publicIncidentId, "OPEN", "CURRENT", "mock-112-incident-001"),
        new MissingPerson(
            publicIncidentId,
            "가상 실종자 001",
            null,
            "남색 점퍼, 회색 등산화",
            "인왕산 북측 산책로 입구",
            OffsetDateTime.parse("2026-04-28T08:30:00+09:00")),
        List.of(new OperationalPeriod(OP_DB_ID, publicIncidentId, 1, "ACTIVE", 1L)),
        List.of(
            new AssignedArea(
                ASSIGNED_AREA_DB_ID, publicIncidentId, OP_DB_ID, "ASSIGNED", 1L)),
        List.of(
            new InitialMarker(
                "mk-precinct-clue-001",
                publicIncidentId,
                OP_DB_ID,
                point("126.956500", "37.571200"),
                "ACTIVE")),
        new OverallSearchArea(
            publicOverallSearchAreaId,
            publicIncidentId,
            "OVERALL",
            "ACTIVE",
            "overall-area-hash-precinct-current",
            overallSearchAreaPolygon()),
        tileItems(),
        packageItems(itemState, publicManifestId, publicOverallSearchAreaId));
  }

  public synchronized OfflinePackageInstallationStatus saveStatus(
      String incidentId, OfflinePackageInstallationReportRequest request) {
    ensureFixtureManifest();
    String incidentDbId = incidentDbId(incidentId);
    String requestManifestDbId = manifestDbId(request.manifestId());
    String requestPolicePhoneDbId = policePhoneDbId(request.policePhoneId());
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(incidentDbId);
    if (current == null
        || !current.id().equals(requestManifestDbId)
        || current.manifestVersion() != request.manifestVersion()) {
      throw new OfflinePackageApiException("write_conflict", HttpStatus.CONFLICT);
    }
    if (PURGED_MANIFEST_HASH.equals(current.manifestHash())) {
      throw new OfflinePackageApiException("incident_closed", HttpStatus.CONFLICT);
    }
    OfflinePackageInstallationRecord record =
        OfflinePackageInstallationRecord.from(
            INSTALLATION_DB_ID,
            incidentDbId,
            requestManifestDbId,
            requestPolicePhoneDbId,
            request,
            SERVER_TS);
    mapper.deleteInstallationForPhone(requestManifestDbId, requestPolicePhoneDbId);
    mapper.insertInstallation(record);
    return publicStatus(
        new OfflinePackageInstallationStatus(
            record.id(),
            record.incidentId(),
            record.policePhoneId(),
            record.status(),
            record.version(),
            record.sequence(),
            request.manifestVersion(),
            current.manifestVersion(),
            record.readyForOfflineUse()));
  }

  public synchronized List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
    return mapper.findStatusesByIncident(incidentDbId(incidentId)).stream()
        .map(OfflinePackageRepository::publicStatus)
        .toList();
  }

  public synchronized List<OfflinePackageInstallationStatus>
      staleReadyAndPartialForOverallAreaChange(
          String incidentId,
          String overallSearchAreaId,
          long overallSearchAreaVersion,
          String sourceHash) {
    ensureFixtureManifest();
    String incidentDbId = incidentDbId(incidentId);
    String overallSearchAreaDbId = searchAreaDbId(overallSearchAreaId);
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(incidentDbId);
    if (current == null) {
      return List.of();
    }
    if (PURGED_MANIFEST_HASH.equals(current.manifestHash())) {
      return List.of();
    }
    if (current.overallSearchAreaId().equals(overallSearchAreaDbId)
        && current.overallSearchAreaVersion() >= overallSearchAreaVersion) {
      return List.of();
    }

    List<String> changedStatusIds = mapper.findStaleCandidateInstallationIds(current.id());
    int nextManifestVersion = current.manifestVersion() + 1;
    String nextManifestId = nextManifestId(nextManifestVersion);
    mapper.insertNextManifestFrom(
        current.id(),
        manifestDbId(nextManifestId),
        nextManifestVersion,
        overallSearchAreaDbId,
        overallSearchAreaVersion,
        manifestHash(incidentId, overallSearchAreaId, overallSearchAreaVersion, sourceHash),
        SERVER_TS);
    if (changedStatusIds.isEmpty()) {
      return List.of();
    }
    mapper.markReadyAndPartialInstallationsStale(current.id(), SERVER_TS);
    return mapper.findStatusesByIds(changedStatusIds, nextManifestVersion).stream()
        .map(OfflinePackageRepository::publicStatus)
        .toList();
  }

  public synchronized long purgeIncidentPackage(UUID incidentId) {
    String incidentIdValue = incidentId.toString();
    long targetInstallationCount = mapper.countPurgeTargetInstallationsByIncident(incidentIdValue);
    long targetManifestCount =
        mapper.countPurgeTargetManifestsByIncident(incidentIdValue, PURGED_MANIFEST_HASH);
    OffsetDateTime purgedAt = OffsetDateTime.now(ZoneOffset.UTC);
    mapper.tombstoneInstallationsByIncident(incidentIdValue, purgedAt);
    mapper.sanitizeManifestPayloadsByIncident(incidentIdValue, PURGED_MANIFEST_HASH, purgedAt);
    return targetManifestCount + targetInstallationCount;
  }

  private void ensureFixtureManifest() {
    if (mapper.countManifest(MANIFEST_DB_ID) > 0) {
      ensureSeedStatus("pkg-status-precinct-ready-001", POLICE_PHONE_ID, "READY", 3, true);
      ensureSeedStatus(
          "pkg-status-precinct-partial-001", "dev-precinct-phone-02", "PARTIAL", 2, false);
      ensureSeedStatus(
          "pkg-status-precinct-downloading-001", "dev-precinct-phone-05", "DOWNLOADING", 1, false);
      ensureSeedStatus("pkg-status-precinct-stale-001", "dev-precinct-phone-03", "STALE", 4, false);
      ensureSeedStatus(
          "pkg-status-precinct-failed-001", "dev-precinct-phone-04", "FAILED", 5, false);
      return;
    }
    mapper.insertManifest(
        MANIFEST_DB_ID,
        INCIDENT_DB_ID,
        MANIFEST_VERSION,
        OP_DB_ID,
        OVERALL_SEARCH_AREA_DB_ID,
        EXPIRES_AT,
        SERVER_TS);
    seedStatus("pkg-status-precinct-ready-001", POLICE_PHONE_ID, "READY", 3, true);
    seedStatus("pkg-status-precinct-partial-001", "dev-precinct-phone-02", "PARTIAL", 2, false);
    seedStatus(
        "pkg-status-precinct-downloading-001", "dev-precinct-phone-05", "DOWNLOADING", 1, false);
    seedStatus("pkg-status-precinct-stale-001", "dev-precinct-phone-03", "STALE", 4, false);
    seedStatus("pkg-status-precinct-failed-001", "dev-precinct-phone-04", "FAILED", 5, false);
  }

  private void seedStatus(
      String id, String policePhoneId, String status, long version, boolean readyForOfflineUse) {
    mapper.insertInstallation(
        new OfflinePackageInstallationRecord(
            installationDbId(id),
            MANIFEST_DB_ID,
            INCIDENT_DB_ID,
            policePhoneDbId(policePhoneId),
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

  private void ensureSeedStatus(
      String id, String policePhoneId, String status, long version, boolean readyForOfflineUse) {
    if (mapper.countInstallation(installationDbId(id)) > 0) {
      return;
    }
    String seedPolicePhoneId = policePhoneId;
    if (mapper.findInstallationForPhone(MANIFEST_DB_ID, policePhoneDbId(policePhoneId)) != null) {
      seedPolicePhoneId = policePhoneId + "-seed";
    }
    seedStatus(id, seedPolicePhoneId, status, version, readyForOfflineUse);
  }

  private PackageItemState itemStateFor(String manifestId, String policePhoneId) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      return PackageItemState.pending();
    }
    OfflinePackageInstallationRecord record =
        mapper.findInstallationForPhone(manifestId, policePhoneId);
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

  private static List<PackageItem> packageItems(
      PackageItemState itemState, String manifestId, String overallSearchAreaId) {
    return List.of(
        item(
            "incident:" + INCIDENT_ALIAS,
            "INCIDENT_META",
            itemState,
            "sha256:2222222222222222222222222222222222222222222222222222222222222222"),
        item(
            "missing-person:" + INCIDENT_ALIAS,
            "MISSING_PERSON_CACHE",
            itemState,
            "sha256:3333333333333333333333333333333333333333333333333333333333333333"),
        item(
            "op-list:" + INCIDENT_ALIAS,
            "OP_LIST",
            itemState,
            "sha256:4444444444444444444444444444444444444444444444444444444444444444"),
        item(
            "assigned-area:" + ASSIGNED_AREA_ALIAS,
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
            "tile-manifest:" + MANIFEST_ALIAS,
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
        "local://tiles/%s/%d/%d/%d.pbf".formatted(INCIDENT_ALIAS, z, x, y),
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

  private static String manifestHash(
      String incidentId,
      String overallSearchAreaId,
      long overallSearchAreaVersion,
      String sourceHash) {
    String source =
        incidentId + "|" + overallSearchAreaId + "|" + overallSearchAreaVersion + "|" + sourceHash;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static String nextManifestId(int nextManifestVersion) {
    return manifestDbId(MANIFEST_ALIAS + "-rev-" + nextManifestVersion);
  }

  private static OfflinePackageInstallationStatus publicStatus(
      OfflinePackageInstallationStatus status) {
    return new OfflinePackageInstallationStatus(
        installationPublicId(status.id()),
        incidentPublicId(status.incidentId()),
        policePhonePublicId(status.policePhoneId()),
        status.status(),
        status.version(),
        status.sequence(),
        status.manifestVersion(),
        status.activeManifestVersion(),
        status.readyForOfflineUse());
  }

  private static String incidentDbId(String value) {
    return dbId(value, "incident");
  }

  private static String manifestDbId(String value) {
    return dbId(value, "offline_package_manifest");
  }

  private static String installationDbId(String value) {
    return dbId(value, "offline_package_installation");
  }

  private static String policePhoneDbId(String value) {
    return dbId(value, "police_phone");
  }

  private static String searchAreaDbId(String value) {
    return dbId(value, "search_area");
  }

  private static String dbId(String value, String namespace) {
    if (value == null || value.isBlank()) {
      return value;
    }
    String known = KNOWN_DB_IDS_BY_ALIAS.get(value);
    if (known != null) {
      return known;
    }
    if (isUuid(value)) {
      return value;
    }
    return derivedUuid(namespace, value);
  }

  private static String manifestPublicId(OfflinePackageManifestRecord manifest) {
    return manifestDbId(manifest.id());
  }

  private static String incidentPublicId(String value) {
    return incidentDbId(value);
  }

  private static String searchAreaPublicId(String value) {
    return searchAreaDbId(value);
  }

  private static String installationPublicId(String value) {
    return installationDbId(value);
  }

  private static String policePhonePublicId(String value) {
    return policePhoneDbId(value);
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static String derivedUuid(String namespace, String value) {
    String source = namespace + ":" + value;
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      String hex = HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
      return "%s-%s-%s-%s-%s"
          .formatted(
              hex.substring(0, 8),
              hex.substring(8, 12),
              hex.substring(12, 16),
              hex.substring(16, 20),
              hex.substring(20, 32));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("MD5 digest is unavailable", exception);
    }
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
