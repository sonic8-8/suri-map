package com.surimap.board;

import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerView;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.path.PathExcludedPoint;
import com.surimap.path.PathQueryRow;
import com.surimap.path.SearchPathSegment;
import com.surimap.path.SearchPathService;
import com.surimap.retention.purge.LocalPurgeState;
import com.surimap.retention.purge.RetentionPurgeStatus;
import com.surimap.retention.purge.RetentionPurgeStatusSnapshot;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIncidentBoardSourceRowCollector implements IncidentBoardSourceRowCollector {

  private static final String EMPTY_BOARD_GEOMETRY_HASH = "hash-board-geometry-current";

  private final ObjectProvider<SearchAreaQuery> searchAreaQuery;
  private final ObjectProvider<SearchPathService> searchPathService;
  private final MarkerQuery markerQuery;
  private final PackageBadgeBoardAssembler packageBadgeBoardAssembler;
  private final OperationalPeriodQuery operationalPeriodQuery;
  private final HandoverMemoQuery handoverMemoQuery;
  private final SearchHistorySummaryMapper searchHistorySummaryMapper;
  private final ObjectProvider<IncidentMapper> incidentMapper;
  private final ObjectProvider<RetentionPurgeStatus> retentionPurgeStatus;

  public DefaultIncidentBoardSourceRowCollector(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchPathService> searchPathService,
      MarkerQuery markerQuery,
      OfflinePackageInstallationQuery offlinePackageInstallationQuery,
      OperationalPeriodQuery operationalPeriodQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper,
      ObjectProvider<IncidentMapper> incidentMapper,
      ObjectProvider<RetentionPurgeStatus> retentionPurgeStatus) {
    this.searchAreaQuery = searchAreaQuery;
    this.searchPathService = searchPathService;
    this.markerQuery = Objects.requireNonNull(markerQuery, "markerQuery must not be null");
    this.packageBadgeBoardAssembler =
        new PackageBadgeBoardAssembler(
            Objects.requireNonNull(
                offlinePackageInstallationQuery, "offlinePackageInstallationQuery must not be null"));
    this.operationalPeriodQuery =
        Objects.requireNonNull(operationalPeriodQuery, "operationalPeriodQuery must not be null");
    this.handoverMemoQuery =
        Objects.requireNonNull(handoverMemoQuery, "handoverMemoQuery must not be null");
    this.searchHistorySummaryMapper =
        Objects.requireNonNull(searchHistorySummaryMapper, "searchHistorySummaryMapper must not be null");
    this.incidentMapper = Objects.requireNonNull(incidentMapper, "incidentMapper must not be null");
    this.retentionPurgeStatus =
        Objects.requireNonNull(retentionPurgeStatus, "retentionPurgeStatus must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public IncidentBoardSourceRowSnapshot collect(BoardSourceRowContext context) {
    Objects.requireNonNull(context, "context must not be null");
    UUID activeOpId =
        operationalPeriodQuery.current(context.incidentId()).map(CurrentOpResult::opId).orElse(null);
    List<UUID> selectedOpIds = selectedOpIds(context.requestedOpIds(), activeOpId);

    List<BoardSourceRow> rows = new ArrayList<>();
    collectSearchAreaRows(context, selectedOpIds, rows);
    collectPathRows(context, selectedOpIds, rows);
    collectMarkerRows(context, selectedOpIds, rows);
    collectPackageRows(context, rows);
    collectOperationalPeriodRows(context, rows);
    collectHandoverMemoRows(context, selectedOpIds, rows);
    collectSearchHistorySummaryRows(context, selectedOpIds, rows);
    collectIncidentTerminalRows(context, rows);

    return new IncidentBoardSourceRowSnapshot(
        activeOpId, selectedOpIds, geometryHash(rows), rows);
  }

  private void collectSearchAreaRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    SearchAreaQuery query = searchAreaQuery.getIfAvailable();
    if (query == null) {
      return;
    }
    if (context.includes("overall_search_area")) {
      query.overallOf(context.incidentId()).map(this::overallSearchAreaRow).ifPresent(rows::add);
    }
    if (context.includes("area")) {
      if (selectedOpIds.isEmpty()) {
        query
            .byIncident(context.incidentId(), searchAreaFilters(null, null))
            .areas()
            .stream()
            .map(this::areaRow)
            .forEach(rows::add);
      } else {
        selectedOpIds.forEach(
            opId ->
                query.byOp(opId, searchAreaFilters(null, null)).areas().stream()
                    .map(this::areaRow)
                    .forEach(rows::add));
      }
    }
  }

  private void collectPathRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    if (!context.includes("path")) {
      return;
    }
    SearchPathService service = searchPathService.getIfAvailable();
    if (service == null) {
      return;
    }
    if (selectedOpIds.isEmpty()) {
      service.query(context.incidentId(), null, null).paths().stream().map(this::pathRow).forEach(rows::add);
      return;
    }
    selectedOpIds.forEach(
        opId ->
            service.query(context.incidentId(), opId, null).paths().stream()
                .map(this::pathRow)
                .forEach(rows::add));
  }

  private void collectMarkerRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    if (!context.includes("marker")) {
      return;
    }
    if (selectedOpIds.isEmpty()) {
      markerQuery.byIncident(context.incidentId(), MarkerQueryFilters.empty()).markers().stream()
          .map(this::markerRow)
          .forEach(rows::add);
      return;
    }
    selectedOpIds.forEach(
        opId ->
            markerQuery
                .byIncident(context.incidentId(), new MarkerQueryFilters(opId, null, null))
                .markers()
                .stream()
                .map(this::markerRow)
                .forEach(rows::add));
  }

  private void collectPackageRows(BoardSourceRowContext context, List<BoardSourceRow> rows) {
    if (!context.includes("package_badge")) {
      return;
    }
    rows.addAll(packageBadgeBoardAssembler.sourceRowsByIncident(context.incidentId().toString()));
  }

  private void collectOperationalPeriodRows(BoardSourceRowContext context, List<BoardSourceRow> rows) {
    boolean includeToggle = context.includes("op_toggle");
    boolean includeHistory = context.includes("op_history");
    if (!includeToggle && !includeHistory) {
      return;
    }
    List<OperationalPeriodRow> opRows = operationalPeriodQuery.list(context.incidentId());
    if (includeToggle) {
      opRows.stream().map(row -> opRow("op_toggle", "board-op-toggle-", row)).forEach(rows::add);
    }
    if (includeHistory) {
      opRows.stream().map(row -> opRow("op_history", "board-op-history-", row)).forEach(rows::add);
    }
  }

  private void collectHandoverMemoRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    if (!context.includes("handover_memo")) {
      return;
    }
    if (selectedOpIds.isEmpty()) {
      handoverMemoQuery.byContext(context.incidentId(), null, null, null).stream()
          .map(this::handoverMemoRow)
          .forEach(rows::add);
      return;
    }
    selectedOpIds.forEach(
        opId ->
            handoverMemoQuery.byContext(context.incidentId(), opId, null, null).stream()
                .map(this::handoverMemoRow)
                .forEach(rows::add));
  }

  private void collectSearchHistorySummaryRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    if (!context.includes("search_history_summary") || selectedOpIds.isEmpty()) {
      return;
    }
    selectedOpIds.forEach(
        opId ->
            searchHistorySummaryMapper
                .findByOp(opId, context.incidentId(), null, null, null, null)
                .stream()
                .map(this::searchHistorySummaryRow)
                .forEach(rows::add));
  }

  private void collectIncidentTerminalRows(BoardSourceRowContext context, List<BoardSourceRow> rows) {
    if (!context.includes("incident_terminal")) {
      return;
    }
    IncidentMapper mapper = incidentMapper.getIfAvailable();
    if (mapper == null) {
      return;
    }

    mapper
        .findByIncidentId(context.incidentId())
        .filter(incident -> "CLOSED".equals(incident.getStatus()))
        .map(incident -> incidentTerminalRow(incident, purgeStatusOf(incident.getId())))
        .ifPresent(rows::add);
  }

  private BoardSourceRow overallSearchAreaRow(OverallSearchAreaResult row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", row.incidentId().toString());
    payload.put("geometryHash", sourceHash("overall_search_area", row.id().toString(), row.version(), row.status()));
    payload.put("geometry", row.geometry());
    payload.put("bbox", row.bbox());
    payload.put("updatedAt", row.updatedAt());
    return sourceRow(
        "overall_search_area",
        "S2",
        row.id().toString(),
        "board-overall-search-area-" + row.id(),
        row.status(),
        row.version(),
        row.version(),
        eventId("S2", "overall-search-area", row.id().toString(), row.version()),
        String.valueOf(payload.get("geometryHash")),
        payload);
  }

  private BoardSourceRow areaRow(SearchAreaRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", row.incidentId().toString());
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "parentAreaId", row.parentAreaId());
    payload.put("geometryHash", sourceHash("area", row.id().toString(), row.version(), row.status()));
    payload.put("geometry", row.geometry());
    payload.put("bbox", row.bbox());
    payload.put("updatedAt", row.updatedAt());
    payload.put("historyCount", row.historyCount());
    return sourceRow(
        "area",
        "S2",
        row.id().toString(),
        "board-area-" + row.id(),
        row.status(),
        row.version(),
        row.version(),
        eventId("S2", "area", row.id().toString(), row.version()),
        String.valueOf(payload.get("geometryHash")),
        payload);
  }

  private BoardSourceRow pathRow(PathQueryRow row) {
    String status = row.status().name();
    Map<String, Object> payload = new LinkedHashMap<>();
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "policePhoneId", row.policePhoneId());
    payload.put("geometryHash", sourceHash("path", row.id().toString(), row.version(), status));
    payload.put("geometry", Map.of("type", "LineString", "coordinates", row.geometry()));
    payload.put("segments", row.segments().stream().map(this::segmentPayload).toList());
    payload.put("excludedPoints", row.excludedPoints().stream().map(this::excludedPointPayload).toList());
    return sourceRow(
        "path",
        "S3-1",
        row.id().toString(),
        "board-path-" + row.id(),
        status,
        row.version(),
        row.version(),
        eventId("S3-1", "path", row.id().toString(), row.version()),
        String.valueOf(payload.get("geometryHash")),
        payload);
  }

  private BoardSourceRow markerRow(MarkerView row) {
    String status = row.status().name();
    Map<String, Object> payload = new LinkedHashMap<>();
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "accountId", row.accountId());
    putUuid(payload, "policePhoneId", row.policePhoneId());
    payload.put("markerType", row.type().name());
    if (row.supportRequestType() != null) {
      payload.put("supportRequestType", row.supportRequestType().name());
    }
    payload.put("source", row.source().name());
    payload.put("memo", row.memo());
    payload.put("occurredAt", row.occurredAt());
    payload.put("geometryHash", sourceHash("marker", row.id().toString(), row.version(), status));
    payload.put("geometry", MarkerGeoJsonPoint.from(row.location()));
    payload.put("photoSummary", row.photoSummary().stream().map(this::photoSummaryPayload).toList());
    return sourceRow(
        "marker",
        "S5",
        row.id().toString(),
        "board-marker-" + row.id(),
        status,
        row.version(),
        row.version(),
        eventId("S5", "marker", row.id().toString(), row.version()),
        String.valueOf(payload.get("geometryHash")),
        payload);
  }

  private BoardSourceRow opRow(String slot, String rowPrefix, OperationalPeriodRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("opId", row.opId().toString());
    payload.put("incidentId", row.incidentId().toString());
    payload.put("sequenceNo", row.sequenceNo());
    payload.put("startedAt", row.startedAt());
    payload.put("endedAt", row.endedAt());
    payload.put("reason", row.reason());
    return sourceRow(
        slot,
        "S8",
        row.opId().toString(),
        rowPrefix + row.opId(),
        row.status(),
        row.version(),
        row.sequenceNo(),
        eventId("S8", slot.replace('_', '-'), row.opId().toString(), row.version()),
        sourceHash(slot, row.opId().toString(), row.version(), row.status()),
        payload);
  }

  private BoardSourceRow handoverMemoRow(HandoverMemoRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    putUuid(payload, "opId", row.opId());
    payload.put("targetType", row.targetType());
    putUuid(payload, "targetId", row.targetId());
    payload.put("content", row.content());
    putUuid(payload, "createdByAccountId", row.createdByAccountId());
    payload.put("createdAt", row.createdAt());
    return sourceRow(
        "handover_memo",
        "S8",
        row.memoId().toString(),
        "board-handover-memo-" + row.memoId(),
        "ACTIVE",
        row.version(),
        row.version(),
        eventId("S8", "handover-memo", row.memoId().toString(), row.version()),
        sourceHash("handover_memo", row.memoId().toString(), row.version(), "ACTIVE"),
        payload);
  }

  private BoardSourceRow searchHistorySummaryRow(SearchHistorySummaryRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "dutyShiftId", row.dutyShiftId());
    payload.put("summaryText", row.content());
    payload.put("sourceReadiness", row.sourceReadiness());
    payload.put("generatedAt", row.generatedAt());
    return sourceRow(
        "search_history_summary",
        "S8",
        row.summaryId().toString(),
        "board-ai-summary-" + row.summaryId(),
        row.status(),
        row.version(),
        row.version(),
        eventId("S8", "search-history-summary", row.summaryId().toString(), row.version()),
        row.sourceHash(),
        payload);
  }

  private BoardSourceRow incidentTerminalRow(
      IncidentRecord incident, RetentionPurgeStatusSnapshot purgeStatus) {
    String terminalStatus = terminalStatus(purgeStatus);
    String closedStatus = closedStatus(terminalStatus);
    String writeDisabledReason = writeDisabledReason(terminalStatus);
    String localPurgeState = localPurgeState(purgeStatus);
    long version = Math.max(incident.getVersion(), purgeStatus == null ? 0 : purgeStatus.version());
    String sourceSpec = purgeStatus == null ? "S1-1" : "S1-3";
    String incidentId = incident.getId().toString();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", incidentId);
    payload.put("terminalStatus", terminalStatus);
    payload.put("closedStatus", closedStatus);
    payload.put("closedAt", incident.getClosedAt());
    payload.put("writeDisabledReason", writeDisabledReason);
    payload.put("localPurgeState", localPurgeState);

    return sourceRow(
        "incident_terminal",
        sourceSpec,
        incidentId,
        "board-incident-terminal-" + incidentId,
        terminalStatus,
        version,
        version,
        eventId(sourceSpec, "incident-terminal", incidentId, version),
        sourceHash("incident_terminal", incidentId, version, terminalStatus + "|" + localPurgeState),
        payload);
  }

  private Map<String, Object> segmentPayload(SearchPathSegment segment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", segment.id());
    payload.put("version", segment.version());
    payload.put("movementType", segment.movementType().name());
    payload.put("movementTypeSource", segment.movementTypeSource().name());
    payload.put("startIndex", segment.startIndex());
    payload.put("endIndex", segment.endIndex());
    payload.put("startPointId", segment.startPointId());
    payload.put("endPointId", segment.endPointId());
    putUuid(payload, "correctedByAccountId", segment.correctedByAccountId());
    payload.put("correctedAt", segment.correctedAt());
    return payload;
  }

  private Map<String, Object> excludedPointPayload(PathExcludedPoint point) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("pointId", point.pointId());
    payload.put("reason", point.reason());
    payload.put("clientTs", point.clientTs());
    return payload;
  }

  private Map<String, Object> photoSummaryPayload(MarkerPhotoSummary photo) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("photoId", photo.photoId().toString());
    payload.put("status", photo.status());
    payload.put("version", photo.version());
    payload.put("contentType", photo.contentType());
    payload.put("sizeBytes", photo.sizeBytes());
    payload.put("attachedAt", photo.attachedAt());
    return payload;
  }

  private static BoardSourceRow sourceRow(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String status,
      long version,
      long sequence,
      String latestEventId,
      String sourceHash,
      Map<String, Object> payload) {
    return new BoardSourceRow(
        slot,
        sourceSpec,
        sourceResponseId,
        boardRowId,
        status,
        version,
        sequence,
        latestEventId,
        sourceHash,
        payload);
  }

  private static List<UUID> selectedOpIds(List<UUID> requestedOpIds, UUID activeOpId) {
    if (!requestedOpIds.isEmpty()) {
      return List.copyOf(new LinkedHashSet<>(requestedOpIds));
    }
    return activeOpId == null ? List.of() : List.of(activeOpId);
  }

  private static SearchAreaFilters searchAreaFilters(UUID opId, Long minVersion) {
    return new SearchAreaFilters(null, opId, null, minVersion, null, false);
  }

  private static void putUuid(Map<String, Object> payload, String key, UUID value) {
    if (value != null) {
      payload.put(key, value.toString());
    }
  }

  private RetentionPurgeStatusSnapshot purgeStatusOf(UUID incidentId) {
    RetentionPurgeStatus status = retentionPurgeStatus.getIfAvailable();
    if (status == null) {
      return null;
    }
    return status.byIncident(incidentId).orElse(null);
  }

  private static String terminalStatus(RetentionPurgeStatusSnapshot purgeStatus) {
    if (purgeStatus == null) {
      return "CLOSED";
    }
    if (purgeStatus.localPurgeState() == LocalPurgeState.LOCAL_PURGED) {
      return "PURGED";
    }
    return "PURGE_PENDING";
  }

  private static String closedStatus(String terminalStatus) {
    return switch (terminalStatus) {
      case "PURGED" -> "purged";
      case "PURGE_PENDING" -> "purge_pending";
      default -> "closed";
    };
  }

  private static String writeDisabledReason(String terminalStatus) {
    return "PURGED".equals(terminalStatus) ? "purged" : "incident_closed";
  }

  private static String localPurgeState(RetentionPurgeStatusSnapshot purgeStatus) {
    if (purgeStatus == null) {
      return "not_started";
    }
    return switch (purgeStatus.localPurgeState()) {
      case NOT_STARTED -> "not_started";
      case PURGE_PENDING -> "queued";
      case WAITING_FOR_SYNC -> "in_progress";
      case LOCAL_PURGED -> "completed";
      case FAILED_RETRYABLE -> "failed_retryable";
    };
  }

  private static String geometryHash(List<BoardSourceRow> rows) {
    List<String> hashes =
        rows.stream()
            .filter(row -> List.of("overall_search_area", "area", "path", "marker").contains(row.slot()))
            .map(row -> String.valueOf(row.payload().get("geometryHash")))
            .filter(value -> value != null && !value.isBlank() && !"null".equals(value))
            .sorted()
            .toList();
    if (hashes.isEmpty()) {
      return EMPTY_BOARD_GEOMETRY_HASH;
    }
    return hash("board-geometry|" + String.join("|", hashes));
  }

  private static String sourceHash(String slot, String id, long version, String status) {
    return hash(slot + "|" + id + "|" + status + "|" + version);
  }

  private static String eventId(String sourceSpec, String sourceType, String id, long version) {
    return "evt-" + sourceSpec.toLowerCase() + "-" + sourceType + "-" + id + "-v" + version;
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
