package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("SearchPath mapper")
@Sql(scripts = "/sql/path/search-path-context.sql")
class SearchPathMapperTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID POLICE_PHONE_ID = SearchPathFixtures.POLICE_PHONE_ID;
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID OTHER_DUTY_SHIFT_ID =
      UUID.fromString("71000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_PATH_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
  private static final UUID NONMATCHING_ID =
      UUID.fromString("00000000-0000-0000-0000-000000009999");
  private static final UUID SEGMENT_ID = UUID.fromString("72000000-0000-0000-0000-000000002621");
  private static final UUID SECOND_SEGMENT_ID =
      UUID.fromString("72000000-0000-0000-0000-000000002622");
  private static final UUID EXCLUDED_POINT_ID =
      UUID.fromString("73000000-0000-0000-0000-000000002621");
  private static final UUID SECOND_EXCLUDED_POINT_ID =
      UUID.fromString("73000000-0000-0000-0000-000000002622");
  private static final UUID LIFECYCLE_EVENT_ID =
      UUID.fromString("74000000-0000-0000-0000-000000002621");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private SearchPathMapper searchPathMapper;

  @Test
  @DisplayName("SearchPath를 저장하면 같은 값으로 조회한다")
  void insertPathAndFindPathById() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();

    searchPathMapper.insertPath(path);

    SearchPath found = searchPathMapper.findPathById(PATH_ID).orElseThrow();
    assertThat(found.getId()).isEqualTo(PATH_ID);
    assertThat(found.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(found.getOpId()).isEqualTo(OP_ID);
    assertThat(found.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(found.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(found.getStatus()).isEqualTo(SearchPathStatus.RECORDING);
    assertThat(found.getVersion()).isEqualTo(1L);
    assertThat(found.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(found.getCreatedAt()).isEqualTo(STARTED_AT);
    assertThat(found.getUpdatedAt()).isEqualTo(STARTED_AT);
  }

  @Test
  @DisplayName("SearchPath 목록은 사건, OP, 계정 조건으로 필터링한다")
  @Sql(scripts = {"/sql/path/search-path-context.sql", "/sql/path/search-path-other-phone.sql"})
  void findPathsFiltersByIncidentOpPolicePhoneAndAccount() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    SearchPath otherPath =
        path.toBuilder()
            .id(OTHER_PATH_ID)
            .dutyShiftId(OTHER_DUTY_SHIFT_ID)
            .accountId(OTHER_ACCOUNT_ID)
            .build();
    searchPathMapper.insertPath(path);
    searchPathMapper.insertPath(otherPath);

    assertThat(searchPathMapper.findPaths(INCIDENT_ID, null, null))
        .extracting(SearchPath::getId)
        .containsExactlyInAnyOrder(PATH_ID, OTHER_PATH_ID);
    assertThat(searchPathMapper.findPaths(null, OP_ID, null))
        .extracting(SearchPath::getId)
        .containsExactlyInAnyOrder(PATH_ID, OTHER_PATH_ID);
    assertThat(searchPathMapper.findPaths(null, null, ACCOUNT_ID))
        .extracting(SearchPath::getId)
        .containsExactly(PATH_ID);

    assertThat(searchPathMapper.findPaths(NONMATCHING_ID, null, null)).isEmpty();
    assertThat(searchPathMapper.findPaths(null, NONMATCHING_ID, null)).isEmpty();
    assertThat(searchPathMapper.findPaths(null, null, NONMATCHING_ID)).isEmpty();
  }

  @Test
  @DisplayName("수색 경로 하위 객체를 도메인 객체로 직접 저장하고 조회한다")
  void insertAndFindPathDetails() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    searchPathMapper.insertPath(path);

    SearchPathSegment segment =
        SearchPathSegment.builder()
            .id(SEGMENT_ID)
            .searchPathId(PATH_ID)
            .movementType(MovementType.FOOT)
            .movementTypeSource(MovementTypeSource.AUTO)
            .geometry(lineString())
            .startedAt(STARTED_AT)
            .endedAt(STARTED_AT.plusSeconds(5))
            .version(1L)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    SearchPathExcludedPoint excludedPoint =
        SearchPathExcludedPoint.builder()
            .id(EXCLUDED_POINT_ID)
            .searchPathId(PATH_ID)
            .pointId("point-excluded-1")
            .reason("low_accuracy")
            .clientTs(OffsetDateTime.ofInstant(STARTED_AT.plusSeconds(10), ZoneOffset.UTC))
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    SearchPathLifecycleEvent lifecycleEvent =
        SearchPathLifecycleEvent.builder()
            .id(LIFECYCLE_EVENT_ID)
            .searchPathId(PATH_ID)
            .eventType("STARTED")
            .clientTs(STARTED_AT)
            .serverReceivedAt(STARTED_AT)
            .version(1L)
            .createdAt(STARTED_AT)
            .build();

    searchPathMapper.insertSegments(List.of(segment));
    searchPathMapper.insertExcludedPoints(List.of(excludedPoint));
    searchPathMapper.insertLifecycleEvent(lifecycleEvent);

    assertThat(searchPathMapper.findSegmentsByPathId(PATH_ID))
        .singleElement()
        .satisfies(
            found -> {
              assertThat(found.getId()).isEqualTo(SEGMENT_ID);
              assertThat(found.getSearchPathId()).isEqualTo(PATH_ID);
              assertThat(found.getMovementType()).isEqualTo(MovementType.FOOT);
              assertThat(found.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
              assertThat(found.getGeometry().getSRID()).isEqualTo(4326);
              assertThat(found.getVersion()).isEqualTo(1L);
            });
    assertThat(searchPathMapper.findExcludedPointsByPathId(PATH_ID))
        .singleElement()
        .satisfies(
            found -> {
              assertThat(found.getId()).isEqualTo(EXCLUDED_POINT_ID);
              assertThat(found.getSearchPathId()).isEqualTo(PATH_ID);
              assertThat(found.getPointId()).isEqualTo("point-excluded-1");
              assertThat(found.getReason()).isEqualTo("low_accuracy");
            });
    assertThat(searchPathMapper.findLifecycleEventsByPathId(PATH_ID))
        .singleElement()
        .satisfies(
            found -> {
              assertThat(found.getId()).isEqualTo(LIFECYCLE_EVENT_ID);
              assertThat(found.getSearchPathId()).isEqualTo(PATH_ID);
              assertThat(found.getEventType()).isEqualTo("STARTED");
            });
  }

  @Test
  @DisplayName("구간 목록을 저장하면 모든 구간 ID가 조회된다")
  void insertSegmentsStoresEverySegment() {
    insertPath(null);
    SearchPathSegment first =
        SearchPathSegment.builder()
            .id(SEGMENT_ID)
            .searchPathId(PATH_ID)
            .movementType(MovementType.FOOT)
            .movementTypeSource(MovementTypeSource.AUTO)
            .geometry(lineString())
            .startedAt(STARTED_AT)
            .endedAt(STARTED_AT.plusSeconds(5))
            .version(1L)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    SearchPathSegment second =
        first.toBuilder()
            .id(SECOND_SEGMENT_ID)
            .startedAt(STARTED_AT.plusSeconds(10))
            .endedAt(STARTED_AT.plusSeconds(15))
            .build();

    searchPathMapper.insertSegments(List.of(first, second));

    assertThat(searchPathMapper.findSegmentsByPathId(PATH_ID))
        .extracting(SearchPathSegment::getId)
        .containsExactly(SEGMENT_ID, SECOND_SEGMENT_ID);
  }

  @Test
  @DisplayName("제외 좌표 목록을 저장하면 모든 제외 좌표 ID가 조회된다")
  void insertExcludedPointsStoresEveryPoint() {
    insertPath(null);
    SearchPathExcludedPoint first =
        SearchPathExcludedPoint.builder()
            .id(EXCLUDED_POINT_ID)
            .searchPathId(PATH_ID)
            .pointId("point-excluded-1")
            .reason("low_accuracy")
            .clientTs(OffsetDateTime.ofInstant(STARTED_AT, ZoneOffset.UTC))
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    SearchPathExcludedPoint second =
        first.toBuilder()
            .id(SECOND_EXCLUDED_POINT_ID)
            .pointId("point-excluded-2")
            .clientTs(OffsetDateTime.ofInstant(STARTED_AT.plusSeconds(5), ZoneOffset.UTC))
            .build();

    searchPathMapper.insertExcludedPoints(List.of(first, second));

    assertThat(searchPathMapper.findExcludedPointsByPathId(PATH_ID))
        .extracting(SearchPathExcludedPoint::getId)
        .containsExactly(EXCLUDED_POINT_ID, SECOND_EXCLUDED_POINT_ID);
  }

  @Test
  @DisplayName("좌표 묶음을 추가하면 경로 도형의 좌표 수와 버전이 증가한다")
  void updatePathAfterPointAppendExtendsGeometry() {
    insertPath(lineString());

    int updated =
        searchPathMapper.updatePathAfterPointAppend(
            PATH_ID, 2, lineString(), 1L, 2L, STARTED_AT.plusSeconds(5));

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            """
            SELECT ST_NumPoints(geometry) AS point_count, version
            FROM search_path
            WHERE id = ?::uuid
            """,
            PATH_ID.toString());
    assertThat(updated).isEqualTo(1);
    assertThat(stored.get("point_count")).isEqualTo(4);
    assertThat(stored.get("version")).isEqualTo(2L);
  }

  @Test
  @DisplayName("좌표 하나로 시작한 경로에 다음 좌표를 추가하면 도형은 두 좌표만 가진다")
  void updatePathAfterFirstPointRemovesStoredDuplicate() {
    Coordinate firstCoordinate = new Coordinate(126.950000, 37.560000);
    GeometryFactory geometryFactory = new GeometryFactory();
    LineString firstPointGeometry =
        geometryFactory.createLineString(
            new Coordinate[] {firstCoordinate, firstCoordinate.copy()});
    firstPointGeometry.setSRID(4326);
    Geometry appendedPoint = geometryFactory.createPoint(new Coordinate(126.950200, 37.560200));
    appendedPoint.setSRID(4326);
    insertPath(firstPointGeometry);

    searchPathMapper.updatePathAfterPointAppend(
        PATH_ID, 1, appendedPoint, 1L, 2L, STARTED_AT.plusSeconds(5));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT ST_NumPoints(geometry) FROM search_path WHERE id = ?::uuid",
                Integer.class,
                PATH_ID.toString()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("좌표 추가용 경로 정보를 잠금 조회하면 경로 도형은 읽지 않는다")
  void findPathMetadataForUpdateDoesNotLoadGeometry() {
    insertPath(lineString());

    SearchPath found = searchPathMapper.findPathMetadataForUpdate(PATH_ID).orElseThrow();

    assertThat(found.getId()).isEqualTo(PATH_ID);
    assertThat(found.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(found.getGeometry()).isNull();
  }

  @Test
  @DisplayName("마지막 GPS 좌표 순번에 1을 더해 다음 저장 순번을 반환한다")
  void findNextGpsPointOrderReturnsNextStoredOrder() {
    insertPath(null);
    searchPathMapper.insertGpsPoints(
        PATH_ID,
        5,
        List.of(
            gpsPoint("gps-count-001", "126.913001", "35.162001", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint(
                "gps-count-002", "126.913002", "35.162002", "1.75", 4, "2026-04-28T00:00:05Z")),
        STARTED_AT);

    assertThat(searchPathMapper.findNextGpsPointOrder(PATH_ID)).isEqualTo(7);
  }

  @Test
  @DisplayName("GPS 측정값을 저장하면 수집 순서와 원본 값을 유지한다")
  void insertAndFindGpsPointsPreservesMeasurements() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();
    searchPathMapper.insertPath(path);
    List<GpsPoint> points =
        List.of(
            gpsPoint(
                "gps-original-001", "126.913001", "35.162001", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint(
                "gps-original-002",
                "126.913002",
                "35.162002",
                "1.75",
                null,
                "2026-04-28T00:00:05Z"));

    searchPathMapper.insertGpsPoints(PATH_ID, 0, points, STARTED_AT);

    List<GpsPoint> found = searchPathMapper.findGpsPointsByPathId(PATH_ID);
    assertThat(found).hasSize(2);
    assertGpsPoint(found.get(0), points.get(0));
    assertGpsPoint(found.get(1), points.get(1));
    assertThat(found.get(1).getHorizontalAccuracyM()).isNull();
  }

  private GpsPoint gpsPoint(
      String pointId,
      String lon,
      String lat,
      String speedMps,
      Integer horizontalAccuracyM,
      String clientTs) {
    return GpsPoint.builder()
        .pointId(pointId)
        .clientTs(OffsetDateTime.parse(clientTs))
        .lon(new BigDecimal(lon))
        .lat(new BigDecimal(lat))
        .speedMps(new BigDecimal(speedMps))
        .horizontalAccuracyM(horizontalAccuracyM)
        .build();
  }

  private void insertPath(Geometry geometry) {
    searchPathMapper.insertPath(
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .geometry(geometry)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build());
  }

  private void assertGpsPoint(GpsPoint actual, GpsPoint expected) {
    assertThat(actual.getPointId()).isEqualTo(expected.getPointId());
    assertThat(actual.getClientTs()).isEqualTo(expected.getClientTs());
    assertThat(actual.getLon()).isEqualByComparingTo(expected.getLon());
    assertThat(actual.getLat()).isEqualByComparingTo(expected.getLat());
    assertThat(actual.getSpeedMps()).isEqualByComparingTo(expected.getSpeedMps());
    assertThat(actual.getHorizontalAccuracyM()).isEqualTo(expected.getHorizontalAccuracyM());
  }

  private LineString lineString() {
    LineString geometry =
        new GeometryFactory()
            .createLineString(
                new Coordinate[] {
                  new Coordinate(126.950000, 37.560000), new Coordinate(126.950100, 37.560100)
                });
    geometry.setSRID(4326);
    return geometry;
  }
}
