package com.surimap.offlinepackage.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.domain.MissingPersonRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
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
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
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
  private final IncidentMapper incidentMapper;
  private final OperationalPeriodQuery operationalPeriodQuery;
  private final SearchAreaQuery searchAreaQuery;
  private final SearchAreaAssignmentQuery assignmentQuery;
  private final MarkerQuery markerQuery;

  public OfflinePackageRepository(OfflinePackageMapper mapper) {
    this(
        mapper,
        (IncidentMapper) null,
        (OperationalPeriodQuery) null,
        (SearchAreaQuery) null,
        (SearchAreaAssignmentQuery) null,
        (MarkerQuery) null);
  }

  @Autowired
  public OfflinePackageRepository(
      OfflinePackageMapper mapper,
      ObjectProvider<IncidentMapper> incidentMapper,
      ObjectProvider<OperationalPeriodQuery> operationalPeriodQuery,
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchAreaAssignmentQuery> assignmentQuery,
      ObjectProvider<MarkerQuery> markerQuery) {
    this(
        mapper,
        incidentMapper == null ? null : incidentMapper.getIfAvailable(),
        operationalPeriodQuery == null ? null : operationalPeriodQuery.getIfAvailable(),
        searchAreaQuery == null ? null : searchAreaQuery.getIfAvailable(),
        assignmentQuery == null ? null : assignmentQuery.getIfAvailable(),
        markerQuery == null ? null : markerQuery.getIfAvailable());
  }

  private OfflinePackageRepository(
      OfflinePackageMapper mapper,
      IncidentMapper incidentMapper,
      OperationalPeriodQuery operationalPeriodQuery,
      SearchAreaQuery searchAreaQuery,
      SearchAreaAssignmentQuery assignmentQuery,
      MarkerQuery markerQuery) {
    this.mapper = mapper;
    this.incidentMapper = incidentMapper;
    this.operationalPeriodQuery = operationalPeriodQuery;
    this.searchAreaQuery = searchAreaQuery;
    this.assignmentQuery = assignmentQuery;
    this.markerQuery = markerQuery;
  }

  public synchronized OfflinePackageManifestResponse manifest(
      String incidentId, String policePhoneId) {
    String incidentDbId = incidentDbId(incidentId);
    String policePhoneDbId =
        policePhoneDbId(policePhoneId == null ? POLICE_PHONE_ID : policePhoneId);
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(incidentDbId);
    if (current == null) {
      Optional<OfflinePackageManifestResponse> generated =
          generateManifestFromSources(incidentDbId, policePhoneDbId);
      if (generated.isPresent()) {
        return generated.get();
      }
      ensureFixtureManifest();
      current = mapper.findCurrentManifestByIncident(incidentDbId);
    }
    if (current == null) {
      throw new IllegalArgumentException("offline package manifest not found: " + incidentId);
    }
    if (PURGED_MANIFEST_HASH.equals(current.manifestHash())
        || (mapper.countActiveInstallationsByManifest(current.id().toString()) == 0
            && mapper.countPurgedInstallationsByManifest(current.id().toString()) > 0)) {
      throw new OfflinePackageApiException("package_purged", HttpStatus.GONE);
    }
    Optional<SourceSnapshot> sourceSnapshot = sourceSnapshot(UUID.fromString(incidentDbId));
    if (sourceSnapshot.isPresent()) {
      return manifestFromSource(current, policePhoneDbId, sourceSnapshot.get());
    }
    return fixtureManifest(current, policePhoneDbId);
  }

  private Optional<OfflinePackageManifestResponse> generateManifestFromSources(
      String incidentDbId, String policePhoneDbId) {
    UUID incidentUuid = UUID.fromString(incidentDbId);
    Optional<SourceSnapshot> sourceSnapshot = sourceSnapshot(incidentUuid);
    if (sourceSnapshot.isEmpty()) {
      return Optional.empty();
    }
    SourceSnapshot snapshot = sourceSnapshot.get();
    int manifestVersion = 1;
    String manifestId = manifestDbId("generated:" + incidentDbId + ":" + manifestVersion);
    String manifestHash = sourceHash(snapshot.manifestHashSource());
    mapper.insertManifest(
        manifestId,
        incidentDbId,
        manifestVersion,
        snapshot.currentOp().opId().toString(),
        snapshot.overallSearchArea().id().toString(),
        snapshot.overallSearchArea().version(),
        manifestHash,
        EXPIRES_AT,
        SERVER_TS);
    OfflinePackageManifestRecord record =
        new OfflinePackageManifestRecord(
            UUID.fromString(manifestId),
            incidentUuid,
            manifestVersion,
            snapshot.overallSearchArea().id(),
            snapshot.overallSearchArea().version(),
            manifestHash,
            EXPIRES_AT);
    return Optional.of(manifestFromSource(record, policePhoneDbId, snapshot));
  }

  private OfflinePackageManifestResponse fixtureManifest(
      OfflinePackageManifestRecord current, String policePhoneDbId) {
    String publicIncidentId = incidentPublicId(current.incidentId().toString());
    String publicManifestId = manifestPublicId(current);
    String publicOverallSearchAreaId = searchAreaPublicId(current.overallSearchAreaId().toString());
    PackageItemState itemState = itemStateFor(current.id().toString(), policePhoneDbId);
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
        fixturePackageItems(itemState, publicManifestId, publicOverallSearchAreaId));
  }

  private Optional<SourceSnapshot> sourceSnapshot(UUID incidentId) {
    if (incidentMapper == null
        || operationalPeriodQuery == null
        || searchAreaQuery == null
        || assignmentQuery == null
        || markerQuery == null) {
      return Optional.empty();
    }
    try {
      return readSourceSnapshot(incidentId);
    } catch (DataAccessException ignored) {
      return Optional.empty();
    }
  }

  private Optional<SourceSnapshot> readSourceSnapshot(UUID incidentId) {
    Optional<IncidentRecord> incident = incidentMapper.findByIncidentId(incidentId);
    if (incident.isEmpty()) {
      return Optional.empty();
    }
    List<OperationalPeriodRow> queriedOperationalPeriods = operationalPeriodQuery.list(incidentId);
    List<OperationalPeriodRow> operationalPeriods =
        queriedOperationalPeriods == null ? List.of() : queriedOperationalPeriods;
    if (operationalPeriods.isEmpty()) {
      return Optional.empty();
    }
    Optional<OverallSearchAreaResult> overallSearchArea = searchAreaQuery.overallOf(incidentId);
    if (overallSearchArea.isEmpty()) {
      return Optional.empty();
    }

    OperationalPeriodRow currentOp =
        operationalPeriods.stream()
            .filter(op -> "ACTIVE".equals(op.status()))
            .findFirst()
            .orElse(operationalPeriods.get(0));
    SearchAreaCollection opAreas = searchAreaQuery.byOp(currentOp.opId(), SearchAreaFilters.empty());
    Map<UUID, SearchAreaRow> areasById =
        opAreas == null
            ? Map.of()
            : opAreas.areas().stream()
                .collect(
                    Collectors.toMap(
                        SearchAreaRow::id,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new));
    var queriedAssignments = assignmentQuery.byOp(currentOp.opId());
    Set<UUID> assignedAreaIds =
        queriedAssignments == null
            ? Set.of()
            : queriedAssignments.stream()
                .map(row -> row.searchAreaId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    List<SearchAreaRow> assignedAreas =
        assignedAreaIds.stream().map(areasById::get).filter(Objects::nonNull).toList();
    MarkerQueryResult markerResult = markerQuery.byIncident(incidentId, MarkerQueryFilters.empty());
    List<MarkerView> markers = markerResult == null ? List.of() : markerResult.markers();

    return Optional.of(
        new SourceSnapshot(
            incident.get(),
            incidentMapper.findMissingPersonByIncidentId(incidentId).orElse(null),
            operationalPeriods.stream()
                .sorted(Comparator.comparingInt(OperationalPeriodRow::sequenceNumber))
                .toList(),
            currentOp,
            overallSearchArea.get(),
            assignedAreas,
            markers.stream()
                .filter(marker -> marker.location() != null)
                .sorted(
                    Comparator.comparing(MarkerView::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MarkerView::id))
                .toList()));
  }

  private OfflinePackageManifestResponse manifestFromSource(
      OfflinePackageManifestRecord record, String policePhoneDbId, SourceSnapshot source) {
    String incidentId = source.incident().getId().toString();
    String manifestId = manifestPublicId(record);
    PackageItemState itemState = itemStateFor(record.id().toString(), policePhoneDbId);
    return new OfflinePackageManifestResponse(
        manifestId,
        incidentId,
        record.manifestVersion(),
        record.expiresAt(),
        "sha256:" + record.manifestHash(),
        new PolicePhoneContext(
            policePhonePublicId(policePhoneDbId),
            AccountIdentityCatalog.PRECINCT_TEAM_ID.toString(),
            "TEAM",
            "team-precinct-jongno",
            "MEMBER"),
        new IncidentMetadata(
            incidentId,
            source.incident().getStatus(),
            "OPEN".equals(source.incident().getStatus()) ? "CURRENT" : "TERMINAL",
            source.incident().getSourceIncidentId()),
        missingPersonFromSource(source.missingPerson()),
        source.operationalPeriods().stream()
            .map(
                op ->
                    new OperationalPeriod(
                        op.opId().toString(),
                        op.incidentId().toString(),
                        op.sequenceNumber(),
                        op.status(),
                        op.version()))
            .toList(),
        source.assignedAreas().stream()
            .map(
                area ->
                    new AssignedArea(
                        area.id().toString(),
                        area.incidentId().toString(),
                        area.opId().toString(),
                        area.status(),
                        area.version()))
            .toList(),
        source.initialMarkers().stream()
            .map(
                marker ->
                    new InitialMarker(
                        marker.id().toString(),
                        marker.incidentId().toString(),
                        marker.opId().toString(),
                        coordinate(marker.location()),
                        marker.status().name()))
            .toList(),
        new OverallSearchArea(
            source.overallSearchArea().id().toString(),
            source.overallSearchArea().incidentId().toString(),
            "OVERALL",
            source.overallSearchArea().status(),
            sourceHash("overall:" + source.overallSearchArea().id() + ":" + source.overallSearchArea().version()),
            source.overallSearchArea().geometry().outerRing()),
        tileItems(),
        sourcePackageItems(itemState, manifestId, source));
  }

  private static MissingPerson missingPersonFromSource(MissingPersonRecord missingPerson) {
    if (missingPerson == null) {
      return null;
    }
    return new MissingPerson(
        missingPerson.getIncidentId().toString(),
        missingPerson.getDisplayName(),
        missingPerson.getPhotoObjectKey(),
        missingPerson.getAppearanceText(),
        missingPerson.getLastSeenLocationText(),
        missingPerson.getLastSeenAt() == null
            ? null
            : OffsetDateTime.ofInstant(missingPerson.getLastSeenAt(), ZoneOffset.UTC));
  }

  public synchronized OfflinePackageInstallationStatus saveStatus(
      String incidentId, OfflinePackageInstallationReportRequest request) {
    ensureFixtureManifest();
    String incidentDbId = incidentDbId(incidentId);
    String requestManifestDbId = manifestDbId(request.manifestId());
    String requestPolicePhoneDbId = policePhoneDbId(request.policePhoneId());
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(incidentDbId);
    if (current == null
        || !current.id().toString().equals(requestManifestDbId)
        || current.manifestVersion() != request.manifestVersion()) {
      throw new OfflinePackageApiException("write_conflict", HttpStatus.CONFLICT);
    }
    if (PURGED_MANIFEST_HASH.equals(current.manifestHash())) {
      throw new OfflinePackageApiException("incident_closed", HttpStatus.CONFLICT);
    }
    OfflinePackageInstallationRecord record =
        OfflinePackageInstallationRecord.from(
            installationIdForReport(requestManifestDbId, requestPolicePhoneDbId),
            incidentDbId,
            requestManifestDbId,
            requestPolicePhoneDbId,
            request,
            SERVER_TS);
    mapper.deleteInstallationForPhone(requestManifestDbId, requestPolicePhoneDbId);
    mapper.insertInstallation(record);
    return publicStatus(
        new OfflinePackageInstallationStatus(
            record.id().toString(),
            record.incidentId().toString(),
            record.policePhoneId().toString(),
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
    if (current.overallSearchAreaId().toString().equals(overallSearchAreaDbId)
        && current.overallSearchAreaVersion() >= overallSearchAreaVersion) {
      return List.of();
    }

    List<String> changedStatusIds = mapper.findStaleCandidateInstallationIds(current.id().toString());
    int nextManifestVersion = current.manifestVersion() + 1;
    String nextManifestId = nextManifestId(nextManifestVersion);
    mapper.insertNextManifestFrom(
        current.id().toString(),
        manifestDbId(nextManifestId),
        nextManifestVersion,
        overallSearchAreaDbId,
        overallSearchAreaVersion,
        manifestHash(incidentId, overallSearchAreaId, overallSearchAreaVersion, sourceHash),
        SERVER_TS);
    if (changedStatusIds.isEmpty()) {
      return List.of();
    }
    mapper.markReadyAndPartialInstallationsStale(current.id().toString(), SERVER_TS);
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
    OfflinePackageManifestRecord current = mapper.findCurrentManifestByIncident(INCIDENT_DB_ID);
    if (current != null && !current.id().toString().equals(MANIFEST_DB_ID)) {
      return;
    }
    if (current != null || mapper.countManifest(MANIFEST_DB_ID) > 0) {
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
        1L,
        "1111111111111111111111111111111111111111111111111111111111111111",
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
            UUID.fromString(installationDbId(id)),
            UUID.fromString(MANIFEST_DB_ID),
            UUID.fromString(INCIDENT_DB_ID),
            UUID.fromString(policePhoneDbId(policePhoneId)),
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

  private static List<PackageItem> fixturePackageItems(
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

  private static List<PackageItem> sourcePackageItems(
      PackageItemState itemState, String manifestId, SourceSnapshot source) {
    List<PackageItem> items = new ArrayList<>();
    String incidentId = source.incident().getId().toString();
    items.add(
        item(
            "incident:" + incidentId,
            "INCIDENT_META",
            itemState,
            source.incident().getVersion(),
            prefixedSourceHash("incident:" + incidentId + ":" + source.incident().getVersion())));
    items.add(
        item(
            "missing-person:" + incidentId,
            "MISSING_PERSON_CACHE",
            itemState,
            source.missingPerson() == null ? 0L : source.incident().getVersion(),
            prefixedSourceHash("missing-person:" + incidentId + ":" + source.missingPersonHash())));
    items.add(
        item(
            "op-list:" + incidentId,
            "OP_LIST",
            itemState,
            source.maxOperationalPeriodVersion(),
            prefixedSourceHash("op-list:" + source.operationalPeriodHash())));
    String assignedAreaKey =
        source.assignedAreas().isEmpty()
            ? "assigned-area:none:" + incidentId
            : "assigned-area:" + source.assignedAreas().get(0).id();
    items.add(
        item(
            assignedAreaKey,
            "ASSIGNED_AREA",
            itemState,
            source.maxAssignedAreaVersion(),
            prefixedSourceHash("assigned-area:" + source.assignedAreaHash())));
    String markerKey =
        source.initialMarkers().isEmpty()
            ? "initial-marker:none:" + incidentId
            : "initial-marker:" + source.initialMarkers().get(0).id();
    items.add(
        item(
            markerKey,
            "INITIAL_MARKER",
            itemState,
            source.maxMarkerVersion(),
            prefixedSourceHash("initial-marker:" + source.markerHash())));
    items.add(
        item(
            "overall-search-area:" + source.overallSearchArea().id(),
            "OVERALL_SEARCH_AREA",
            itemState,
            source.overallSearchArea().version(),
            prefixedSourceHash(
                "overall:"
                    + source.overallSearchArea().id()
                    + ":"
                    + source.overallSearchArea().version())));
    items.add(
        item(
            "tile-manifest:" + manifestId,
            "TILE",
            itemState,
            1L,
            prefixedSourceHash("tile-manifest:" + manifestId)));
    return List.copyOf(items);
  }

  private static PackageItem item(
      String itemKey, String itemType, PackageItemState itemState, String sourceHash) {
    return item(itemKey, itemType, itemState, 1L, sourceHash);
  }

  private static PackageItem item(
      String itemKey,
      String itemType,
      PackageItemState itemState,
      long sourceVersion,
      String sourceHash) {
    String status = itemState.statusOf(itemKey);
    return new PackageItem(itemKey, itemType, status, sourceVersion, sourceHash);
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

  private static List<BigDecimal> coordinate(Point point) {
    return List.of(BigDecimal.valueOf(point.getX()), BigDecimal.valueOf(point.getY()));
  }

  private static String prefixedSourceHash(String source) {
    return "sha256:" + sourceHash(source);
  }

  private static String sourceHash(String source) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
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

  private static String installationIdForReport(String manifestDbId, String policePhoneDbId) {
    if (MANIFEST_DB_ID.equals(manifestDbId) && POLICE_PHONE_ID.equals(policePhoneDbId)) {
      return INSTALLATION_DB_ID;
    }
    return installationDbId(manifestDbId + ":" + policePhoneDbId);
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
    return manifest.id().toString();
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

  private record SourceSnapshot(
      IncidentRecord incident,
      MissingPersonRecord missingPerson,
      List<OperationalPeriodRow> operationalPeriods,
      OperationalPeriodRow currentOp,
      OverallSearchAreaResult overallSearchArea,
      List<SearchAreaRow> assignedAreas,
      List<MarkerView> initialMarkers) {

    String manifestHashSource() {
      return String.join(
          "|",
          "incident:" + incident.getId() + ":" + incident.getVersion(),
          "missing-person:" + missingPersonHash(),
          "ops:" + operationalPeriodHash(),
          "assigned:" + assignedAreaHash(),
          "markers:" + markerHash(),
          "overall:" + overallSearchArea.id() + ":" + overallSearchArea.version());
    }

    String missingPersonHash() {
      if (missingPerson == null) {
        return "none";
      }
      return String.join(
          ":",
          missingPerson.getIncidentId().toString(),
          Objects.toString(missingPerson.getDisplayName(), ""),
          Objects.toString(missingPerson.getPhotoObjectKey(), ""),
          Objects.toString(missingPerson.getAppearanceText(), ""),
          Objects.toString(missingPerson.getLastSeenLocationText(), ""),
          Objects.toString(missingPerson.getLastSeenAt(), ""));
    }

    String operationalPeriodHash() {
      return operationalPeriods.stream()
          .map(op -> op.opId() + ":" + op.status() + ":" + op.sequenceNumber() + ":" + op.version())
          .collect(Collectors.joining(","));
    }

    long maxOperationalPeriodVersion() {
      return operationalPeriods.stream().mapToLong(OperationalPeriodRow::version).max().orElse(0L);
    }

    String assignedAreaHash() {
      return assignedAreas.stream()
          .map(area -> area.id() + ":" + area.status() + ":" + area.version())
          .collect(Collectors.joining(","));
    }

    long maxAssignedAreaVersion() {
      return assignedAreas.stream().mapToLong(SearchAreaRow::version).max().orElse(0L);
    }

    String markerHash() {
      return initialMarkers.stream()
          .map(marker -> marker.id() + ":" + marker.status() + ":" + marker.version())
          .collect(Collectors.joining(","));
    }

    long maxMarkerVersion() {
      return initialMarkers.stream().mapToLong(MarkerView::version).max().orElse(0L);
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
