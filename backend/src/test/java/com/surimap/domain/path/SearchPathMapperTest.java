package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import java.util.stream.IntStream;
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
import org.junit.jupiter.params.provider.CsvSource;
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
  // 구간 묶기 migration에서 바뀌지 않아야 하는 구간 번호다. MANUAL 구간(4), 앞 구간과 합치면 좌표가 120개를 넘어 묶이지 않는 구간(11), 좌표가 120개를 넘는 구간(12)과 그 뒤 구간(13), 다른 경로의 구간(14, 15)이다.
  private static final List<Integer> MERGE_KEPT_SEGMENTS = List.of(4, 11, 12, 13, 14, 15);

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
  @DisplayName("구간 묶기 migration을 실행하면 이어진 같은 이동 유형의 자동 구간만 좌표 120개 안에서 앞 구간에 묶인다")
  void merge_migration_joins_consecutive_auto_segments_of_same_type_within_limit() {
    // given: 묶을 자동 구간, MANUAL 구간, 좌표가 120개를 넘는 구간이 섞인 경로와 묶을 구간이 없는 경로가 있다.
    insertMergeFixture();
    List<Map<String, Object>> gpsBefore = tableRows("search_path_gps_point");
    List<Map<String, Object>> excludedBefore = tableRows("search_path_excluded_point");
    List<Map<String, Object>> pointCountsBefore = pointCountsByMovementType();
    List<String> keptBefore = segmentRows(MERGE_KEPT_SEGMENTS);

    // when: 배포할 구간 묶기 SQL을 실행한다.
    mergeSegments();

    // then: 묶을 수 있는 구간만 앞 구간에 묶이고 나머지 구간과 원본 좌표는 그대로다.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id, start_point_order, end_point_order, movement_type, movement_type_source"
                    + " FROM search_path_segment WHERE search_path_id = ? ORDER BY start_point_order",
                PATH_ID))
        .extracting(
            row -> row.get("id"),
            row -> row.get("start_point_order"),
            row -> row.get("end_point_order"),
            row -> row.get("movement_type"),
            row -> row.get("movement_type_source"))
        .containsExactly(
            tuple(mergeSegmentId(1), 0, 4, "FOOT", "AUTO"),
            tuple(mergeSegmentId(4), 5, 6, "FOOT", "MANUAL"),
            tuple(mergeSegmentId(5), 7, 10, "FOOT", "AUTO"),
            tuple(mergeSegmentId(7), 11, 15, "VEHICLE", "AUTO"),
            tuple(mergeSegmentId(9), 16, 135, "FOOT", "AUTO"),
            tuple(mergeSegmentId(11), 136, 137, "FOOT", "AUTO"),
            tuple(mergeSegmentId(12), 138, 258, "FOOT", "AUTO"),
            tuple(mergeSegmentId(13), 259, 259, "FOOT", "AUTO"));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM search_path_segment WHERE search_path_id = ? ORDER BY start_point_order",
                UUID.class,
                OTHER_PATH_ID))
        .containsExactly(mergeSegmentId(14), mergeSegmentId(15));
    assertThat(segmentRows(MERGE_KEPT_SEGMENTS)).isEqualTo(keptBefore);
    assertThat(pointCountsByMovementType()).isEqualTo(pointCountsBefore);
    assertThat(tableRows("search_path_gps_point")).isEqualTo(gpsBefore);
    assertThat(tableRows("search_path_excluded_point")).isEqualTo(excludedBefore);
  }

  @Test
  @DisplayName("구간을 묶으면 첫 구간 ID가 유지되고, 도형과 시각은 원본 좌표와 측정 시각으로 다시 만들어진다")
  void merge_migration_rebuilds_merged_segment_from_gps_points() {
    // given: 순번 0~4, 7~10, 11~15, 16~135를 여러 구간으로 나눠 저장한 경로가 있다.
    insertMergeFixture();

    // when: 배포할 구간 묶기 SQL을 실행한다.
    mergeSegments();

    // then: 묶음마다 첫 구간이 순번 범위 전체의 원본 좌표와 측정 시각을 가진다.
    // 구간 번호, 시작 순번, 끝 순번 순서다. 원본 GPS의 측정 시각은 순번 n에서 STARTED_AT + n초다.
    int[][] merges = {{1, 0, 4}, {5, 7, 10}, {7, 11, 15}, {9, 16, 135}};
    for (int[] merge : merges) {
      SearchPathSegment merged =
          searchPathMapper.findSegmentById(mergeSegmentId(merge[0])).orElseThrow();
      String segment = "구간 " + merge[0];
      assertThat(merged.getStartIndex()).as(segment).isEqualTo(merge[1]);
      assertThat(merged.getEndIndex()).as(segment).isEqualTo(merge[2]);
      assertThat(merged.getGeometry().getCoordinates())
          .as(segment)
          .containsExactly(
              IntStream.rangeClosed(merge[1], merge[2])
                  .mapToObj(SearchPathMapperTest::mergeFixtureCoordinate)
                  .toArray(Coordinate[]::new));
      assertThat(merged.getStartedAt()).as(segment).isEqualTo(STARTED_AT.plusSeconds(merge[1]));
      assertThat(merged.getEndedAt()).as(segment).isEqualTo(STARTED_AT.plusSeconds(merge[2]));
    }
  }

  @Test
  @DisplayName("구간을 묶은 경로만 버전이 1 오르고, 묶기 전 버전 이후의 변경분을 조회하면 묶은 구간이 담긴다")
  void merge_migration_bumps_versions_only_for_changed_paths_and_exposes_changes() {
    // given: 경로 버전이 10인 경로와, 묶을 구간이 없고 경로 버전이 5인 경로가 있다.
    insertMergeFixture();
    Map<String, Object> otherPathBefore =
        jdbcTemplate.queryForMap("SELECT * FROM search_path WHERE id = ?", OTHER_PATH_ID);

    // when: 배포할 구간 묶기 SQL을 실행한다.
    mergeSegments();

    // then: 바뀐 경로와 묶은 구간만 버전이 오르고, 묶기 전 버전 이후의 변경분을 조회하면 묶은 구간이 담긴다.
    assertThat(searchPathMapper.findPathById(PATH_ID).orElseThrow().getVersion()).isEqualTo(11L);
    assertThat(jdbcTemplate.queryForMap("SELECT * FROM search_path WHERE id = ?", OTHER_PATH_ID))
        .isEqualTo(otherPathBefore);
    assertThat(
            List.of(1, 5, 7, 9).stream()
                .map(n -> searchPathMapper.findSegmentById(mergeSegmentId(n)).orElseThrow())
                .toList())
        .extracting(SearchPathSegment::getVersion, SearchPathSegment::getLastChangedPathVersion)
        .containsExactly(tuple(2L, 11L), tuple(2L, 11L), tuple(2L, 11L), tuple(2L, 11L));
    assertThat(
            searchPathMapper.findBoardSegments(
                List.of(
                    SearchPathBoardSegmentQuery.builder()
                        .pathId(PATH_ID)
                        .lowerVersion(10L)
                        .upperVersion(11L)
                        .limit(100)
                        .build())))
        .extracting(
            SearchPathSegment::getId, SearchPathSegment::getStartIndex, SearchPathSegment::getEndIndex)
        .containsExactly(
            tuple(mergeSegmentId(9), 16, 135),
            tuple(mergeSegmentId(7), 11, 15),
            tuple(mergeSegmentId(5), 7, 10),
            tuple(mergeSegmentId(1), 0, 4));
  }

  @ParameterizedTest
  @CsvSource({
    "segment_gap, gps_covered_once",
    "segment_overlap, gps_covered_once",
    "gps_missing, gps_order_contiguous",
    "progress_missing, segment_progress_known",
    "geometry_mismatch, merged_sources_match_gps",
    "single_point_second_point_mismatch, merged_sources_match_gps"
  })
  @DisplayName("구간 범위나 원본 좌표를 확정할 수 없으면 구간 묶기 migration이 실패하고 아무 행도 바뀌지 않는다")
  void merge_migration_aborts_without_partial_changes_on_unresolved_data(
      String invalidData, String failedRule) {
    // given: 구간 순번에 빈틈이나 겹침이 있거나, GPS 순번이 빠졌거나, 순번을 보완하지 않은 구간이나 원본 GPS와 다른 도형이 있다.
    // 좌표 1개짜리 구간 2의 도형은 첫 점만 GPS 순번 2와 같고 두 번째 점이 다를 수도 있다.
    insertMergeFixture();
    switch (invalidData) {
      case "segment_gap" ->
          jdbcTemplate.update("DELETE FROM search_path_segment WHERE id = ?", mergeSegmentId(2));
      case "segment_overlap" ->
          jdbcTemplate.update(
              "UPDATE search_path_segment SET start_point_order = 2 WHERE id = ?",
              mergeSegmentId(3));
      case "gps_missing" ->
          jdbcTemplate.update(
              "DELETE FROM search_path_gps_point WHERE search_path_id = ? AND point_order = 3",
              PATH_ID);
      case "progress_missing" ->
          jdbcTemplate.update(
              "UPDATE search_path_segment SET start_point_order = NULL, end_point_order = NULL"
                  + " WHERE id = ?",
              mergeSegmentId(13));
      case "geometry_mismatch" ->
          jdbcTemplate.update(
              "UPDATE search_path_segment"
                  + " SET geometry = ST_GeomFromText('LINESTRING(127 37,127 37)',4326) WHERE id = ?",
              mergeSegmentId(2));
      case "single_point_second_point_mismatch" ->
          jdbcTemplate.update(
              "UPDATE search_path_segment SET geometry"
                  + " = ST_GeomFromText('LINESTRING(126.90002 37.5,127 37.5)',4326) WHERE id = ?",
              mergeSegmentId(2));
      default -> throw new IllegalArgumentException(invalidData);
    }
    List<Map<String, Object>> segmentsBefore = tableRows("search_path_segment");
    List<Map<String, Object>> pathsBefore = tableRows("search_path");

    // when: 불확실한 자료에 구간 묶기 SQL을 실행한다.
    assertThatThrownBy(this::mergeSegments)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class,
            error -> {
              assertThat(error.getSQLState()).isEqualTo("23514");
              assertThat(error.getMessage()).contains("(" + failedRule + ", f)");
            });

    // then: 정상으로 보이는 경로와 구간도 바뀌지 않는다.
    assertThat(tableRows("search_path_segment")).isEqualTo(segmentsBefore);
    assertThat(tableRows("search_path")).isEqualTo(pathsBefore);
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
  @DisplayName("상황판 좌표는 요청한 범위 순서와 범위 안 순번대로 읽고 측정 시각의 저장 범위를 보존한다")
  void board_points_follow_requested_range_order_and_keep_stored_time_range() {
    // given: 한 경로에 좌표 0..5를 저장하고 처음과 끝 좌표를 1970년 이전 시각과 저장 상한 시각으로 바꾼다.
    insertPath(null);
    searchPathMapper.insertGpsPoints(
        PATH_ID,
        0,
        List.of(
            gpsPoint("board-0", "126.900000", "35.100000", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint("board-1", "126.900100", "35.100000", "1.25", 4, "2026-04-28T00:00:05Z"),
            gpsPoint("board-2", "126.900200", "35.100000", "1.25", 4, "2026-04-28T00:00:10Z"),
            gpsPoint("board-3", "126.900300", "35.100000", "1.25", 4, "2026-04-28T00:00:15Z"),
            gpsPoint("board-4", "126.900400", "35.100000", "1.25", 4, "2026-04-28T00:00:20Z"),
            gpsPoint("board-5", "-0.000001", "-35.100000", "1.25", 4, "2026-04-28T00:00:25Z")),
        STARTED_AT);
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = '1969-12-31 23:59:59.999999+00'"
            + " WHERE point_order = 0");
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = '294276-12-31 23:59:59.999999+00'"
            + " WHERE point_order = 5");

    // when: 같은 경로의 떨어진 두 범위를 뒤 범위부터 요청한다.
    List<SearchPathBoardPoint> found =
        searchPathMapper.findBoardPoints(
            SearchPathBoardPointRanges.from(
                List.of(
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(4)
                        .endIndex(5)
                        .build(),
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(0)
                        .endIndex(1)
                        .build())));

    // then: 범위 순서(1부터)와 범위 안 순번대로 읽고, 저장한 좌표와 시각을 그대로 돌려준다.
    assertThat(found)
        .extracting(SearchPathBoardPoint::getRangeOrder, SearchPathBoardPoint::getPointOrder)
        .containsExactly(tuple(1, 4), tuple(1, 5), tuple(2, 0), tuple(2, 1));
    assertThat(found.get(1).getLon()).isEqualTo(-0.000001);
    assertThat(found.get(1).getLat()).isEqualTo(-35.1);
    assertThat(found.get(1).getClientTs())
        .isEqualTo(Instant.parse("+294276-12-31T23:59:59.999999Z"));
    assertThat(found.get(2).getClientTs()).isEqualTo(Instant.parse("1969-12-31T23:59:59.999999Z"));
  }

  @Test
  @DisplayName("상황판 좌표 범위가 5,000개를 넘어도 한 번에 빠짐없이 범위 순서대로 읽는다")
  void board_points_read_more_than_five_thousand_ranges_at_once() {
    // given: 한 경로에 좌표 0..10001을 저장하고 짝수 순번만 하나씩 떨어진 범위 5,001개로 만든다.
    insertPath(null);
    jdbcTemplate.update(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT ?,n,'many-'||n,'2026-04-28T00:00:00Z'::timestamptz + n*interval '1 second',
          126.9,35.1,1,now()
        FROM generate_series(0,10001) n
        """,
        PATH_ID);
    List<SearchPathSegment> ranges =
        IntStream.range(0, 5001)
            .mapToObj(
                i ->
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(i * 2)
                        .endIndex(i * 2)
                        .build())
            .toList();

    // when: 범위 5,001개를 한 번에 요청한다.
    List<SearchPathBoardPoint> found =
        searchPathMapper.findBoardPoints(SearchPathBoardPointRanges.from(ranges));

    // then: 범위마다 좌표 하나씩, 요청 순서대로 받는다.
    assertThat(found).hasSize(5001);
    assertThat(found)
        .allSatisfy(
            point -> assertThat(point.getPointOrder()).isEqualTo((point.getRangeOrder() - 1) * 2));
    assertThat(found)
        .extracting(SearchPathBoardPoint::getRangeOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(1, 5001).boxed().toList());
  }

  @Test
  @DisplayName("상황판 좌표 묶음은 요청한 범위마다 한 행으로 순번 순서의 배열과 기준 밖 시각 여부를 돌려준다")
  void board_point_runs_return_one_row_per_requested_range_with_ordered_arrays() {
    // given: 한 경로에 좌표 0..5를 저장하고 처음 좌표는 1970년 이전, 끝 좌표는 저장 상한 시각으로 바꾼다.
    insertPath(null);
    searchPathMapper.insertGpsPoints(
        PATH_ID,
        0,
        List.of(
            gpsPoint("run-0", "126.900000", "35.100000", "1.25", 4, "2026-04-28T00:00:00Z"),
            gpsPoint("run-1", "126.900100", "35.100000", "1.25", 4, "2026-04-28T00:00:05Z"),
            gpsPoint("run-2", "126.900200", "35.100000", "1.25", 4, "2026-04-28T00:00:10Z"),
            gpsPoint("run-3", "126.900300", "35.100000", "1.25", 4, "2026-04-28T00:00:15Z"),
            gpsPoint("run-4", "126.900400", "35.100000", "1.25", 4, "2026-04-28T00:00:20Z"),
            gpsPoint("run-5", "-0.000001", "-35.100000", "1.25", 4, "2026-04-28T00:00:25Z")),
        STARTED_AT);
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = '1969-12-31 23:59:59.999999+00'"
            + " WHERE point_order = 0");
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = '294276-12-31 23:59:59.999999+00'"
            + " WHERE point_order = 5");

    // when: 뒤 범위(4..5), 앞 범위(0..1), 좌표가 없는 범위(8..9), 끝이 비는 범위(5..6) 순서로 요청한다.
    List<SearchPathBoardPointRun> found =
        searchPathMapper.findBoardPointRuns(
            SearchPathBoardPointRanges.from(
                List.of(
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(4)
                        .endIndex(5)
                        .build(),
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(0)
                        .endIndex(1)
                        .build(),
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(8)
                        .endIndex(9)
                        .build(),
                    SearchPathSegment.builder()
                        .searchPathId(PATH_ID)
                        .startIndex(5)
                        .endIndex(6)
                        .build())));

    // then: 좌표가 있는 범위만 요청 순서대로 한 행씩 받고, 배열은 저장 값을 순번 순서로 담는다.
    assertThat(found)
        .extracting(
            SearchPathBoardPointRun::getRangeOrder,
            SearchPathBoardPointRun::getStartPointOrder,
            SearchPathBoardPointRun::getPointCount,
            SearchPathBoardPointRun::isHasOutOfRangeClientTs)
        .containsExactly(tuple(1, 4, 2, true), tuple(2, 0, 2, false), tuple(4, 5, 1, true));
    assertThat(found.get(0).getLons()).containsExactly(126.9004, -0.000001);
    assertThat(found.get(0).getLats()).containsExactly(35.1, -35.1);
    assertThat(found.get(0).getClientTs(0)).isEqualTo(Instant.parse("2026-04-28T00:00:20Z"));
    assertThat(found.get(1).getLons()).containsExactly(126.9, 126.9001);
    assertThat(found.get(1).getClientTs(0)).isEqualTo(Instant.parse("1969-12-31T23:59:59.999999Z"));
    assertThat(found.get(1).getClientTs(1)).isEqualTo(Instant.parse("2026-04-28T00:00:05Z"));
    assertThat(found.get(1).hasAllPointsThrough(1)).isTrue();
    assertThat(found.get(2).hasAllPointsThrough(6)).isFalse();
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

  private void insertMergeFixture() {
    jdbcTemplate.update(
        """
        INSERT INTO search_path(id,duty_shift_id,account_id,status,version,started_at,created_at,updated_at)
        VALUES (?,?,?,'RECORDING',10,'2026-04-28T00:00:00Z','2026-04-28T00:00:00Z','2026-04-28T00:00:00Z'),
          (?,?,?,'ENDED',5,'2026-04-28T00:00:00Z','2026-04-28T00:00:00Z','2026-04-28T00:00:00Z')
        """,
        PATH_ID,
        DUTY_SHIFT_ID,
        ACCOUNT_ID,
        OTHER_PATH_ID,
        DUTY_SHIFT_ID,
        ACCOUNT_ID);
    insertMergeGpsPoints(PATH_ID, 259);
    insertMergeGpsPoints(OTHER_PATH_ID, 5);
    jdbcTemplate.update(
        "INSERT INTO search_path_excluded_point(id,search_path_id,point_id,reason,client_ts)"
            + " VALUES (?,?,'excluded','low_accuracy','2026-04-28T00:00:01Z')",
        EXCLUDED_POINT_ID,
        PATH_ID);
    // 번호, 시작 순번, 끝 순번, 이동 유형, 분류 출처, 자체 버전, 마지막 변경 경로 버전 순서다.
    insertMergeSegment(PATH_ID, 1, 0, 1, "FOOT", "AUTO", 1, 3);
    insertMergeSegment(PATH_ID, 2, 2, 2, "FOOT", "AUTO", 1, 4);
    insertMergeSegment(PATH_ID, 3, 3, 4, "FOOT", "AUTO", 2, 5);
    insertMergeSegment(PATH_ID, 4, 5, 6, "FOOT", "MANUAL", 3, 6);
    insertMergeSegment(PATH_ID, 5, 7, 8, "FOOT", "AUTO", 1, 6);
    insertMergeSegment(PATH_ID, 6, 9, 10, "FOOT", "AUTO", 1, 7);
    insertMergeSegment(PATH_ID, 7, 11, 13, "VEHICLE", "AUTO", 1, 7);
    insertMergeSegment(PATH_ID, 8, 14, 15, "VEHICLE", "AUTO", 1, 8);
    insertMergeSegment(PATH_ID, 9, 16, 134, "FOOT", "AUTO", 1, 8);
    insertMergeSegment(PATH_ID, 10, 135, 135, "FOOT", "AUTO", 1, 9);
    insertMergeSegment(PATH_ID, 11, 136, 137, "FOOT", "AUTO", 1, 9);
    insertMergeSegment(PATH_ID, 12, 138, 258, "FOOT", "AUTO", 1, 10);
    insertMergeSegment(PATH_ID, 13, 259, 259, "FOOT", "AUTO", 1, 10);
    insertMergeSegment(OTHER_PATH_ID, 14, 0, 2, "FOOT", "AUTO", 1, 4);
    insertMergeSegment(OTHER_PATH_ID, 15, 3, 5, "VEHICLE", "AUTO", 1, 5);
  }

  private void insertMergeGpsPoints(UUID pathId, int lastOrder) {
    jdbcTemplate.update(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps)
        SELECT ?,n,'p'||n,'2026-04-28T00:00:00Z'::timestamptz+n*interval '1 second',126.9+n*0.00001,37.5,1.25
        FROM generate_series(0,?) n
        """,
        pathId,
        lastOrder);
  }

  // 앱에서 저장할 때와 같게, 순번 범위의 원본 좌표로 도형과 시각을 만든다. 좌표 하나짜리 도형에는 같은 좌표를 두 번 넣는다.
  private void insertMergeSegment(
      UUID pathId,
      int number,
      int start,
      int end,
      String movementType,
      String source,
      long version,
      long lastChangedPathVersion) {
    jdbcTemplate.update(
        """
        INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
          last_changed_path_version,movement_type,movement_type_source,geometry,started_at,ended_at,
          corrected_by_account_id,corrected_at,version,created_at,updated_at)
        SELECT ?,?,?,?,?,?,?::text,
          ST_SetSRID(ST_MakeLine(CASE WHEN count(*) = 1 THEN array_agg(g.point) || array_agg(g.point)
            ELSE array_agg(g.point ORDER BY g.point_order) END),4326),
          min(g.client_ts),max(g.client_ts),
          CASE WHEN ?::text = 'MANUAL' THEN '63000000-0000-0000-0000-000000002621'::uuid END,
          CASE WHEN ?::text = 'MANUAL' THEN '2026-04-28T01:00:00Z'::timestamptz END,
          ?,'2026-04-28T00:00:00Z','2026-04-28T00:00:00Z'
        FROM (SELECT point_order,client_ts,ST_MakePoint(lon::float8,lat::float8) AS point
          FROM search_path_gps_point WHERE search_path_id = ? AND point_order BETWEEN ? AND ?) g
        """,
        mergeSegmentId(number),
        pathId,
        start,
        end,
        lastChangedPathVersion,
        movementType,
        source,
        source,
        source,
        version,
        pathId,
        start,
        end);
  }

  private List<Map<String, Object>> tableRows(String table) {
    return jdbcTemplate.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2");
  }

  private List<Map<String, Object>> pointCountsByMovementType() {
    return jdbcTemplate.queryForList(
        "SELECT search_path_id, movement_type, sum(end_point_order - start_point_order + 1) AS points"
            + " FROM search_path_segment GROUP BY search_path_id, movement_type ORDER BY 1, 2");
  }

  private List<String> segmentRows(List<Integer> numbers) {
    return numbers.stream()
        .map(
            number ->
                jdbcTemplate.queryForObject(
                    "SELECT to_jsonb(s)::text FROM search_path_segment s WHERE id = ?",
                    String.class,
                    mergeSegmentId(number)))
        .toList();
  }

  private void mergeSegments() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                new ResourceDatabasePopulator(
                        new ClassPathResource(
                            "db/migration/V20261102_001__merge_search_path_segments_by_movement_type.sql"))
                    .execute(jdbcTemplate.getDataSource()));
  }

  private static UUID mergeSegmentId(int number) {
    return UUID.fromString("75000000-0000-0000-0000-%012d".formatted(number));
  }

  private static Coordinate mergeFixtureCoordinate(int pointOrder) {
    return new Coordinate(
        new BigDecimal("126.9")
            .add(new BigDecimal("0.00001").multiply(BigDecimal.valueOf(pointOrder)))
            .doubleValue(),
        37.5);
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
