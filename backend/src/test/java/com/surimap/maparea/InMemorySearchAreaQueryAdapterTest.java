package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.query.InMemorySearchAreaQueryAdapter;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemorySearchAreaQueryAdapterTest {

  private InMemorySearchAreaQueryAdapter adapter;
  private Instant now;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-04-28T00:00:00Z");
    adapter = new InMemorySearchAreaQueryAdapter();

    adapter.registerOverall(
        BoundaryAreaFixtures.OVERALL_AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        "ACTIVE",
        BoundaryAreaFixtures.OVERALL_AREA_VERSION,
        GeometryFixtures.validOverallSearchAreaPolygon(),
        now);

    adapter.registerArea(
        BoundaryAreaFixtures.AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "ACTIVE",
        BoundaryAreaFixtures.AREA_CREATED_VERSION,
        GeometryFixtures.validSearchAreaPolygon(),
        now,
        now);
  }

  // --- overallOf ---

  @Test
  void overallOf_registered_returnsPresent() {
    Optional<OverallSearchAreaResult> result = adapter.overallOf(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(result).isPresent();
  }

  @Test
  void overallOf_unknownIncidentId_returnsEmpty() {
    Optional<OverallSearchAreaResult> result = adapter.overallOf(UUID.randomUUID());
    assertThat(result).isEmpty();
  }

  @Test
  void overallOf_geometry_matchesFixture() {
    OverallSearchAreaResult result =
        adapter.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();
    assertThat(result.geometry()).isEqualTo(GeometryFixtures.validOverallSearchAreaPolygon());
  }

  @Test
  void overallOf_bbox_matchesBoundaryGeometryBbox() {
    OverallSearchAreaResult result =
        adapter.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();
    assertThat(result.bbox()).isEqualTo(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX);
  }

  @Test
  void overallOf_fields_matchRegistered() {
    OverallSearchAreaResult result =
        adapter.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();
    assertThat(result.id()).isEqualTo(BoundaryAreaFixtures.OVERALL_AREA_ID);
    assertThat(result.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(result.version()).isEqualTo(BoundaryAreaFixtures.OVERALL_AREA_VERSION);
  }

  // --- byIncident ---

  @Test
  void byIncident_registeredArea_returned() {
    SearchAreaCollection col =
        adapter.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    assertThat(col.areas()).hasSize(1);
  }

  @Test
  void byIncident_unknownIncidentId_returnsEmptyCollection() {
    SearchAreaCollection col = adapter.byIncident(UUID.randomUUID(), SearchAreaFilters.empty());
    assertThat(col.areas()).isEmpty();
  }

  @Test
  void byIncident_areaRow_fieldsMatchRegistered() {
    SearchAreaRow row =
        adapter
            .byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty())
            .areas()
            .get(0);
    assertThat(row.id()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(row.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(row.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(row.status()).isEqualTo("ACTIVE");
    assertThat(row.version()).isEqualTo(BoundaryAreaFixtures.AREA_CREATED_VERSION);
  }

  @Test
  void byIncident_areaRow_geometry_matchesFixture() {
    SearchAreaRow row =
        adapter
            .byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty())
            .areas()
            .get(0);
    assertThat(row.geometry()).isEqualTo(GeometryFixtures.validSearchAreaPolygon());
  }

  @Test
  void byIncident_areaRow_bbox_matchesAreaGeometryBbox() {
    SearchAreaRow row =
        adapter
            .byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty())
            .areas()
            .get(0);
    assertThat(row.bbox()).isEqualTo(GeometryFixtures.AREA_GEOMETRY_BBOX);
  }

  @Test
  void byIncident_sourceVersion_isMaxAreaVersion() {
    SearchAreaCollection col =
        adapter.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    long maxVersion = col.areas().stream().mapToLong(SearchAreaRow::version).max().orElse(0);
    assertThat(col.sourceVersion()).isEqualTo(maxVersion);
  }

  @Test
  void byIncident_includeCancelledFalse_excludesCancelledArea() {
    adapter.registerArea(
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0099"),
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "CANCELLED",
        5L,
        GeometryFixtures.validSearchAreaPolygon(),
        now,
        now);

    SearchAreaCollection col =
        adapter.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    assertThat(col.areas()).noneMatch(r -> "CANCELLED".equals(r.status()));
  }

  @Test
  void byIncident_includeCancelledTrue_includesCancelledArea() {
    UUID cancelledId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0099");
    adapter.registerArea(
        cancelledId,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "CANCELLED",
        5L,
        GeometryFixtures.validSearchAreaPolygon(),
        now,
        now);

    SearchAreaFilters filters = new SearchAreaFilters(null, null, null, null, null, true);
    SearchAreaCollection col = adapter.byIncident(BoundaryAreaFixtures.INCIDENT_ID, filters);
    assertThat(col.areas()).anyMatch(r -> "CANCELLED".equals(r.status()));
  }

  @Test
  void byIncident_statusFilter_returnsOnlyMatchingStatus() {
    adapter.registerArea(
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0088"),
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "COMPLETED",
        4L,
        GeometryFixtures.validSearchAreaPolygon(),
        now,
        now);

    SearchAreaFilters filters =
        new SearchAreaFilters(List.of("ACTIVE"), null, null, null, null, false);
    SearchAreaCollection col = adapter.byIncident(BoundaryAreaFixtures.INCIDENT_ID, filters);
    assertThat(col.areas()).allMatch(r -> "ACTIVE".equals(r.status()));
    assertThat(col.areas()).noneMatch(r -> "COMPLETED".equals(r.status()));
  }

  @Test
  void overallOf_nonActiveStatus_returnsEmpty() {
    InMemorySearchAreaQueryAdapter adapter2 = new InMemorySearchAreaQueryAdapter();
    adapter2.registerOverall(
        BoundaryAreaFixtures.OVERALL_AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        "CANCELLED",
        BoundaryAreaFixtures.OVERALL_AREA_VERSION,
        GeometryFixtures.validOverallSearchAreaPolygon(),
        now);
    assertThat(adapter2.overallOf(BoundaryAreaFixtures.INCIDENT_ID)).isEmpty();
  }

  // --- byOp ---

  @Test
  void byOp_registeredArea_returned() {
    SearchAreaCollection col = adapter.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    assertThat(col.areas()).hasSize(1);
  }

  @Test
  void byOp_unknownOpId_returnsEmptyCollection() {
    SearchAreaCollection col = adapter.byOp(UUID.randomUUID(), SearchAreaFilters.empty());
    assertThat(col.areas()).isEmpty();
  }

  @Test
  void byOp_and_byIncident_shareFieldsForSameArea() {
    SearchAreaRow byOpRow =
        adapter.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty()).areas().get(0);
    SearchAreaRow byIncidentRow =
        adapter
            .byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty())
            .areas()
            .get(0);

    assertThat(byOpRow.id()).isEqualTo(byIncidentRow.id());
    assertThat(byOpRow.status()).isEqualTo(byIncidentRow.status());
    assertThat(byOpRow.version()).isEqualTo(byIncidentRow.version());
    assertThat(byOpRow.geometry()).isEqualTo(byIncidentRow.geometry());
  }

  @Test
  void byOp_includeCancelledFalse_excludesCancelledArea() {
    adapter.registerArea(
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0077"),
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "CANCELLED",
        6L,
        GeometryFixtures.validSearchAreaPolygon(),
        now,
        now);

    SearchAreaCollection col = adapter.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    assertThat(col.areas()).noneMatch(r -> "CANCELLED".equals(r.status()));
  }
}
