package com.surimap.maparea.fixture;

import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaRow;
import java.time.Instant;
import java.util.List;

/**
 * SearchAreaQuery mock 응답 fixture (S2.json §service_contracts).
 *
 * <p>기준 ID: BoundaryAreaFixtures. 기준 geometry: GeometryFixtures.
 */
public final class SearchAreaQueryFixtures {

  private static final Instant FIXED_AT = Instant.parse("2026-04-28T00:00:00Z");

  private SearchAreaQueryFixtures() {}

  /** overallOf(incidentId) mock 결과 — ACTIVE overall_search_area. */
  public static OverallSearchAreaResult overallSearchAreaResult() {
    return new OverallSearchAreaResult(
        BoundaryAreaFixtures.OVERALL_AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        "ACTIVE",
        BoundaryAreaFixtures.OVERALL_AREA_VERSION,
        GeometryFixtures.validOverallSearchAreaPolygon(),
        GeometryFixtures.BOUNDARY_GEOMETRY_BBOX,
        FIXED_AT);
  }

  /** byIncident / byOp mock 결과 내 ACTIVE search_area row. */
  public static SearchAreaRow activeAreaRow() {
    return new SearchAreaRow(
        BoundaryAreaFixtures.AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        null,
        "ACTIVE",
        null,
        BoundaryAreaFixtures.AREA_CREATED_VERSION,
        GeometryFixtures.validSearchAreaPolygon(),
        GeometryFixtures.AREA_GEOMETRY_BBOX,
        FIXED_AT,
        0L);
  }

  /** byIncident(incidentId, empty filters) mock 결과 — OP1 ACTIVE area 1건. */
  public static SearchAreaCollection byIncidentResult() {
    SearchAreaRow area = activeAreaRow();
    return new SearchAreaCollection(
        BoundaryAreaFixtures.INCIDENT_ID, area.version(), List.of(area));
  }

  /** byOp(op1Id, empty filters) mock 결과 — OP1 ACTIVE area 1건. */
  public static SearchAreaCollection byOp1Result() {
    SearchAreaRow area = activeAreaRow();
    return new SearchAreaCollection(
        BoundaryAreaFixtures.INCIDENT_ID, area.version(), List.of(area));
  }

  /** byOp mock 결과에서 소비자 Lane이 참조하면 안 되는 S2 외 테이블. */
  public static final List<String> FORBIDDEN_S2_OWNER_WRITES =
      List.of(
          "operational_period",
          "search_path",
          "path_segment",
          "marker",
          "photo",
          "notification_delivery",
          "offline_package_manifest");
}
