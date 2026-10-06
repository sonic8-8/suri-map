package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired private SqlSessionFactory sqlSessionFactory;
  @Autowired private PlatformTransactionManager transactionManager;

  @ParameterizedTest
  @ValueSource(strings = {"missing", "ambiguous", "overlap", "gap"})
  @DisplayName("GPS 범위를 확정할 수 없으면 일부 구간도 보완하지 않고 원본을 유지한다")
  void unresolved_ranges_abort_backfill_without_partial_changes(String invalidData) {
    // given: 실제 원본에 누락·중복 후보·겹친 구간·순번 공백 중 하나가 있다.
    insertLegacySegmentWithGps();
    switch (invalidData) {
      case "missing" ->
          jdbcTemplate.update(
              "DELETE FROM search_path_gps_point WHERE search_path_id = ? AND point_order = 1",
              PATH_ID);
      case "ambiguous" ->
          searchPathMapper.insertGpsPoints(
              PATH_ID,
              2,
              List.of(
                  gpsPoint(
                      "repeat-first", "126.950000", "37.560000", "1.25", 4, "2026-04-28T00:00:00Z"),
                  gpsPoint(
                      "repeat-second",
                      "126.950100",
                      "37.560100",
                      "1.25",
                      4,
                      "2026-04-28T00:00:05Z")),
              STARTED_AT);
      case "overlap" ->
          searchPathMapper.insertSegments(
              List.of(
                  searchPathMapper.findSegmentById(SEGMENT_ID).orElseThrow().toBuilder()
                      .id(SECOND_SEGMENT_ID)
                      .build()));
      case "gap" ->
          jdbcTemplate.update(
              "UPDATE search_path_gps_point SET point_order = 2 WHERE search_path_id = ? AND point_order = 1",
              PATH_ID);
      default -> throw new IllegalArgumentException(invalidData);
    }
    List<Map<String, Object>> before =
        jdbcTemplate.queryForList("SELECT * FROM search_path_segment ORDER BY id");

    // when: 불확실한 자료에 보완 SQL을 실행한다.
    assertThatThrownBy(this::backfillSegmentProgress)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));

    // then: 정상으로 보이는 구간을 포함해 어떤 행도 부분 변경하지 않는다.
    assertThat(jdbcTemplate.queryForList("SELECT * FROM search_path_segment ORDER BY id"))
        .isEqualTo(before);
    assertThat(searchPathMapper.findPathById(PATH_ID).orElseThrow().getVersion()).isEqualTo(41L);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  @DisplayName("같은 좌표·시각이 반복되어도 생성 ID로 확인된 원본 GPS 개수를 보존한다")
  void backfill_distinguishes_single_point_from_repeated_points(int pointCount) {
    // given: LineString의 모양만으로는 한 점과 같은 위치의 두 점을 구분할 수 없다.
    insertLegacySegmentWithGps();
    jdbcTemplate.update("DELETE FROM search_path_gps_point WHERE search_path_id = ?", PATH_ID);
    List<GpsPoint> points =
        java.util.stream.IntStream.range(0, pointCount)
            .mapToObj(
                index ->
                    gpsPoint(
                        "same-" + index,
                        "126.950000",
                        "37.560000",
                        "1.25",
                        4,
                        "2026-04-28T00:00:00Z"))
            .toList();
    searchPathMapper.insertGpsPoints(PATH_ID, 0, points, STARTED_AT);
    UUID generatedId =
        UUID.nameUUIDFromBytes(
            ("search-path-segment:" + PATH_ID + ":0:" + (pointCount - 1))
                .getBytes(StandardCharsets.UTF_8));
    jdbcTemplate.update(
        "UPDATE search_path_segment SET id = ?, ended_at = started_at,"
            + " geometry = ST_GeomFromText('LINESTRING(126.95 37.56,126.95 37.56)',4326) WHERE id = ?",
        generatedId,
        SEGMENT_ID);

    // when: 원본 순번·좌표·측정 시각·생성 ID를 함께 대조해 보완한다.
    backfillSegmentProgress();

    // then: 도형 점 개수가 아니라 실제 수집한 원본 개수가 유지된다.
    SearchPathSegment found = searchPathMapper.findSegmentById(generatedId).orElseThrow();
    assertThat(found.getStartIndex()).isZero();
    assertThat(found.getEndIndex()).isEqualTo(pointCount - 1);
    assertThat(found.getLastChangedPathVersion()).isEqualTo(41L);
  }

  @Test
  @DisplayName("기존 구간을 보완하면 GPS 순번과 현재 경로 버전만 채우고 원본은 유지한다")
  void backfill_initializes_progress_without_rewriting_existing_path_or_segment() {
    // given: 경로 버전 41, 자체 버전 7인 기존 구간과 원본 GPS다.
    insertLegacySegmentWithGps();
    Map<String, Object> originalPath =
        jdbcTemplate.queryForMap("SELECT * FROM search_path WHERE id = ?", PATH_ID);
    String originalSegment =
        jdbcTemplate.queryForObject(
            "SELECT (to_jsonb(s) - 'start_point_order' - 'end_point_order'"
                + " - 'last_changed_path_version')::text FROM search_path_segment s WHERE id = ?",
            String.class,
            SEGMENT_ID);

    // when: 배포에 사용할 보완 SQL을 실행한다.
    backfillSegmentProgress();

    // then: 과거 버전을 재구성하거나 도형을 바꾸지 않고 현재 버전에서 추적을 시작한다.
    SearchPathSegment found = searchPathMapper.findSegmentById(SEGMENT_ID).orElseThrow();
    assertThat(found.getStartIndex()).isZero();
    assertThat(found.getEndIndex()).isEqualTo(1);
    assertThat(found.getLastChangedPathVersion()).isEqualTo(41L);
    assertThat(jdbcTemplate.queryForMap("SELECT * FROM search_path WHERE id = ?", PATH_ID))
        .isEqualTo(originalPath);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT (to_jsonb(s) - 'start_point_order' - 'end_point_order'"
                    + " - 'last_changed_path_version')::text FROM search_path_segment s WHERE id = ?",
                String.class,
                SEGMENT_ID))
        .isEqualTo(originalSegment);
  }

  @Test
  @DisplayName("저장한 구간을 다시 조회하면 시작·끝 GPS 순번을 유지한다")
  void saved_segment_keeps_gps_point_range_after_reload() {
    // given: GPS 저장 순번 4~5에 해당하는 구간을 준비한다.
    insertPath(null);
    SearchPathSegment segment =
        SearchPathSegment.builder()
            .id(SEGMENT_ID)
            .searchPathId(PATH_ID)
            .movementType(MovementType.FOOT)
            .movementTypeSource(MovementTypeSource.AUTO)
            .geometry(lineString())
            .startedAt(STARTED_AT)
            .endedAt(STARTED_AT.plusSeconds(5))
            .startIndex(4)
            .endIndex(5)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();

    // when: 실제 Mapper로 저장하고 단건·목록·전체 경로 조회로 다시 읽는다.
    searchPathMapper.insertSegments(List.of(segment));
    List<SearchPathSegment> reloaded =
        List.of(
            searchPathMapper.findSegmentById(SEGMENT_ID).orElseThrow(),
            searchPathMapper.findSegmentsByPathId(PATH_ID).get(0),
            searchPathMapper.findPaths(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments().get(0));

    // then: 도형을 검색해 추정하지 않아도 저장한 순번이 그대로 반환된다.
    assertThat(reloaded)
        .allSatisfy(
            found -> {
              assertThat(found.getStartIndex()).isEqualTo(4);
              assertThat(found.getEndIndex()).isEqualTo(5);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"findPaths", "findAllPaths"})
  @DisplayName("GPS 좌표가 여러 개 있어도 조회 SQL은 경로 전체 도형을 한 번만 반환한다")
  void multiple_gps_points_do_not_repeat_stored_path_geometry(String queryMethod) {
    // given: GPS와 경로 전체 도형이 함께 저장된 실제 DB 조건을 만든다.
    insertPath(lineString());
    List<GpsPoint> points =
        List.of(
            gpsPoint("first", "126.950000", "37.560000", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint("second", "126.950050", "37.560050", "1.25", 4, "2026-04-28T00:00:05Z"),
            gpsPoint("third", "126.950100", "37.560100", "1.25", 4, "2026-04-28T00:00:10Z"));
    searchPathMapper.insertGpsPoints(PATH_ID, 0, points, STARTED_AT);
    Map<String, UUID> parameters =
        Map.of("incidentId", INCIDENT_ID, "opId", OP_ID, "accountId", ACCOUNT_ID);
    MappedStatement statement =
        sqlSessionFactory
            .getConfiguration()
            .getMappedStatement(SearchPathMapper.class.getName() + "." + queryMethod);
    BoundSql boundSql = statement.getBoundSql(parameters);

    // when: 운영 Mapper의 SQL과 UUID 바인딩을 그대로 실행해 DB가 반환한 도형을 읽는다.
    List<String> returnedGeometries =
        jdbcTemplate.query(
            connection -> {
              PreparedStatement prepared = connection.prepareStatement(boundSql.getSql());
              new DefaultParameterHandler(statement, parameters, boundSql).setParameters(prepared);
              return prepared;
            },
            (row, index) -> row.getString("geometry"));

    // then: 객체 조립으로 가려지기 전의 SQL 결과에도 전체 도형이 중복되지 않는다.
    assertThat(returnedGeometries)
        .filteredOn(geometry -> geometry != null)
        .containsExactly("SRID=4326;LINESTRING(126.95 37.56,126.9501 37.5601)");
    List<SearchPath> paths =
        queryMethod.equals("findPaths")
            ? searchPathMapper.findPaths(INCIDENT_ID, OP_ID, ACCOUNT_ID)
            : searchPathMapper.findAllPaths();
    assertThat(paths)
        .singleElement()
        .satisfies(
            path -> {
              assertThat(path.getGeometry().equalsExact(lineString())).isTrue();
              assertThat(path.getGeometry().getSRID()).isEqualTo(4326);
              assertThat(path.getPoints()).usingRecursiveComparison().isEqualTo(points);
              assertThat(path.getSegments()).isEmpty();
              assertThat(path.getExcludedPoints()).isEmpty();
            });
  }

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
    assertThat(searchPathMapper.findAllPaths())
        .allSatisfy(
            found -> {
              assertThat(found.getPoints()).isEmpty();
              assertThat(found.getSegments()).isEmpty();
              assertThat(found.getExcludedPoints()).isEmpty();
            });
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

    // given: 시각 순서와 수집 순서가 다른 GPS 좌표도 같은 경로에 저장한다.
    List<GpsPoint> points =
        List.of(
            gpsPoint("later-clock", "126.913001", "35.162001", "1.25", 4, "2026-04-28T00:00:05Z"),
            gpsPoint(
                "later-clock", "126.913002", "35.162002", "1.75", null, "2026-04-28T00:00:00Z"));
    searchPathMapper.insertGpsPoints(PATH_ID, 0, points, STARTED_AT);

    // when: 조건별 조회와 전체 조회 모두 버전과 하위 객체를 한꺼번에 읽는다.
    for (List<SearchPath> paths :
        List.of(
            searchPathMapper.findPaths(INCIDENT_ID, OP_ID, ACCOUNT_ID),
            searchPathMapper.findAllPaths())) {
      // then: 하위 객체를 중복·누락 없이 조립하고 GPS 수집 순서와 모든 원본 필드를 보존한다.
      assertThat(paths)
          .singleElement()
          .satisfies(
              found -> {
                assertThat(found.getVersion()).isEqualTo(1L);
                assertThat(found.getPoints()).usingRecursiveComparison().isEqualTo(points);
                assertThat(found.getSegments())
                    .usingRecursiveComparison()
                    .withEqualsForType(
                        (left, right) ->
                            left.getSRID() == right.getSRID() && left.equalsExact(right),
                        Geometry.class)
                    .isEqualTo(List.of(segment));
                assertThat(found.getExcludedPoints())
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(excludedPoint));
              });
    }
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
  @DisplayName("GPS 조회는 생성자 접근 예외 없이 수집 순서와 원본 값을 유지한다")
  void saved_gps_points_are_read_without_constructor_access_exceptions(
      @TempDir Path temporaryDirectory) throws IOException {
    // given: 수집 순서가 있는 좌표와 정확도가 없는 좌표를 실제 DB에 저장한다.
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

    // when: 기본 MyBatis의 예외 1건을 대조군으로 기록하고 실제 Mapper로 조회한다.
    Path recordingPath = temporaryDirectory.resolve("gps-query.jfr");
    long threadId = Thread.currentThread().getId();
    List<GpsPoint> found;
    try (Recording recording = new Recording()) {
      recording.enable("jdk.JavaExceptionThrow").withStackTrace();
      recording.start();
      new DefaultObjectFactory().create(GpsPoint.class);
      found = searchPathMapper.findGpsPointsByPathId(PATH_ID);
      recording.stop();
      recording.dump(recordingPath);
    }

    // then: 대조군 외에 생성자 접근 예외가 없고 저장한 순서와 측정값을 보존한다.
    long constructorAccessExceptions =
        RecordingFile.readAllEvents(recordingPath).stream()
            .filter(event -> event.getEventType().getName().equals("jdk.JavaExceptionThrow"))
            .filter(event -> event.getThread().getJavaThreadId() == threadId)
            .filter(
                event ->
                    event
                        .getClass("thrownClass")
                        .getName()
                        .equals("java.lang.IllegalAccessException"))
            .filter(
                event ->
                    event.getStackTrace() != null
                        && event.getStackTrace().getFrames().stream()
                            .anyMatch(
                                frame ->
                                    frame
                                        .getMethod()
                                        .getType()
                                        .getName()
                                        .equals("java.lang.reflect.Constructor")))
            .count();
    assertThat(constructorAccessExceptions)
        .as("기본 MyBatis 대조군의 1건 외에 실제 조회에서 발생한 생성자 접근 예외")
        .isEqualTo(1);
    assertThat(found).hasSize(2);
    assertGpsPoint(found.get(0), points.get(0));
    assertGpsPoint(found.get(1), points.get(1));
    assertThat(found.get(1).getHorizontalAccuracyM()).isNull();
  }

  private void insertLegacySegmentWithGps() {
    insertPath(null);
    jdbcTemplate.update("UPDATE search_path SET version = 41 WHERE id = ?", PATH_ID);
    searchPathMapper.insertGpsPoints(
        PATH_ID,
        0,
        List.of(
            gpsPoint("first", "126.950000", "37.560000", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint("second", "126.950100", "37.560100", "1.25", 4, "2026-04-28T00:00:05Z")),
        STARTED_AT);
    searchPathMapper.insertSegments(
        List.of(
            SearchPathSegment.builder()
                .id(SEGMENT_ID)
                .searchPathId(PATH_ID)
                .movementType(MovementType.FOOT)
                .movementTypeSource(MovementTypeSource.AUTO)
                .geometry(lineString())
                .startedAt(STARTED_AT)
                .endedAt(STARTED_AT.plusSeconds(5))
                .version(7L)
                .createdAt(STARTED_AT)
                .updatedAt(STARTED_AT)
                .build()));
  }

  private void backfillSegmentProgress() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                new ResourceDatabasePopulator(
                        new ClassPathResource(
                            "db/migration/V20261006_002__backfill_search_path_segment_progress.sql"))
                    .execute(jdbcTemplate.getDataSource()));
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
