package com.surimap.searcharea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.api.controller.searcharea.request.CreateSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.PatchSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.SplitSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaSplitResponse;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SearchAreaApiService MyBatis persistence")
@Tag("integration")
class SearchAreaApiServicePersistenceRedTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000002481");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002481");
  private static final UUID UNIT_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000002483");
  private static final UUID UNIT_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002483");
  private static final UUID PATCH_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000002484");
  private static final UUID PATCH_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002484");
  private static final UUID SPLIT_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000002485");
  private static final UUID SPLIT_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002485");
  private static final UUID OVERALL_SPLIT_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000002486");
  private static final UUID OVERALL_SPLIT_OLD_OP_ID =
      UUID.fromString("70000000-0000-0000-0000-000000002486");
  private static final UUID OVERALL_SPLIT_CURRENT_OP_ID =
      UUID.fromString("70000000-0000-0000-0000-000000002487");
  private static final UUID COMMANDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111112481");
  private static final UUID READ_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000002482");
  private static final UUID READ_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002482");
  private static final UUID READ_OVERALL_ID =
      UUID.fromString("20000000-0000-0000-0000-000000002482");
  private static final UUID READ_UNIT_ID =
      UUID.fromString("30000000-0000-0000-0000-000000002482");
  private static final OffsetDateTime CLIENT_TS =
      OffsetDateTime.parse("2026-05-12T09:00:00+09:00");
  private static final Instant SEEDED_UPDATED_AT = Instant.parse("2026-05-12T01:30:00Z");

  @Autowired private SearchAreaApiService service;

  @Test
  @DisplayName("overall create stores ACTIVE OVERALL search_area tied to active OP")
  void overall_create_persists_search_area_row_with_active_operational_period() {
    seedOpenIncidentWithActiveOperationalPeriod();

    SearchAreaResponse response =
        service.create(overallCreateRequest(), "idem-search-area-persistence-248");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT id,
                   operational_period_id,
                   area_level,
                   status,
                   version,
                   ST_SRID(geometry) AS srid,
                   ST_GeometryType(geometry) AS geometry_type
            FROM search_area
            WHERE id = ?::uuid
            """,
            response.id().toString());

    assertThat(row.get("id")).isEqualTo(response.id());
    assertThat(row.get("operational_period_id")).isEqualTo(OP_ID);
    assertThat(row.get("area_level")).isEqualTo("OVERALL");
    assertThat(row.get("status")).isEqualTo("ACTIVE");
    assertThat(row.get("version")).isEqualTo(1L);
    assertThat(row.get("srid")).isEqualTo(4326);
    assertThat(row.get("geometry_type")).isEqualTo("ST_Polygon");
  }

  @Test
  @DisplayName("overallOf reads seeded ACTIVE OVERALL search_area row from DB")
  void overallOf_reads_seeded_active_overall_from_database() {
    seedOpenIncidentWithActiveOperationalPeriod(READ_INCIDENT_ID, READ_OP_ID);
    seedSearchArea(
        READ_OVERALL_ID,
        READ_OP_ID,
        null,
        "OVERALL",
        "OVERALL",
        "ACTIVE",
        3L,
        overallWkt(),
        SEEDED_UPDATED_AT);

    SearchAreaQuery query = service;
    Optional<OverallSearchAreaResult> result = query.overallOf(READ_INCIDENT_ID);

    assertThat(result).isPresent();
    OverallSearchAreaResult overall = result.orElseThrow();
    assertThat(overall.id()).isEqualTo(READ_OVERALL_ID);
    assertThat(overall.incidentId()).isEqualTo(READ_INCIDENT_ID);
    assertThat(overall.status()).isEqualTo("ACTIVE");
    assertThat(overall.version()).isEqualTo(3L);
    assertThat(overall.geometry()).isEqualTo(overallPolygon());
    assertThat(overall.bbox())
        .containsExactly(
            new BigDecimal("126.95"),
            new BigDecimal("37.57"),
            new BigDecimal("126.951"),
            new BigDecimal("37.571"));
    assertThat(overall.updatedAt()).isEqualTo(SEEDED_UPDATED_AT);
  }

  @Test
  @DisplayName("byIncident reads seeded UNIT rows and derives historyCount from search_area_history")
  void byIncident_reads_seeded_unit_rows_with_history_count_from_database() {
    seedOpenIncidentWithActiveOperationalPeriod(READ_INCIDENT_ID, READ_OP_ID);
    seedSearchArea(
        READ_OVERALL_ID,
        READ_OP_ID,
        null,
        "OVERALL",
        "OVERALL",
        "ACTIVE",
        3L,
        overallWkt(),
        SEEDED_UPDATED_AT);
    seedSearchArea(
        READ_UNIT_ID,
        READ_OP_ID,
        READ_OVERALL_ID,
        "UNIT-1",
        "UNIT",
        "ACTIVE",
        5L,
        unitWkt(),
        SEEDED_UPDATED_AT.plusSeconds(60));
    seedSearchAreaHistory(READ_UNIT_ID, "CREATED", "ACTIVE", SEEDED_UPDATED_AT.plusSeconds(10));
    seedSearchAreaHistory(
        READ_UNIT_ID, "GEOMETRY_UPDATED", "ACTIVE", SEEDED_UPDATED_AT.plusSeconds(20));

    SearchAreaQuery query = service;
    SearchAreaCollection collection =
        query.byIncident(
            READ_INCIDENT_ID,
            new SearchAreaFilters(List.of("ACTIVE"), READ_OP_ID, null, null, null, false));

    assertThat(collection.incidentId()).isEqualTo(READ_INCIDENT_ID);
    assertThat(collection.sourceVersion()).isEqualTo(5L);
    SearchAreaRow unit =
        collection.areas().stream()
            .filter(row -> READ_UNIT_ID.equals(row.id()))
            .findFirst()
            .orElseThrow();
    assertThat(unit.opId()).isEqualTo(READ_OP_ID);
    assertThat(unit.parentAreaId()).isEqualTo(READ_OVERALL_ID);
    assertThat(unit.status()).isEqualTo("ACTIVE");
    assertThat(unit.version()).isEqualTo(5L);
    assertThat(unit.geometry()).isEqualTo(unitPolygon());
    assertThat(unit.historyCount()).isEqualTo(2L);
    assertThat(unit.updatedAt()).isEqualTo(SEEDED_UPDATED_AT.plusSeconds(60));
  }

  @Test
  @DisplayName("UNIT create stores child search_area row and CREATED history")
  void unit_create_persists_search_area_row_and_created_history() {
    seedOpenIncidentWithActiveOperationalPeriod(UNIT_INCIDENT_ID, UNIT_OP_ID);
    SearchAreaResponse overall =
        service.create(
            overallCreateRequest(UNIT_INCIDENT_ID), "idem-search-area-overall-before-unit-248");

    SearchAreaResponse response =
        service.create(
            unitCreateRequest(UNIT_INCIDENT_ID, UNIT_OP_ID),
            "idem-search-area-unit-persistence-248");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT id,
                   operational_period_id,
                   parent_search_area_id,
                   area_level,
                   status,
                   version,
                   ST_SRID(geometry) AS srid,
                   ST_GeometryType(geometry) AS geometry_type
            FROM search_area
            WHERE id = ?::uuid
            """,
            response.id().toString());

    assertThat(row.get("id")).isEqualTo(response.id());
    assertThat(row.get("operational_period_id")).isEqualTo(UNIT_OP_ID);
    assertThat(row.get("parent_search_area_id")).isEqualTo(overall.id());
    assertThat(row.get("area_level")).isEqualTo("UNIT");
    assertThat(row.get("status")).isEqualTo("ACTIVE");
    assertThat(row.get("version")).isEqualTo(1L);
    assertThat(row.get("srid")).isEqualTo(4326);
    assertThat(row.get("geometry_type")).isEqualTo("ST_Polygon");

    Map<String, Object> history =
        jdbcTemplate.queryForMap(
            """
            SELECT change_type,
                   previous_status,
                   next_status,
                   changed_by_account_id,
                   ST_SRID(next_geometry) AS next_srid,
                   ST_GeometryType(next_geometry) AS next_geometry_type
            FROM search_area_history
            WHERE search_area_id = ?::uuid
            """,
            response.id().toString());

    assertThat(history.get("change_type")).isEqualTo("CREATED");
    assertThat(history.get("previous_status")).isNull();
    assertThat(history.get("next_status")).isEqualTo("ACTIVE");
    assertThat(history.get("changed_by_account_id")).isEqualTo(COMMANDER_ID);
    assertThat(history.get("next_srid")).isEqualTo(4326);
    assertThat(history.get("next_geometry_type")).isEqualTo("ST_Polygon");
  }

  @Test
  @DisplayName("status PATCH updates search_area version and appends STATUS_CHANGED history")
  void status_patch_persists_status_version_and_status_changed_history() {
    seedOpenIncidentWithActiveOperationalPeriod(PATCH_INCIDENT_ID, PATCH_OP_ID);
    service.create(
        overallCreateRequest(PATCH_INCIDENT_ID), "idem-search-area-overall-before-patch-248");
    SearchAreaResponse created =
        service.create(
            unitCreateRequest(PATCH_INCIDENT_ID, PATCH_OP_ID),
            "idem-search-area-unit-before-patch-248");

    SearchAreaResponse patched =
        service.patch(
            created.id(),
            new PatchSearchAreaRequest(
                PATCH_OP_ID, null, "구역 수색 완료", 1L, "COMPLETED", CLIENT_TS.plusMinutes(15)),
            "idem-search-area-unit-patch-248");

    assertThat(patched.status()).isEqualTo("COMPLETED");
    assertThat(patched.version()).isEqualTo(2L);
    assertThat(patched.historyCount()).isEqualTo(2L);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT status, version
            FROM search_area
            WHERE id = ?::uuid
            """,
            created.id().toString());
    assertThat(row.get("status")).isEqualTo("COMPLETED");
    assertThat(row.get("version")).isEqualTo(2L);

    Map<String, Object> history =
        jdbcTemplate.queryForMap(
            """
            SELECT change_type,
                   previous_status,
                   next_status,
                   change_memo,
                   changed_by_account_id
            FROM search_area_history
            WHERE search_area_id = ?::uuid
            ORDER BY changed_at DESC
            LIMIT 1
            """,
            created.id().toString());
    assertThat(history.get("change_type")).isEqualTo("STATUS_CHANGED");
    assertThat(history.get("previous_status")).isEqualTo("ACTIVE");
    assertThat(history.get("next_status")).isEqualTo("COMPLETED");
    assertThat(history.get("change_memo")).isEqualTo("구역 수색 완료");
    assertThat(history.get("changed_by_account_id")).isEqualTo(COMMANDER_ID);
  }

  @Test
  @DisplayName("split persists CANCELLED parent, ACTIVE children, and S2 split_lifecycle histories")
  void split_persists_parent_children_and_split_lifecycle_histories() {
    seedOpenIncidentWithActiveOperationalPeriod(SPLIT_INCIDENT_ID, SPLIT_OP_ID);
    service.create(
        overallCreateRequest(SPLIT_INCIDENT_ID), "idem-search-area-overall-before-split-248");
    SearchAreaResponse parent =
        service.create(
            unitCreateRequest(SPLIT_INCIDENT_ID, SPLIT_OP_ID),
            "idem-search-area-unit-before-split-248");

    SearchAreaSplitResponse response =
        service.split(
            parent.id(),
            new SplitSearchAreaRequest(
                SPLIT_OP_ID,
                List.of(
                    polygon("126.950100", "37.570100", "0.000500", "0.001000"),
                    polygon("126.950600", "37.570100", "0.000500", "0.001000")),
                "분할",
                1L,
                CLIENT_TS.plusMinutes(20)),
            "idem-search-area-unit-split-248");

    assertThat(response.parentAreaId()).isEqualTo(parent.id());
    assertThat(response.parent().id()).isEqualTo(parent.id());
    assertThat(response.parent().status()).isEqualTo("CANCELLED");
    assertThat(response.parent().version()).isEqualTo(2L);
    assertThat(response.parent().historyCount()).isEqualTo(2L);
    assertThat(response.parent().geometry()).isEqualTo(parent.geometry());
    assertThat(response.createdAreaIds()).hasSize(2);
    assertThat(response.children()).hasSize(2);
    assertThat(response.children())
        .allSatisfy(
            child -> {
              assertThat(child.parentAreaId()).isEqualTo(parent.id());
              assertThat(child.opId()).isEqualTo(SPLIT_OP_ID);
              assertThat(child.status()).isEqualTo("ACTIVE");
              assertThat(child.version()).isEqualTo(1L);
              assertThat(child.historyCount()).isEqualTo(1L);
            });
    assertThat(response.createdAreaIds())
        .containsExactlyElementsOf(
            response.children().stream().map(SearchAreaResponse::id).toList());

    Map<String, Object> parentRow =
        jdbcTemplate.queryForMap(
            """
            SELECT status,
                   version,
                   ST_Equals(geometry, ST_GeomFromText(?, 4326)) AS original_geometry
            FROM search_area
            WHERE id = ?::uuid
            """,
            unitWkt(),
            parent.id().toString());
    assertThat(parentRow.get("status")).isEqualTo("CANCELLED");
    assertThat(parentRow.get("version")).isEqualTo(2L);
    assertThat(parentRow.get("original_geometry")).isEqualTo(true);

    Integer childCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_area
            WHERE parent_search_area_id = ?::uuid
              AND operational_period_id = ?::uuid
              AND status = 'ACTIVE'
              AND version = 1
            """,
            Integer.class,
            parent.id().toString(),
            SPLIT_OP_ID.toString());
    assertThat(childCount).isEqualTo(2);

    Map<String, Object> parentHistory =
        jdbcTemplate.queryForMap(
            """
            SELECT change_type,
                   previous_status,
                   next_status,
                   change_memo,
                   changed_by_account_id,
                   ST_Equals(previous_geometry, ST_GeomFromText(?, 4326)) AS previous_original,
                   ST_Equals(next_geometry, ST_GeomFromText(?, 4326)) AS next_original
            FROM search_area_history
            WHERE search_area_id = ?::uuid
              AND change_type = 'STATUS_CHANGED'
            ORDER BY changed_at DESC
            LIMIT 1
            """,
            unitWkt(),
            unitWkt(),
            parent.id().toString());
    assertThat(parentHistory.get("change_type")).isEqualTo("STATUS_CHANGED");
    assertThat(parentHistory.get("previous_status")).isEqualTo("ACTIVE");
    assertThat(parentHistory.get("next_status")).isEqualTo("CANCELLED");
    assertThat(parentHistory.get("change_memo")).isEqualTo("분할");
    assertThat(parentHistory.get("changed_by_account_id")).isEqualTo(COMMANDER_ID);
    assertThat(parentHistory.get("previous_original")).isEqualTo(true);
    assertThat(parentHistory.get("next_original")).isEqualTo(true);

    Integer splitHistoryCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_area_history history
            JOIN search_area child
              ON child.id = history.search_area_id
            WHERE child.parent_search_area_id = ?::uuid
              AND history.change_type = 'SPLIT'
              AND history.previous_status IS NULL
              AND history.next_status = 'ACTIVE'
              AND history.next_geometry IS NOT NULL
              AND history.changed_by_account_id = ?::uuid
            """,
            Integer.class,
            parent.id().toString(),
            COMMANDER_ID.toString());
    assertThat(splitHistoryCount).isEqualTo(2);
  }

  @Test
  @DisplayName("overall split keeps OVERALL ACTIVE and creates UNIT children across OP transition")
  void overall_split_keeps_overall_active_and_creates_unit_children() {
    seedOpenIncidentWithActiveOperationalPeriod(OVERALL_SPLIT_INCIDENT_ID, OVERALL_SPLIT_OLD_OP_ID);
    SearchAreaResponse overall =
        service.create(
            overallCreateRequest(OVERALL_SPLIT_INCIDENT_ID),
            "idem-search-area-overall-before-overall-split-248");

    endOperationalPeriod(OVERALL_SPLIT_OLD_OP_ID, CLIENT_TS.plusMinutes(19));
    seedActiveOperationalPeriod(
        OVERALL_SPLIT_INCIDENT_ID, OVERALL_SPLIT_CURRENT_OP_ID, 2, CLIENT_TS.plusMinutes(20));

    SearchAreaSplitResponse response =
        service.split(
            overall.id(),
            new SplitSearchAreaRequest(
                OVERALL_SPLIT_CURRENT_OP_ID,
                List.of(
                    polygon("126.950100", "37.570100", "0.000400", "0.000800"),
                    polygon("126.950500", "37.570100", "0.000400", "0.000800")),
                "overall split",
                1L,
                CLIENT_TS.plusMinutes(21)),
            "idem-search-area-overall-split-248");

    assertThat(response.parentAreaId()).isEqualTo(overall.id());
    assertThat(response.parent().id()).isEqualTo(overall.id());
    assertThat(response.parent().areaLevel()).isEqualTo("OVERALL");
    assertThat(response.parent().status()).isEqualTo("ACTIVE");
    assertThat(response.parent().version()).isEqualTo(1L);
    assertThat(response.parent().geometry()).isEqualTo(overall.geometry());
    assertThat(response.children())
        .hasSize(2)
        .allSatisfy(
            child -> {
              assertThat(child.parentAreaId()).isEqualTo(overall.id());
              assertThat(child.opId()).isEqualTo(OVERALL_SPLIT_CURRENT_OP_ID);
              assertThat(child.areaLevel()).isEqualTo("UNIT");
              assertThat(child.status()).isEqualTo("ACTIVE");
            });

    Map<String, Object> overallRow =
        jdbcTemplate.queryForMap(
            """
            SELECT status,
                   version,
                   ST_Equals(geometry, ST_GeomFromText(?, 4326)) AS original_geometry
            FROM search_area
            WHERE id = ?::uuid
            """,
            overallWkt(),
            overall.id().toString());
    assertThat(overallRow.get("status")).isEqualTo("ACTIVE");
    assertThat(overallRow.get("version")).isEqualTo(1L);
    assertThat(overallRow.get("original_geometry")).isEqualTo(true);

    Integer parentStatusChangedHistoryCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_area_history
            WHERE search_area_id = ?::uuid
              AND change_type = 'STATUS_CHANGED'
            """,
            Integer.class,
            overall.id().toString());
    assertThat(parentStatusChangedHistoryCount).isZero();

    Integer unitChildCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_area
            WHERE parent_search_area_id = ?::uuid
              AND operational_period_id = ?::uuid
              AND area_level = 'UNIT'
              AND status = 'ACTIVE'
            """,
            Integer.class,
            overall.id().toString(),
            OVERALL_SPLIT_CURRENT_OP_ID.toString());
    assertThat(unitChildCount).isEqualTo(2);

    Optional<OverallSearchAreaResult> activeOverall = service.overallOf(OVERALL_SPLIT_INCIDENT_ID);
    assertThat(activeOverall).isPresent();
    assertThat(activeOverall.orElseThrow().id()).isEqualTo(overall.id());
    assertThat(activeOverall.orElseThrow().status()).isEqualTo("ACTIVE");
  }

  private void seedOpenIncidentWithActiveOperationalPeriod() {
    seedOpenIncidentWithActiveOperationalPeriod(INCIDENT_ID, OP_ID);
  }

  private void seedOpenIncidentWithActiveOperationalPeriod(UUID incidentId, UUID opId) {
    Instant now = Instant.parse("2026-05-12T00:00:00Z");
    Timestamp nowTimestamp = Timestamp.from(now);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
            id, source_incident_id, title, status, opened_at,
            closed_at, closed_by_account_id, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?, 'OPEN', ?, NULL, NULL, 1, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET status = 'OPEN',
            updated_at = EXCLUDED.updated_at
        """,
        incidentId.toString(),
        incidentId.toString(),
        "S2 SearchArea persistence red",
        nowTimestamp,
        nowTimestamp,
        nowTimestamp);

    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, reason_memo,
            started_by_account_id, ended_by_account_id, started_at, ended_at,
            version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL', NULL,
                ?::uuid, NULL, ?, NULL, 1, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET status = 'ACTIVE',
            updated_at = EXCLUDED.updated_at
        """,
        opId.toString(),
        incidentId.toString(),
        COMMANDER_ID.toString(),
        nowTimestamp,
        nowTimestamp,
        nowTimestamp);
  }

  private void endOperationalPeriod(UUID opId, Instant endedAt) {
    Timestamp endedAtTimestamp = Timestamp.from(endedAt);
    jdbcTemplate.update(
        """
        UPDATE operational_period
        SET status = 'ENDED',
            ended_by_account_id = ?::uuid,
            ended_at = ?,
            version = version + 1,
            updated_at = ?
        WHERE id = ?::uuid
        """,
        COMMANDER_ID.toString(),
        endedAtTimestamp,
        endedAtTimestamp,
        opId.toString());
  }

  private void seedActiveOperationalPeriod(
      UUID incidentId, UUID opId, int sequenceNumber, Instant startedAt) {
    Timestamp startedAtTimestamp = Timestamp.from(startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, reason_memo,
            started_by_account_id, ended_by_account_id, started_at, ended_at,
            version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?, 'ACTIVE', 'AREA_CHANGED', NULL,
                ?::uuid, NULL, ?, NULL, 1, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET incident_id = EXCLUDED.incident_id,
            sequence_number = EXCLUDED.sequence_number,
            status = EXCLUDED.status,
            reason = EXCLUDED.reason,
            reason_memo = EXCLUDED.reason_memo,
            started_by_account_id = EXCLUDED.started_by_account_id,
            ended_by_account_id = EXCLUDED.ended_by_account_id,
            started_at = EXCLUDED.started_at,
            ended_at = EXCLUDED.ended_at,
            version = EXCLUDED.version,
            created_at = EXCLUDED.created_at,
            updated_at = EXCLUDED.updated_at
        """,
        opId.toString(),
        incidentId.toString(),
        sequenceNumber,
        COMMANDER_ID.toString(),
        startedAtTimestamp,
        startedAtTimestamp,
        startedAtTimestamp);
  }

  private void seedSearchArea(
      UUID id,
      UUID opId,
      UUID parentId,
      String name,
      String areaLevel,
      String status,
      long version,
      String wkt,
      Instant updatedAt) {
    Timestamp updatedAtTimestamp = Timestamp.from(updatedAt);
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
            id, operational_period_id, parent_search_area_id, name, area_level,
            geometry, status, version, created_by_account_id, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?,
                ST_GeomFromText(?, 4326), ?, ?, ?::uuid, ?, ?)
        """,
        id.toString(),
        opId.toString(),
        parentId == null ? null : parentId.toString(),
        name,
        areaLevel,
        wkt,
        status,
        version,
        COMMANDER_ID.toString(),
        updatedAtTimestamp,
        updatedAtTimestamp);
  }

  private void seedSearchAreaHistory(
      UUID searchAreaId, String changeType, String nextStatus, Instant changedAt) {
    Timestamp changedAtTimestamp = Timestamp.from(changedAt);
    jdbcTemplate.update(
        """
        INSERT INTO search_area_history (
            id, search_area_id, change_type, previous_status, next_status,
            previous_geometry, next_geometry, change_memo, changed_by_account_id,
            changed_at, created_at
        )
        VALUES (gen_random_uuid(), ?::uuid, ?, NULL, ?,
                NULL, NULL, NULL, ?::uuid, ?, ?)
        """,
        searchAreaId.toString(),
        changeType,
        nextStatus,
        COMMANDER_ID.toString(),
        changedAtTimestamp,
        changedAtTimestamp);
  }

  private static CreateSearchAreaRequest overallCreateRequest() {
    return overallCreateRequest(INCIDENT_ID);
  }

  private static CreateSearchAreaRequest overallCreateRequest(UUID incidentId) {
    return new CreateSearchAreaRequest(
        incidentId, null, "OVERALL", polygon("126.950000", "37.570000"), null, CLIENT_TS);
  }

  private static CreateSearchAreaRequest unitCreateRequest(UUID incidentId, UUID opId) {
    return new CreateSearchAreaRequest(
        incidentId, opId, "UNIT", polygon("126.950100", "37.570100"), "1구역", CLIENT_TS);
  }

  private static GeoJsonPolygon polygon(String minLon, String minLat) {
    return polygon(minLon, minLat, "0.001000", "0.001000");
  }

  private static GeoJsonPolygon polygon(
      String minLon, String minLat, String lonDelta, String latDelta) {
    BigDecimal lon = new BigDecimal(minLon).stripTrailingZeros();
    BigDecimal lat = new BigDecimal(minLat).stripTrailingZeros();
    BigDecimal maxLon = lon.add(new BigDecimal(lonDelta)).stripTrailingZeros();
    BigDecimal maxLat = lat.add(new BigDecimal(latDelta)).stripTrailingZeros();
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                List.of(lon, lat),
                List.of(maxLon, lat),
                List.of(maxLon, maxLat),
                List.of(lon, maxLat),
                List.of(lon, lat))));
  }

  private static GeoJsonPolygon overallPolygon() {
    return polygon("126.95", "37.57");
  }

  private static GeoJsonPolygon unitPolygon() {
    return polygon("126.9501", "37.5701");
  }

  private static String overallWkt() {
    return "POLYGON((126.95 37.57,126.951 37.57,126.951 37.571,126.95 37.571,126.95 37.57))";
  }

  private static String unitWkt() {
    return "POLYGON((126.9501 37.5701,126.9511 37.5701,126.9511 37.5711,126.9501 37.5711,126.9501 37.5701))";
  }
}
