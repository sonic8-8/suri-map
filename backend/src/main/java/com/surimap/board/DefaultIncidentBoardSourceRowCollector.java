package com.surimap.board;

import com.surimap.api.service.path.SearchPathService;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import com.surimap.domain.path.PathExcludedPoint;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.AssignmentRow;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.notification.query.MarkerNotificationToastQuery;
import com.surimap.marker.notification.query.MarkerNotificationToastRow;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerView;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.policephone.PolicePhoneFreshnessStatus;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import com.surimap.retention.purge.IncidentDataPurgeRun;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.IncidentDataPurgeStore;
import com.surimap.retention.purge.LocalPurgeState;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIncidentBoardSourceRowCollector implements IncidentBoardSourceRowCollector {

  private static final String EMPTY_BOARD_GEOMETRY_HASH = "hash-board-geometry-current";

  private final ObjectProvider<SearchAreaQuery> searchAreaQuery;
  private final ObjectProvider<SearchAreaAssignmentQuery> searchAreaAssignmentQuery;
  private final ObjectProvider<SearchPathService> searchPathService;
  private final ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery;
  private final MarkerQuery markerQuery;
  private final PackageBadgeBoardAssembler packageBadgeBoardAssembler;
  private final OperationalPeriodQuery operationalPeriodQuery;
  private final HandoverMemoQuery handoverMemoQuery;
  private final SearchHistorySummaryMapper searchHistorySummaryMapper;
  private final ObjectProvider<IncidentMapper> incidentMapper;
  private final ObjectProvider<IncidentReadMapper> incidentReadMapper;
  private final ObjectProvider<IncidentDataPurgeStore> purgeStore;
  private final ObjectProvider<MarkerNotificationToastQuery> toastQuery;
  private final ObjectProvider<DutyShiftMapper> dutyShiftMapper;

  public DefaultIncidentBoardSourceRowCollector(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchPathService> searchPathService,
      ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery,
      MarkerQuery markerQuery,
      OfflinePackageInstallationQuery offlinePackageInstallationQuery,
      OperationalPeriodQuery operationalPeriodQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper) {
    this(
        searchAreaQuery,
        searchPathService,
        policePhoneFreshnessQuery,
        markerQuery,
        offlinePackageInstallationQuery,
        operationalPeriodQuery,
        handoverMemoQuery,
        searchHistorySummaryMapper,
        null,
        null,
        null,
        null,
        null);
  }

  public DefaultIncidentBoardSourceRowCollector(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchPathService> searchPathService,
      ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery,
      MarkerQuery markerQuery,
      OfflinePackageInstallationQuery offlinePackageInstallationQuery,
      OperationalPeriodQuery operationalPeriodQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper,
      ObjectProvider<IncidentMapper> incidentMapper,
      ObjectProvider<IncidentReadMapper> incidentReadMapper,
      ObjectProvider<IncidentDataPurgeStore> purgeStore,
      ObjectProvider<MarkerNotificationToastQuery> toastQuery) {
    this(
        searchAreaQuery,
        searchPathService,
        policePhoneFreshnessQuery,
        markerQuery,
        offlinePackageInstallationQuery,
        operationalPeriodQuery,
        handoverMemoQuery,
        searchHistorySummaryMapper,
        incidentMapper,
        incidentReadMapper,
        purgeStore,
        toastQuery,
        null,
        null);
  }

  public DefaultIncidentBoardSourceRowCollector(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchPathService> searchPathService,
      ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery,
      MarkerQuery markerQuery,
      OfflinePackageInstallationQuery offlinePackageInstallationQuery,
      OperationalPeriodQuery operationalPeriodQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper,
      ObjectProvider<IncidentMapper> incidentMapper,
      ObjectProvider<IncidentReadMapper> incidentReadMapper,
      ObjectProvider<IncidentDataPurgeStore> purgeStore,
      ObjectProvider<MarkerNotificationToastQuery> toastQuery,
      ObjectProvider<SearchAreaAssignmentQuery> searchAreaAssignmentQuery) {
    this(
        searchAreaQuery,
        searchPathService,
        policePhoneFreshnessQuery,
        markerQuery,
        offlinePackageInstallationQuery,
        operationalPeriodQuery,
        handoverMemoQuery,
        searchHistorySummaryMapper,
        incidentMapper,
        incidentReadMapper,
        purgeStore,
        toastQuery,
        null,
        searchAreaAssignmentQuery);
  }

  @Autowired
  public DefaultIncidentBoardSourceRowCollector(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchPathService> searchPathService,
      ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery,
      MarkerQuery markerQuery,
      OfflinePackageInstallationQuery offlinePackageInstallationQuery,
      OperationalPeriodQuery operationalPeriodQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper,
      ObjectProvider<IncidentMapper> incidentMapper,
      ObjectProvider<IncidentReadMapper> incidentReadMapper,
      ObjectProvider<IncidentDataPurgeStore> purgeStore,
      ObjectProvider<MarkerNotificationToastQuery> toastQuery,
      ObjectProvider<DutyShiftMapper> dutyShiftMapper,
      ObjectProvider<SearchAreaAssignmentQuery> searchAreaAssignmentQuery) {
    this.searchAreaQuery = searchAreaQuery;
    this.searchAreaAssignmentQuery = searchAreaAssignmentQuery;
    this.searchPathService = searchPathService;
    this.policePhoneFreshnessQuery = policePhoneFreshnessQuery;
    this.markerQuery = Objects.requireNonNull(markerQuery, "markerQuery must not be null");
    this.packageBadgeBoardAssembler =
        new PackageBadgeBoardAssembler(
            Objects.requireNonNull(
                offlinePackageInstallationQuery,
                "offlinePackageInstallationQuery must not be null"));
    this.operationalPeriodQuery =
        Objects.requireNonNull(operationalPeriodQuery, "operationalPeriodQuery must not be null");
    this.handoverMemoQuery =
        Objects.requireNonNull(handoverMemoQuery, "handoverMemoQuery must not be null");
    this.searchHistorySummaryMapper =
        Objects.requireNonNull(
            searchHistorySummaryMapper, "searchHistorySummaryMapper must not be null");
    this.incidentMapper = incidentMapper;
    this.incidentReadMapper = incidentReadMapper;
    this.purgeStore = purgeStore;
    this.toastQuery = toastQuery;
    this.dutyShiftMapper = dutyShiftMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public IncidentBoardSourceRowSnapshot collect(BoardSourceRowContext context) {
    Objects.requireNonNull(context, "context must not be null");
    UUID activeOpId =
        operationalPeriodQuery
            .current(context.incidentId())
            .map(CurrentOpResult::opId)
            .orElse(null);
    List<UUID> selectedOpIds = selectedOpIds(context.requestedOpIds(), activeOpId);

    List<BoardSourceRow> rows = new ArrayList<>();
    Optional<BoardSourceRow> terminalRow = incidentTerminalRow(context.incidentId());
    collectSearchAreaRows(context, selectedOpIds, rows);
    collectPathRows(context, selectedOpIds, rows);
    collectPolicePhoneFreshnessRows(context, rows, terminalRow.isPresent());
    collectMarkerRows(context, markerOpIds(context, activeOpId, selectedOpIds), rows);
    collectToastRows(context, rows);
    collectPackageRows(context, rows);
    collectIncidentTerminalRow(context, rows, terminalRow);
    collectOperationalPeriodRows(context, rows);
    collectHandoverMemoRows(context, selectedOpIds, rows);
    collectHandoverStatusRows(context, selectedOpIds, rows);
    collectSearchHistorySummaryRows(context, selectedOpIds, rows);

    return new IncidentBoardSourceRowSnapshot(activeOpId, selectedOpIds, geometryHash(rows), rows);
  }

  private void collectSearchAreaRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    SearchAreaQuery query = searchAreaQuery.getIfAvailable();
    if (query == null) {
      return;
    }
    Map<String, AssignmentRow> activeAssignmentsByAccountId =
        activeIncidentAssignmentsByAccountId(context.incidentId());
    if (context.includes("overall_search_area")) {
      query.overallOf(context.incidentId()).map(this::overallSearchAreaRow).ifPresent(rows::add);
    }
    if (context.includes("area")) {
      if (selectedOpIds.isEmpty()) {
        List<SearchAreaRow> areaRows =
            query.byIncident(context.incidentId(), searchAreaFilters(null, null)).areas();
        Map<UUID, List<SearchAreaAssignmentRow>> assignmentsByAreaId =
            assignmentsByAreaId(
                areaRows.stream().map(SearchAreaRow::opId).filter(Objects::nonNull).toList());
        areaRows.stream()
            .filter(row -> !"OVERALL".equals(row.areaLevel()))
            .map(
                row ->
                    areaRow(
                        row,
                        assignmentsByAreaId.getOrDefault(row.id(), List.of()),
                        activeAssignmentsByAccountId))
            .forEach(rows::add);
      } else {
        selectedOpIds.forEach(
            opId -> {
              List<SearchAreaRow> areaRows =
                  query.byOp(opId, searchAreaFilters(null, null)).areas();
              Map<UUID, List<SearchAreaAssignmentRow>> assignmentsByAreaId =
                  assignmentsByAreaId(List.of(opId));
              areaRows.stream()
                  .filter(row -> !"OVERALL".equals(row.areaLevel()))
                  .map(
                      row ->
                          areaRow(
                              row,
                              assignmentsByAreaId.getOrDefault(row.id(), List.of()),
                              activeAssignmentsByAccountId))
                  .forEach(rows::add);
            });
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
      queryPaths(service, context.incidentId(), null).getPaths().stream()
          .map(this::pathRow)
          .forEach(rows::add);
      return;
    }
    selectedOpIds.forEach(
        opId ->
            queryPaths(service, context.incidentId(), opId).getPaths().stream()
                .map(this::pathRow)
                .forEach(rows::add));
  }

  private SearchPathQueryServiceResponse queryPaths(
      SearchPathService service, UUID incidentId, UUID opId) {
    return service.query(
        SearchPathQueryServiceRequest.builder().incidentId(incidentId).opId(opId).build());
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

  private void collectPolicePhoneFreshnessRows(
      BoardSourceRowContext context, List<BoardSourceRow> rows, boolean terminalIncident) {
    if (!context.includes("police_phone_freshness") || terminalIncident) {
      return;
    }
    PolicePhoneFreshnessQuery query = policePhoneFreshnessQuery.getIfAvailable();
    if (query == null) {
      return;
    }
    query.byIncident(context.incidentId()).stream().map(this::freshnessRow).forEach(rows::add);
  }

  private void collectPackageRows(BoardSourceRowContext context, List<BoardSourceRow> rows) {
    if (!context.includes("package_badge")) {
      return;
    }
    rows.addAll(packageBadgeBoardAssembler.sourceRowsByIncident(context.incidentId().toString()));
  }

  private void collectToastRows(BoardSourceRowContext context, List<BoardSourceRow> rows) {
    if (!context.includes("toast")) {
      return;
    }
    MarkerNotificationToastQuery query = toastQuery == null ? null : toastQuery.getIfAvailable();
    if (query == null) {
      return;
    }
    query.byIncident(context.incidentId()).stream().map(this::toastRow).forEach(rows::add);
  }

  private void collectIncidentTerminalRow(
      BoardSourceRowContext context,
      List<BoardSourceRow> rows,
      Optional<BoardSourceRow> terminalRow) {
    if (context.includes("incident_terminal")) {
      terminalRow.ifPresent(rows::add);
    }
  }

  private void collectOperationalPeriodRows(
      BoardSourceRowContext context, List<BoardSourceRow> rows) {
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

  private void collectHandoverStatusRows(
      BoardSourceRowContext context, List<UUID> selectedOpIds, List<BoardSourceRow> rows) {
    if (!context.includes("handover_status") || selectedOpIds.isEmpty()) {
      return;
    }
    UUID opId = selectedOpIds.get(0);
    OperationalPeriodRow opRow =
        operationalPeriodQuery.list(context.incidentId()).stream()
            .filter(row -> row.opId().equals(opId))
            .findFirst()
            .orElse(null);
    if (opRow == null) {
      return;
    }
    List<HandoverMemoRow> memos =
        handoverMemoQuery.byContext(context.incidentId(), opId, "OPERATIONAL_PERIOD", opId);
    rows.add(handoverStatusRow(context.incidentId(), opRow, memos));
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

  private BoardSourceRow overallSearchAreaRow(OverallSearchAreaResult row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", row.incidentId().toString());
    payload.put("areaLevel", "OVERALL");
    payload.put("colorToken", row.colorToken());
    payload.put(
        "geometryHash",
        sourceHash("overall_search_area", row.id().toString(), row.version(), row.status()));
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

  private BoardSourceRow areaRow(
      SearchAreaRow row,
      List<SearchAreaAssignmentRow> assignments,
      Map<String, AssignmentRow> activeAssignmentsByAccountId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    long version = areaRowVersion(row, assignments);
    String latestEventId = areaLatestEventId(row, assignments);
    Map<String, String> activePolicePhoneIdsByAccountId =
        activePolicePhoneIdsByAccountId(row.incidentId());
    payload.put("incidentId", row.incidentId().toString());
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "parentAreaId", row.parentAreaId());
    payload.put("areaLevel", row.areaLevel());
    payload.put("colorToken", row.colorToken());
    payload.put(
        "geometryHash", sourceHash("area", row.id().toString(), row.version(), row.status()));
    payload.put("geometry", row.geometry());
    payload.put("bbox", row.bbox());
    payload.put("updatedAt", row.updatedAt());
    payload.put("historyCount", row.historyCount());
    payload.put(
        "assignedAccounts",
        assignments.stream()
            .map(
                assignment ->
                    assignmentPayload(
                        assignment,
                        row.incidentId(),
                        row.opId(),
                        activeAssignmentsByAccountId,
                        activePolicePhoneIdsByAccountId))
            .toList());
    return sourceRow(
        "area",
        "S2",
        row.id().toString(),
        "board-area-" + row.id(),
        row.status(),
        version,
        version,
        latestEventId,
        sourceHash(
            "area", row.id() + "|" + assignmentFingerprint(assignments), version, row.status()),
        payload);
  }

  private static long areaRowVersion(SearchAreaRow row, List<SearchAreaAssignmentRow> assignments) {
    return Math.max(
        row.version(),
        assignments.stream()
            .mapToLong(SearchAreaAssignmentRow::version)
            .max()
            .orElse(row.version()));
  }

  private static String areaLatestEventId(
      SearchAreaRow row, List<SearchAreaAssignmentRow> assignments) {
    SearchAreaAssignmentRow latestAssignment =
        assignments.stream()
            .max(java.util.Comparator.comparingLong(SearchAreaAssignmentRow::version))
            .orElse(null);
    if (latestAssignment != null && latestAssignment.version() > row.version()) {
      return eventId(
          "S2",
          "search-area-assignment",
          latestAssignment.id().toString(),
          latestAssignment.version());
    }
    return eventId("S2", "area", row.id().toString(), row.version());
  }

  private static String assignmentFingerprint(List<SearchAreaAssignmentRow> assignments) {
    if (assignments.isEmpty()) {
      return "no-assignments";
    }
    return String.join(
        "|",
        assignments.stream()
            .map(
                row ->
                    row.id()
                        + ":"
                        + row.assignedAccountId()
                        + ":"
                        + row.status()
                        + ":"
                        + row.version())
            .sorted()
            .toList());
  }

  private Map<UUID, List<SearchAreaAssignmentRow>> assignmentsByAreaId(List<UUID> opIds) {
    SearchAreaAssignmentQuery query =
        searchAreaAssignmentQuery == null ? null : searchAreaAssignmentQuery.getIfAvailable();
    if (query == null || opIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<SearchAreaAssignmentRow>> assignmentsByAreaId = new LinkedHashMap<>();
    opIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .forEach(
            opId ->
                query.byOp(opId).stream()
                    .filter(row -> "ACTIVE".equals(row.status()))
                    .forEach(
                        row ->
                            assignmentsByAreaId
                                .computeIfAbsent(row.searchAreaId(), ignored -> new ArrayList<>())
                                .add(row)));
    return assignmentsByAreaId;
  }

  private Map<String, Object> assignmentPayload(
      SearchAreaAssignmentRow row,
      UUID incidentId,
      UUID opId,
      Map<String, AssignmentRow> activeAssignmentsByAccountId,
      Map<String, String> activePolicePhoneIdsByAccountId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    AssignmentRow incidentAssignment =
        activeAssignmentsByAccountId.get(row.assignedAccountId().toString());
    String displayName = assignmentDisplayName(incidentAssignment);
    String activeDutyPhoneId =
        activeDutyShiftPolicePhoneId(incidentId, opId, row.assignedAccountId()).orElse(null);
    payload.put("assignmentId", row.id().toString());
    payload.put("accountId", row.assignedAccountId().toString());
    payload.put("displayName", displayName);
    payload.put("accountDisplayName", displayName);
    payload.put(
        "policePhoneId",
        activeDutyPhoneId != null
            ? activeDutyPhoneId
            : fallbackPolicePhoneId(
                opId, row.assignedAccountId(), activePolicePhoneIdsByAccountId));
    payload.put(
        "accountType", incidentAssignment == null ? null : incidentAssignment.getAccountType());
    payload.put(
        "organizationType",
        incidentAssignment == null ? null : incidentAssignment.getOrganizationType());
    payload.put(
        "incidentRole", incidentAssignment == null ? null : incidentAssignment.getIncidentRole());
    putUuid(payload, "assignedByAccountId", row.assignedByAccountId());
    payload.put("assignedAt", row.assignedAt());
    payload.put("status", row.status());
    payload.put("version", row.version());
    return payload;
  }

  private Optional<String> activeDutyShiftPolicePhoneId(
      UUID incidentId, UUID opId, UUID accountId) {
    DutyShiftMapper mapper = dutyShiftMapper == null ? null : dutyShiftMapper.getIfAvailable();
    if (mapper == null || incidentId == null || opId == null || accountId == null) {
      return Optional.empty();
    }
    return mapper.findByFilters(incidentId, opId, null, accountId, "ACTIVE").stream()
        .map(shift -> shift.getPolicePhoneId() == null ? null : shift.getPolicePhoneId().toString())
        .filter(Objects::nonNull)
        .findFirst();
  }

  private String fallbackPolicePhoneId(
      UUID opId, UUID accountId, Map<String, String> activePolicePhoneIdsByAccountId) {
    if (dutyShiftMapper != null && dutyShiftMapper.getIfAvailable() != null && opId != null) {
      return null;
    }
    return activePolicePhoneIdsByAccountId.get(accountId.toString());
  }

  private Map<String, AssignmentRow> activeIncidentAssignmentsByAccountId(UUID incidentId) {
    IncidentReadMapper query =
        incidentReadMapper == null ? null : incidentReadMapper.getIfAvailable();
    if (query == null) {
      return Map.of();
    }

    Map<String, AssignmentRow> assignmentsByAccountId = new LinkedHashMap<>();
    query.findActiveAssignmentsByIncidentId(incidentId).stream()
        .filter(Objects::nonNull)
        .filter(row -> row.getAccountId() != null && !row.getAccountId().isBlank())
        .forEach(row -> assignmentsByAccountId.putIfAbsent(row.getAccountId(), row));
    return assignmentsByAccountId;
  }

  private Map<String, String> activePolicePhoneIdsByAccountId(UUID incidentId) {
    IncidentReadMapper query =
        incidentReadMapper == null ? null : incidentReadMapper.getIfAvailable();
    if (query == null || incidentId == null) {
      return Map.of();
    }

    Map<String, String> policePhoneIdsByAccountId = new LinkedHashMap<>();
    query.findActiveAssignmentTargetsByIncidentId(incidentId).stream()
        .filter(Objects::nonNull)
        .filter(row -> row.getAccountId() != null && !row.getAccountId().isBlank())
        .filter(row -> row.getPolicePhoneId() != null && !row.getPolicePhoneId().isBlank())
        .forEach(
            row ->
                policePhoneIdsByAccountId.putIfAbsent(row.getAccountId(), row.getPolicePhoneId()));
    return policePhoneIdsByAccountId;
  }

  private static String assignmentDisplayName(AssignmentRow assignment) {
    if (assignment == null) {
      return "사건 배정 계정";
    }

    String accountDisplayName = normalizedLabel(assignment.getAccountDisplayName());
    if (accountDisplayName != null) {
      return accountDisplayName;
    }

    List<String> values =
        List.of(
            normalizedLabel(formatOrganizationType(assignment.getOrganizationType())),
            normalizedLabel(formatAccountType(assignment.getAccountType())),
            normalizedLabel(formatIncidentRole(assignment.getIncidentRole())));
    String fallback = String.join(" · ", values.stream().filter(Objects::nonNull).toList());
    return fallback.isBlank() ? "사건 배정 계정" : fallback;
  }

  private static String formatAccountType(String value) {
    if ("TEAM".equals(value)) return "팀 계정";
    if ("PATROL_CAR".equals(value)) return "순찰차 계정";
    if ("COMMAND".equals(value)) return "지휘 계정";
    return value == null || value.isBlank() ? null : "기타 계정";
  }

  private static String formatOrganizationType(String value) {
    if ("MISSING_TEAM".equals(value)) return "실종팀";
    if ("SUPPORT_UNIT".equals(value)) return "지원부대";
    if ("POLICE_SUBSTATION".equals(value)) return "지구대/파출소";
    return value == null || value.isBlank() ? null : "기타 조직";
  }

  private static String formatIncidentRole(String value) {
    if ("MEMBER".equals(value)) return "현장 대원";
    if ("FIELD_COMMANDER".equals(value)) return "현장 지휘";
    if ("INCIDENT_COMMANDER".equals(value)) return "사건 지휘";
    return value == null || value.isBlank() ? null : "사건 담당";
  }

  private static String normalizedLabel(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private BoardSourceRow pathRow(SearchPathQueryRowServiceResponse row) {
    String status = row.getStatus().name();
    Map<String, Object> payload = new LinkedHashMap<>();
    putUuid(payload, "opId", row.getOpId());
    putUuid(payload, "dutyShiftId", row.getDutyShiftId());
    putUuid(payload, "accountId", row.getAccountId());
    putUuid(payload, "policePhoneId", row.getPolicePhoneId());
    payload.put(
        "geometryHash", sourceHash("path", row.getId().toString(), row.getVersion(), status));
    payload.put("geometry", Map.of("type", "LineString", "coordinates", row.getGeometry()));
    payload.put("segments", row.getSegments().stream().map(this::segmentPayload).toList());
    payload.put(
        "excludedPoints",
        row.getExcludedPoints().stream().map(this::excludedPointPayload).toList());
    return sourceRow(
        "path",
        "S3-1",
        row.getId().toString(),
        "board-path-" + row.getId(),
        status,
        row.getVersion(),
        row.getVersion(),
        eventId("S3-1", "path", row.getId().toString(), row.getVersion()),
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
    payload.put(
        "photoSummary", row.photoSummary().stream().map(this::photoSummaryPayload).toList());
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

  private BoardSourceRow handoverStatusRow(
      UUID incidentId, OperationalPeriodRow opRow, List<HandoverMemoRow> memos) {
    String handoverStatus = memos.isEmpty() ? "NEEDS_MEMO" : "READY";
    long version =
        Math.max(
            opRow.version(),
            memos.stream().mapToLong(HandoverMemoRow::version).max().orElse(opRow.version()));
    String latestEventId =
        memos.stream()
            .max(java.util.Comparator.comparingLong(HandoverMemoRow::version))
            .map(memo -> eventId("S8", "handover-memo", memo.memoId().toString(), memo.version()))
            .orElse(eventId("S8", "handover-status", opRow.opId().toString(), version));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", incidentId.toString());
    payload.put("opId", opRow.opId().toString());
    payload.put("handoverStatus", handoverStatus);
    payload.put("handoverMemoCount", memos.size());
    return sourceRow(
        "handover_status",
        "S8",
        incidentId.toString(),
        "board-handover-status-" + incidentId,
        handoverStatus,
        version,
        opRow.sequenceNo(),
        latestEventId,
        sourceHash(
            "handover_status",
            incidentId + ":" + opRow.opId() + ":" + memos.size(),
            version,
            handoverStatus),
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

  private Optional<BoardSourceRow> incidentTerminalRow(UUID incidentId) {
    IncidentMapper query = incidentMapper == null ? null : incidentMapper.getIfAvailable();
    if (query == null) {
      return Optional.empty();
    }
    return query
        .findByIncidentId(incidentId)
        .filter(incident -> !"OPEN".equals(incident.getStatus()))
        .map(incident -> incidentTerminalRow(incident, purgeRun(incidentId)));
  }

  private IncidentDataPurgeRun purgeRun(UUID incidentId) {
    IncidentDataPurgeStore store = purgeStore == null ? null : purgeStore.getIfAvailable();
    if (store == null) {
      return null;
    }
    return store.findByIncidentId(incidentId).orElse(null);
  }

  private BoardSourceRow incidentTerminalRow(
      IncidentRecord incident, IncidentDataPurgeRun purgeRun) {
    String terminalStatus = terminalStatus(incident, purgeRun);
    long version =
        purgeRun == null
            ? incident.getVersion()
            : Math.max(incident.getVersion(), purgeRun.version());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", incident.getId().toString());
    payload.put("terminalStatus", terminalStatus);
    payload.put("closedStatus", closedStatus(terminalStatus));
    payload.put(
        "closedAt", incident.getClosedAt() == null ? null : incident.getClosedAt().toString());
    payload.put(
        "writeDisabledReason", "PURGED".equals(terminalStatus) ? "purged" : "incident_closed");
    payload.put("localPurgeState", localPurgeState(purgeRun));
    return sourceRow(
        "incident_terminal",
        purgeRun == null ? "S1-1" : "S1-3",
        incident.getId().toString(),
        "board-incident-terminal-" + incident.getId(),
        terminalStatus,
        version,
        version,
        terminalEventId(incident, purgeRun, version),
        sourceHash("incident_terminal", incident.getId().toString(), version, terminalStatus),
        payload);
  }

  private BoardSourceRow toastRow(MarkerNotificationToastRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", row.notificationType());
    payload.put("markerId", row.markerId().toString());
    payload.put("incidentId", row.incidentId().toString());
    putUuid(payload, "opId", row.opId());
    putUuid(payload, "policePhoneId", row.policePhoneId());
    payload.put("createdAt", row.createdAt());
    return sourceRow(
        "toast",
        "S5",
        row.notificationId().toString(),
        "board-toast-" + row.notificationId(),
        row.status(),
        row.version(),
        row.version(),
        row.latestEventId() == null
            ? eventId("S5", "toast", row.notificationId().toString(), row.version())
            : row.latestEventId().toString(),
        sourceHash("toast", row.notificationId().toString(), row.version(), row.status()),
        payload);
  }

  private BoardSourceRow freshnessRow(PolicePhoneFreshnessRow row) {
    String status = row.derivedFreshness().name();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("policePhoneId", row.policePhoneId().toString());
    payload.put("accountId", row.accountId());
    payload.put("accountType", row.accountType().name());
    payload.put("organizationType", row.organizationType().name());
    payload.put("incidentId", row.incidentId().toString());
    putUuid(payload, "opId", row.opId());
    payload.put("freshness", frontendFreshness(row.derivedFreshness()));
    payload.put("freshnessStatus", status);
    payload.put("lastHeartbeatAt", row.lastHeartbeatAt());
    payload.put("lastSyncAt", row.lastSyncAt());
    if (row.lastHeartbeatAt() != null) {
      payload.put(
          "elapsedSeconds", Duration.between(row.lastHeartbeatAt(), Instant.now()).toSeconds());
    }
    return sourceRow(
        "police_phone_freshness",
        "S1-2",
        row.policePhoneId().toString(),
        "board-PolicePhone-freshness-" + row.policePhoneId(),
        status,
        row.version(),
        row.heartbeatSequence(),
        row.latestEventId() == null
            ? eventId(
                "S1-2", "police-phone-freshness", row.policePhoneId().toString(), row.version())
            : row.latestEventId().toString(),
        sourceHash("police_phone_freshness", row.policePhoneId().toString(), row.version(), status),
        payload);
  }

  private Map<String, Object> segmentPayload(SearchPathQuerySegmentServiceResponse segment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", segment.getId());
    payload.put("version", segment.getVersion());
    payload.put("movementType", segment.getMovementType().name());
    payload.put("movementTypeSource", segment.getMovementTypeSource().name());
    payload.put("geometry", Map.of("type", "LineString", "coordinates", segment.getGeometry()));
    payload.put("startedAt", segment.getStartedAt());
    payload.put("endedAt", segment.getEndedAt());
    putUuid(payload, "correctedByAccountId", segment.getCorrectedByAccountId());
    payload.put("correctedAt", segment.getCorrectedAt());
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
    payload.put("photoUrl", photo.photoUrl());
    payload.put("thumbnailUrl", photo.thumbnailUrl());
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

  private List<UUID> markerOpIds(
      BoardSourceRowContext context, UUID activeOpId, List<UUID> selectedOpIds) {
    if (!context.requestedOpIds().isEmpty()) {
      return selectedOpIds;
    }
    if (activeOpId == null) {
      return List.of();
    }

    List<OperationalPeriodRow> opRows = operationalPeriodQuery.list(context.incidentId());
    Optional<OperationalPeriodRow> activeOp =
        opRows.stream().filter(row -> row.opId().equals(activeOpId)).findFirst();
    if (activeOp.isEmpty()) {
      return List.of(activeOpId);
    }

    int activeSequenceNo = activeOp.get().sequenceNo();
    return opRows.stream()
        .filter(row -> row.sequenceNo() <= activeSequenceNo)
        .map(OperationalPeriodRow::opId)
        .distinct()
        .toList();
  }

  private static SearchAreaFilters searchAreaFilters(UUID opId, Long minVersion) {
    return new SearchAreaFilters(null, opId, null, minVersion, null, true);
  }

  private static void putUuid(Map<String, Object> payload, String key, UUID value) {
    if (value != null) {
      payload.put(key, value.toString());
    }
  }

  private static String geometryHash(List<BoardSourceRow> rows) {
    List<String> hashes =
        rows.stream()
            .filter(
                row ->
                    List.of("overall_search_area", "area", "path", "marker").contains(row.slot()))
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

  private static String frontendFreshness(PolicePhoneFreshnessStatus status) {
    return switch (status) {
      case ONLINE -> "normal";
      case STALE -> "stale";
      case LOST -> "lost";
    };
  }

  private static String terminalStatus(IncidentRecord incident, IncidentDataPurgeRun purgeRun) {
    if (purgeRun != null && purgeRun.status() == IncidentDataPurgeStatus.COMPLETED) {
      return "PURGED";
    }
    if (purgeRun != null) {
      return "PURGE_PENDING";
    }
    return "CLOSED";
  }

  private static String closedStatus(String terminalStatus) {
    return switch (terminalStatus) {
      case "PURGED" -> "purged";
      case "PURGE_PENDING" -> "purge_pending";
      case "CLOSED" -> "closed";
      default -> "not_closed";
    };
  }

  private static String localPurgeState(IncidentDataPurgeRun purgeRun) {
    if (purgeRun == null) {
      return "not_started";
    }
    LocalPurgeState state = purgeRun.localPurgeState();
    return switch (state) {
      case NOT_STARTED -> "not_started";
      case PURGE_PENDING -> "queued";
      case WAITING_FOR_SYNC -> "in_progress";
      case LOCAL_PURGED -> "completed";
      case FAILED_RETRYABLE -> "failed_retryable";
    };
  }

  private static String terminalEventId(
      IncidentRecord incident, IncidentDataPurgeRun purgeRun, long version) {
    if (purgeRun == null) {
      return eventId("S1-1", "incident-closed", incident.getId().toString(), version);
    }
    return eventId("S1-3", "incident-purged", purgeRun.purgeRunId().toString(), version);
  }

  private static String eventId(String sourceSpec, String sourceType, String id, long version) {
    return "evt-" + sourceSpec.toLowerCase() + "-" + sourceType + "-" + id + "-v" + version;
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "sha256:"
          + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
