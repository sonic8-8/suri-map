package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.path.InMemorySearchPathRepository;
import com.surimap.path.NoopPathEventPublisher;
import com.surimap.path.SearchPathAggregate;
import com.surimap.path.SearchPathPoint;
import com.surimap.path.SearchPathService;
import com.surimap.path.validation.GpsPathValidator;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("incident board source row collector")
class IncidentBoardSourceRowCollectorIntegrationTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID OP_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID AREA_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("30000000-0000-4000-8000-000000000099");
  private static final UUID PATH_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
  private static final UUID MARKER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
  private static final UUID PHONE_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
  private static final UUID ACCOUNT_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
  private static final UUID MEMO_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
  private static final UUID SUMMARY_ID = UUID.fromString("90000000-0000-4000-8000-000000000001");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Test
  @DisplayName("collects available source-owner rows for board slots")
  void collects_available_source_owner_rows_for_board_slots() {
    CapturingMarkerQuery markerQuery = new CapturingMarkerQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(new FakeSearchAreaQuery()),
            provider(searchPathService()),
            markerQuery,
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(null));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID,
                List.of(OP_ID),
                List.of(
                    "overall_search_area",
                    "area",
                    "path",
                    "marker",
                    "package_badge",
                    "op_toggle",
                    "op_history",
                    "handover_memo",
                    "search_history_summary"),
                null));

    assertThat(snapshot.activeOpId()).isEqualTo(OP_ID);
    assertThat(snapshot.selectedOpIds()).containsExactly(OP_ID);
    assertThat(snapshot.geometryHash()).isNotBlank();
    assertThat(markerQuery.filters()).extracting(MarkerQueryFilters::opId).containsExactly(OP_ID);
    assertThat(snapshot.sourceRows())
        .extracting(BoardSourceRow::slot)
        .contains(
            "overall_search_area",
            "area",
            "path",
            "marker",
            "package_badge",
            "op_toggle",
            "op_history",
            "handover_memo",
            "search_history_summary");
    assertThat(row(snapshot, "overall_search_area").sourceSpec()).isEqualTo("S2");
    assertThat(row(snapshot, "path").sourceSpec()).isEqualTo("S3-1");
    assertThat(row(snapshot, "marker").sourceSpec()).isEqualTo("S5");
    assertThat(row(snapshot, "package_badge").sourceSpec()).isEqualTo("S7");
    assertThat(row(snapshot, "op_toggle").sourceSpec()).isEqualTo("S8");
    assertThat(row(snapshot, "handover_memo").payload()).containsEntry("content", "memo for next team");
    assertThat(row(snapshot, "search_history_summary").payload())
        .containsEntry("summaryText", "searched ridge trail and checked shelter");
  }

  @Test
  @DisplayName("uses includeSlots before reading optional slot sources")
  void uses_include_slots_before_reading_optional_slot_sources() {
    CapturingMarkerQuery markerQuery = new CapturingMarkerQuery();
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            markerQuery,
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(null));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID, List.of(OP_ID), List.of("marker", "package_badge"), null));

    assertThat(snapshot.sourceRows()).extracting(BoardSourceRow::slot).containsOnly("marker", "package_badge");
    assertThat(markerQuery.filters()).extracting(MarkerQueryFilters::opId).containsExactly(OP_ID);
    assertThat(searchAreaQuery.overallCalls()).isZero();
    assertThat(searchAreaQuery.byIncidentCalls()).isZero();
  }

  @Test
  @DisplayName("sinceVersion does not filter rows from a full board snapshot reload")
  void since_version_does_not_filter_rows_from_full_board_snapshot_reload() {
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            new CapturingMarkerQuery(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(null));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(INCIDENT_ID, List.of(OP_ID), List.of("area"), 1200L));

    assertThat(snapshot.sourceRows()).extracting(BoardSourceRow::slot).containsExactly("area");
    assertThat(row(snapshot, "area").version()).isEqualTo(5L);
    assertThat(searchAreaQuery.byOpFilters())
        .singleElement()
        .extracting(SearchAreaFilters::minVersion)
        .isNull();
  }

  private static BoardSourceRow row(IncidentBoardSourceRowSnapshot snapshot, String slot) {
    return snapshot.sourceRows().stream()
        .filter(row -> row.slot().equals(slot))
        .findFirst()
        .orElseThrow();
  }

  private static SearchPathService searchPathService() {
    InMemorySearchPathRepository repository = new InMemorySearchPathRepository();
    SearchPathAggregate aggregate = new SearchPathAggregate(PATH_ID, INCIDENT_ID, OP_ID, PHONE_ID);
    aggregate.appendAcceptedPoints(
        List.of(
            new SearchPathPoint(
                "pt-001",
                OffsetDateTime.parse("2026-04-28T09:00:00+09:00"),
                new BigDecimal("126.950000"),
                new BigDecimal("37.570000"),
                new BigDecimal("1.0"),
                5),
            new SearchPathPoint(
                "pt-002",
                OffsetDateTime.parse("2026-04-28T09:00:05+09:00"),
                new BigDecimal("126.951000"),
                new BigDecimal("37.571000"),
                new BigDecimal("1.1"),
                5)));
    aggregate.bumpVersion();
    repository.save(aggregate);
    return new SearchPathService(repository, new NoopPathEventPublisher(), new GpsPathValidator());
  }

  private static <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject() {
        return value;
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public Stream<T> stream() {
        return value == null ? Stream.empty() : Stream.of(value);
      }
    };
  }

  private static GeoJsonPolygon polygon() {
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.570000")),
                List.of(new BigDecimal("126.952000"), new BigDecimal("37.570000")),
                List.of(new BigDecimal("126.952000"), new BigDecimal("37.572000")),
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.572000")),
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.570000")))));
  }

  private static class FakeSearchAreaQuery implements SearchAreaQuery {
    @Override
    public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
      return Optional.of(
          new OverallSearchAreaResult(
              OVERALL_AREA_ID,
              incidentId,
              "ACTIVE",
              3L,
              polygon(),
              List.of(
                  new BigDecimal("126.950000"),
                  new BigDecimal("37.570000"),
                  new BigDecimal("126.952000"),
                  new BigDecimal("37.572000")),
              STARTED_AT));
    }

    @Override
    public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
      SearchAreaRow areaRow = areaRow(incidentId);
      return new SearchAreaCollection(
          incidentId, 5L, matchesMinVersion(areaRow, filters) ? List.of(areaRow) : List.of());
    }

    @Override
    public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
      SearchAreaRow areaRow = areaRow(INCIDENT_ID);
      return new SearchAreaCollection(
          INCIDENT_ID, 5L, matchesMinVersion(areaRow, filters) ? List.of(areaRow) : List.of());
    }

    private static boolean matchesMinVersion(SearchAreaRow row, SearchAreaFilters filters) {
      SearchAreaFilters effectiveFilters = filters == null ? SearchAreaFilters.empty() : filters;
      return effectiveFilters.minVersion() == null || row.version() >= effectiveFilters.minVersion();
    }

    private static SearchAreaRow areaRow(UUID incidentId) {
      return new SearchAreaRow(
          AREA_ID,
          incidentId,
          OP_ID,
          null,
          "ACTIVE",
          5L,
          polygon(),
          List.of(
              new BigDecimal("126.950000"),
              new BigDecimal("37.570000"),
              new BigDecimal("126.952000"),
              new BigDecimal("37.572000")),
          STARTED_AT,
          1L);
    }
  }

  private static final class CapturingSearchAreaQuery extends FakeSearchAreaQuery {
    private int overallCalls;
    private int byIncidentCalls;
    private final List<SearchAreaFilters> byIncidentFilters = new ArrayList<>();
    private final List<SearchAreaFilters> byOpFilters = new ArrayList<>();

    @Override
    public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
      overallCalls++;
      return super.overallOf(incidentId);
    }

    @Override
    public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
      byIncidentCalls++;
      byIncidentFilters.add(filters);
      return super.byIncident(incidentId, filters);
    }

    @Override
    public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
      byOpFilters.add(filters);
      return super.byOp(opId, filters);
    }

    private int overallCalls() {
      return overallCalls;
    }

    private int byIncidentCalls() {
      return byIncidentCalls;
    }

    private List<SearchAreaFilters> byIncidentFilters() {
      return List.copyOf(byIncidentFilters);
    }

    private List<SearchAreaFilters> byOpFilters() {
      return List.copyOf(byOpFilters);
    }
  }

  private static final class CapturingMarkerQuery implements MarkerQuery {
    private final List<MarkerQueryFilters> filters = new ArrayList<>();

    @Override
    public MarkerQueryResult byIncident(UUID incidentId, MarkerQueryFilters filters) {
      this.filters.add(filters);
      return new MarkerQueryResult(
          incidentId,
          List.of(
              new MarkerView(
                  MARKER_ID,
                  incidentId,
                  OP_ID,
                  ACCOUNT_ID,
                  PHONE_ID,
                  MarkerType.CLUE,
                  null,
                  MarkerSource.APP,
                  MarkerStatus.ACTIVE,
                  6L,
                  new MarkerGeoJsonPoint(
                          "Point",
                          List.of(new BigDecimal("126.951000"), new BigDecimal("37.571000")))
                      .toPoint(),
                  "clue memo",
                  STARTED_AT,
                  List.of())));
    }

    private List<MarkerQueryFilters> filters() {
      return List.copyOf(filters);
    }
  }

  private static final class FakePackageQuery implements OfflinePackageInstallationQuery {
    @Override
    public List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
      return List.of(
          new OfflinePackageInstallationStatus(
              "pkg-status-test-001",
              incidentId,
              PHONE_ID.toString(),
              "dev-test-phone-01",
              "Test team phone",
              "READY",
              7L,
              701L,
              1,
              true));
    }
  }

  private static final class FakeOperationalPeriodQuery implements OperationalPeriodQuery {
    @Override
    public Optional<CurrentOpResult> current(UUID incidentId) {
      return Optional.of(new CurrentOpResult(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 8L));
    }

    @Override
    public List<OperationalPeriodRow> list(UUID incidentId) {
      return List.of(new OperationalPeriodRow(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 8L));
    }
  }

  private static final class FakeHandoverMemoQuery implements HandoverMemoQuery {
    @Override
    public List<HandoverMemoRow> byContext(UUID incidentId, UUID opId, String targetType, UUID targetId) {
      return List.of(
          new HandoverMemoRow(
              MEMO_ID,
              incidentId,
              opId,
              "OPERATIONAL_PERIOD",
              opId,
              "memo for next team",
              ACCOUNT_ID,
              STARTED_AT,
              9L));
    }
  }

  private static final class FakeSummaryMapper implements SearchHistorySummaryMapper {
    @Override
    public List<SearchHistorySummaryRow> findByOp(
        UUID opId, UUID incidentId, String scopeType, UUID scopeId, UUID dutyShiftId, String status) {
      return List.of(
          new SearchHistorySummaryRow(
              SUMMARY_ID,
              incidentId,
              opId,
              null,
              "READY",
              "searched ridge trail and checked shelter",
              "summary-source-hash-test",
              "READY",
              STARTED_AT,
              10L));
    }

    @Override
    public String sourceFingerprintForScope(UUID opId, UUID dutyShiftId) {
      return "unused";
    }

    @Override
    public String sourceEvidenceForScope(UUID opId, UUID dutyShiftId) {
      return "unused";
    }

    @Override
    public List<SearchHistorySummaryRow> findReadyOrFailedByScopeWithDifferentHash(
        UUID opId, UUID dutyShiftId, String sourceDataHash) {
      return List.of();
    }

    @Override
    public int insertGenerationRequest(
        UUID summaryId,
        UUID opId,
        UUID dutyShiftId,
        String generationStatus,
        String content,
        String sourceDataHash,
        String sourceReadiness,
        UUID requestedByAccountId,
        Instant generatedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
      return 1;
    }

    @Override
    public void updateGenerationResult(
        UUID summaryId,
        String generationStatus,
        String content,
        String sourceReadiness,
        Instant generatedAt,
        Instant updatedAt) {}

    @Override
    public void markStaleByIds(List<UUID> summaryIds, Instant updatedAt) {}
  }
}
