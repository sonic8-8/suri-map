package com.surimap.api.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.path.response.SearchPathSegmentsQueryResponse;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest.PathProgress;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse;
import com.surimap.common.auth.*;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Sql("/sql/path/search-path-context.sql")
@Import(SearchPathBoardServiceTest.SnapshotConfig.class)
@TestPropertySource(
    properties = {
      "surimap.board.search-path.max-paths=${path-query-test.max-paths:2}",
      "surimap.board.search-path.max-segments=${path-query-test.max-segments:2}",
      "surimap.board.search-path.max-coordinates=${path-query-test.max-coordinates:120}",
      "surimap.board.search-path.segments-per-path=${path-query-test.segments-per-path:1}",
      "surimap.board.search-path.max-known-paths=1000"
    })
class SearchPathBoardServiceTest extends PostGisIntegrationTestSupport {
  private static final UUID INCIDENT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT = UUID.fromString("62000000-0000-0000-0000-000000002621");
  @Autowired private SearchPathBoardService service;
  @Autowired private SnapshotPause snapshotPause;
  @Autowired private StatementCounter statementCounter;
  @Autowired private FetchSizeRecorder fetchSizeRecorder;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @Tag("performance")
  @DisplayName("누적 경로 468개의 최초 구간 조회 시간과 직렬화 크기를 측정한다")
  void measure_first_page_with_accumulated_paths() throws Exception {
    // given: 468개 경로 각각에 720개 GPS와 120개 구간을 저장한다.
    // 계정·근무는 공용 fixture를 재사용한다. 업무폰 468대의 쓰기 부하 시험은 아니다.
    seedPath();
    jdbcTemplate.update("UPDATE search_path SET status = 'ENDED'");
    jdbcTemplate.execute(
        """
        INSERT INTO search_path(id,duty_shift_id,account_id,status,version,started_at)
        SELECT md5('board-measure-'||n)::uuid,duty_shift_id,account_id,'ENDED',121,started_at
        FROM search_path CROSS JOIN generate_series(1,468) n
        """);
    jdbcTemplate.execute(
        "DELETE FROM search_path WHERE id = 'ffffffff-ffff-ffff-ffff-ffffffffffff'");
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps)
        SELECT p.id,n,'p'||n,'2026-04-28T00:00:00Z'::timestamptz+n*interval '2.5 seconds',
          126.9+n*0.00001,35.1,1 FROM search_path p CROSS JOIN generate_series(0,719) n
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
          last_changed_path_version,movement_type,movement_type_source,geometry,version,started_at,ended_at)
        SELECT md5(p.id::text||'-'||n)::uuid,p.id,n*6,n*6+5,n+2,'FOOT','AUTO',
          ST_SetSRID(ST_MakeLine(ARRAY(SELECT ST_MakePoint(126.9+(n*6+i)*0.00001,35.1)
            FROM generate_series(0,5) i ORDER BY i)),4326),1,
          '2026-04-28T00:00:00Z'::timestamptz+n*interval '15 seconds',
          '2026-04-28T00:00:00Z'::timestamptz+n*interval '15 seconds'+interval '12.5 seconds'
        FROM search_path p CROSS JOIN generate_series(0,119) n
        """);
    jdbcTemplate.execute("ANALYZE search_path");
    jdbcTemplate.execute("ANALYZE search_path_segment");
    jdbcTemplate.execute("ANALYZE search_path_gps_point");
    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM search_path_gps_point", Long.class))
        .isEqualTo(336960L);
    // when: 두 번 예열한 뒤 첫 페이지를 다섯 번 독립 조회한다.
    for (int run = 0; run < 7; run++) {
      long start = System.nanoTime();
      SearchPathPageServiceResponse response = service.getSearchPathSegments(request(List.of(OP)));
      long queried = System.nanoTime();
      // 공개 응답 변환·직렬화만 측정한다. Controller 호출·HTTP 네트워크·브라우저 시간은 포함하지 않는다.
      byte[] json = objectMapper.writeValueAsBytes(SearchPathSegmentsQueryResponse.from(response));
      long serialized = System.nanoTime();
      int segments = response.getPaths().stream().mapToInt(path -> path.getSegments().size()).sum();
      int coordinates =
          response.getPaths().stream()
              .flatMap(path -> path.getSegments().stream())
              .mapToInt(segment -> segment.getCoordinates().size())
              .sum();
      // then: 전체 이력은 남아 있고, 각 경로의 마지막 좌표를 포함한다.
      assertThat(response.isHasMore()).isTrue();
      assertThat(response.getPaths())
          .allSatisfy(
              path -> {
                assertThat(path.getBaselineVersion()).isEqualTo(121L);
                assertThat(path.getSegments().get(0).getEndPointOrder()).isEqualTo(719);
              });
      if (run >= 2) {
        System.out.printf(
            "PAGE_MEASURE run=%d query_ms=%.3f serialize_ms=%.3f response_json_bytes=%d paths=%d segments=%d coordinates=%d%n",
            run - 1,
            (queried - start) / 1e6,
            (serialized - queried) / 1e6,
            json.length,
            response.getPaths().size(),
            segments,
            coordinates);
      }
    }
  }

  @Test
  @DisplayName("조회 도중 새 구간이 커밋돼도 최초 페이지와 기준 버전은 같은 시점을 유지한다")
  void concurrent_append_does_not_mix_snapshot_versions() throws Exception {
    // given: 경로 메타데이터 조회 직후 잠시 멈추도록 한다.
    seedPath();
    snapshotPause.start();
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      Future<SearchPathPageServiceResponse> reading =
          worker.submit(() -> service.getSearchPathSegments(request(List.of(OP))));
      assertThat(snapshotPause.loaded.await(10, TimeUnit.SECONDS)).isTrue();
      // when: 다른 DB 트랜잭션으로 좌표·구간·버전을 함께 추가한다.
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                jdbcTemplate.update("UPDATE search_path SET version = 4");
                jdbcTemplate.execute(
                    """
            INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps)
            SELECT 'ffffffff-ffff-ffff-ffff-ffffffffffff',n,'p'||n,now(),126.9+n*0.0001,35.1,1
            FROM generate_series(4,5) n
            """);
                jdbcTemplate.execute(
                    """
            INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
              last_changed_path_version,movement_type,movement_type_source,geometry,version,started_at,ended_at)
            VALUES ('00000000-0000-0000-0000-000000000003','ffffffff-ffff-ffff-ffff-ffffffffffff',
              4,5,4,'FOOT','AUTO',ST_GeomFromText('LINESTRING(126.9004 35.1,126.9005 35.1)',4326),1,now(),now())
            """);
              });
      snapshotPause.resume.countDown();
      // then: 진행 중이던 응답에는 버전 3의 뒤 구간만 담긴다.
      SearchPathPageServiceResponse response = reading.get(10, TimeUnit.SECONDS);
      assertThat(response.getPaths().get(0).getBaselineVersion()).isEqualTo(3L);
      assertThat(response.getPaths().get(0).getSegments().get(0).getStartPointOrder()).isEqualTo(2);
      assertThat(
              service
                  .getSearchPathSegments(request(List.of(OP)))
                  .getPaths()
                  .get(0)
                  .getBaselineVersion())
          .isEqualTo(4L);
    } finally {
      snapshotPause.resume.countDown();
      worker.shutdownNow();
    }
  }

  @Test
  @DisplayName("응답 경로 한도를 넘으면 미반환 경로부터 다음 조회를 이어간다")
  void path_budget_preserves_unreturned_paths_and_round_robin() {
    // given: 동일 차수에 경로 세 개가 있다. 한 응답은 두 경로까지만 받는다.
    seedPath();
    jdbcTemplate.update("UPDATE search_path SET status = 'ENDED'");
    jdbcTemplate.execute(
        """
        INSERT INTO search_path(id,duty_shift_id,account_id,status,version,started_at)
        SELECT ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
          duty_shift_id,account_id,'ENDED',1,started_at
        FROM search_path CROSS JOIN generate_series(10,11) n
        """);
    // when: 첫 응답의 진행과 순환 위치를 재전송한다.
    SearchPathPageServiceResponse first = service.getSearchPathSegments(request(List.of(OP)));
    SearchPathPageServiceResponse second =
        service.getSearchPathSegments(continuation(first, false));
    // then: 빈 경로도 처리하고 아직 응답하지 않은 경로를 누락하지 않는다.
    assertThat(first.getPaths()).hasSize(2);
    assertThat(first.isHasMore()).isTrue();
    assertThat(first.getNextSearchPathId())
        .isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    assertThat(second.getPaths()).hasSize(1);
    assertThat(second.getPaths().get(0).getSegments().get(0).getStartPointOrder()).isEqualTo(2);
  }

  @Test
  @DisplayName("구간 하나가 전체 좌표 한도를 넘으면 빈 페이지를 반복하지 않고 거부한다")
  void oversized_segment_returns_explicit_error() {
    // given: 시험 응답 한도 120보다 큰 구간이 저장돼 있다.
    seedPath();
    jdbcTemplate.update(
        "UPDATE search_path_segment SET end_point_order = 122 WHERE start_point_order = 2");
    // when & then: 121개 좌표 구간을 분할하거나 조용히 건너뛰지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of(OP))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode())
                    .isEqualTo(ErrorCode.SEARCH_PATH_SEGMENT_TOO_LARGE));
  }

  @Test
  @DisplayName("저장된 좌표 숫자와 측정 시각을 가장 가까운 double과 마이크로초까지 그대로 반환한다")
  void board_page_preserves_stored_coordinate_digits_and_microsecond_time() {
    // given: 끝 구간 두 좌표가 소수 15자리 좌표와 마이크로초 측정 시각을 가진다.
    seedPath();
    jdbcTemplate.update(
        """
        UPDATE search_path_gps_point SET lon = 126.978034567890123, lat = 37.566512345678901,
          client_ts = '2026-04-28T00:00:01.123456Z'
        WHERE point_order = 2
        """);
    jdbcTemplate.update(
        """
        UPDATE search_path_gps_point SET lon = 127.000000000000001, lat = 37.499999999999999,
          client_ts = '2026-04-28T00:00:06.000001Z'
        WHERE point_order = 3
        """);
    // when: 최초 이력의 끝 구간을 조회한다.
    SearchPathPageServiceResponse.Segment segment =
        service.getSearchPathSegments(request(List.of(OP))).getPaths().get(0).getSegments().get(0);
    // then: 저장 문자열을 double로 바꾼 값, 저장 시각과 같다.
    assertThat(segment.getCoordinates())
        .containsExactly(
            List.of(
                Double.parseDouble("126.978034567890123"),
                Double.parseDouble("37.566512345678901")),
            List.of(
                Double.parseDouble("127.000000000000001"),
                Double.parseDouble("37.499999999999999")));
    assertThat(segment.getStartedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:01.123456Z"));
    assertThat(segment.getEndedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:06.000001Z"));
  }

  @Test
  @DisplayName("무한 측정 시각은 실패하지 않고 JDBC가 OffsetDateTime으로 읽는 값과 같게 반환한다")
  void board_page_returns_infinite_measurement_time_like_jdbc_offset_date_time() {
    // given: 끝 구간을 좌표 2..4로 늘리고 가운데 좌표는 -infinity, 끝 좌표는 infinity로 둔다.
    seedPath();
    jdbcTemplate.update(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff',4,'p4','infinity',126.9004,35.1,1,now())
        """);
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = '-infinity' WHERE point_order = 3");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET end_point_order = 4 WHERE start_point_order = 2");
    // when: 최초 이력의 끝 구간을 조회한다.
    SearchPathPageServiceResponse.Segment segment =
        service.getSearchPathSegments(request(List.of(OP))).getPaths().get(0).getSegments().get(0);
    // then: 세 좌표를 모두 받고, 시작은 유한 시각, 끝은 OffsetDateTime.MAX의 Instant다.
    assertThat(segment.getCoordinates()).hasSize(3);
    assertThat(segment.getStartedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:10Z"));
    assertThat(segment.getEndedAt()).isEqualTo(OffsetDateTime.MAX.toInstant());
  }

  @Test
  @DisplayName("한 페이지의 여러 경로 중 기준 밖 측정 시각이 있는 경로만 좌표마다 다시 읽어도 경로별 좌표와 시각을 지킨다")
  void board_page_rereads_only_range_with_out_of_range_time_and_keeps_each_path() {
    // given: 앞 경로(e)는 보통 시각이고, 뒤 경로(f)의 끝 구간 마지막 좌표만 infinity다.
    seedPath();
    seedSecondPath();
    jdbcTemplate.update(
        "UPDATE search_path_gps_point SET client_ts = 'infinity'"
            + " WHERE search_path_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff' AND point_order = 3");
    // when: 두 경로를 한 페이지로 조회한다.
    List<SearchPathPageServiceResponse.Path> paths =
        service.getSearchPathSegments(request(List.of(OP))).getPaths();
    // then: 앞 경로는 배열 값 그대로, 뒤 경로는 다시 읽은 좌표와 OffsetDateTime.MAX의 Instant를 받는다.
    assertThat(paths)
        .extracting(SearchPathPageServiceResponse.Path::getId)
        .containsExactly(
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    SearchPathPageServiceResponse.Segment first = paths.get(0).getSegments().get(0);
    assertThat(first.getCoordinates())
        .containsExactly(List.of(126.95, 35.1), List.of(126.9501, 35.1));
    assertThat(first.getStartedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:00Z"));
    assertThat(first.getEndedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:05Z"));
    SearchPathPageServiceResponse.Segment second = paths.get(1).getSegments().get(0);
    assertThat(second.getCoordinates())
        .containsExactly(List.of(126.9002, 35.1), List.of(126.9003, 35.1));
    assertThat(second.getStartedAt()).isEqualTo(Instant.parse("2026-04-28T00:00:10Z"));
    assertThat(second.getEndedAt()).isEqualTo(OffsetDateTime.MAX.toInstant());
  }

  @Test
  @DisplayName("앞 경로가 좌표 한도를 일부 쓰면 남은 한도를 넘는 뒤 경로는 다음 조회로 넘긴다")
  void coordinate_budget_is_shared_across_paths_in_one_page() {
    // given: 시험 응답 한도 120에서 앞 경로 구간이 80개, 뒤 경로 끝 구간이 60개 좌표다.
    seedPath();
    seedSecondPath();
    jdbcTemplate.update(
        "UPDATE search_path_segment SET end_point_order = 79"
            + " WHERE search_path_id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET end_point_order = 61"
            + " WHERE search_path_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff'"
            + " AND start_point_order = 2");
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',n,'e'||n,
          '2026-04-28T00:00:00Z'::timestamptz + n*interval '5 seconds',126.95+n*0.0001,35.1,1,now()
        FROM generate_series(2,79) n
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT 'ffffffff-ffff-ffff-ffff-ffffffffffff',n,'p'||n,
          '2026-04-28T00:00:00Z'::timestamptz + n*interval '5 seconds',126.9+n*0.0001,35.1,1,now()
        FROM generate_series(4,61) n
        """);
    // when: 첫 응답의 진행과 순환 위치를 재전송한다.
    SearchPathPageServiceResponse first = service.getSearchPathSegments(request(List.of(OP)));
    SearchPathPageServiceResponse second =
        service.getSearchPathSegments(continuation(first, false));
    // then: 첫 응답은 앞 경로만 담고, 뒤 경로의 끝 구간은 다음 응답에서 받는다.
    assertThat(first.getPaths())
        .extracting(SearchPathPageServiceResponse.Path::getId)
        .containsExactly(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
    assertThat(first.getPaths().get(0).getSegments().get(0).getCoordinates()).hasSize(80);
    assertThat(first.isHasMore()).isTrue();
    assertThat(first.getNextSearchPathId())
        .isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    assertThat(second.getPaths().get(0).getId())
        .isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    assertThat(second.getPaths().get(0).getSegments().get(0).getStartPointOrder()).isEqualTo(2);
    assertThat(second.getPaths().get(0).getSegments().get(0).getCoordinates()).hasSize(60);
  }

  @Test
  @DisplayName("사건 배정이 없는 웹 계정은 경로가 없어도 조회를 거부한다")
  void unassigned_account_cannot_query_empty_scope() {
    // given: 다른 계정에는 현재 사건 배정이 없다.
    SearchPathPageServiceRequest request =
        request(List.of()).toBuilder()
            .authentication(
                new SuriMapAuthentication(
                    "63000000-0000-0000-0000-000000002621",
                    AccountType.PATROL_CAR,
                    OrganizationType.POLICE_SUBSTATION,
                    Channel.WEB,
                    null,
                    List.of()))
            .build();
    // when & then: 공통 채널 검사만으로 접근을 허용하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INCIDENT_ACCESS_DENIED));
  }

  @Test
  @DisplayName("이력 다음 페이지는 앞 구간만 반환하고 최초 기준 버전을 새 값으로 덮어쓰지 않는다")
  void history_continuation_returns_only_older_segment() {
    // given: 끝 구간을 정상 반영했다.
    seedPath();
    SearchPathPageServiceResponse first = service.getSearchPathSegments(request(List.of(OP)));
    // when: 서버가 반환한 이력 진행 정보를 그대로 보낸다.
    SearchPathPageServiceResponse next = service.getSearchPathSegments(continuation(first, false));
    // then: 앞 구간만 수신하고 이력 조회를 마친다. baseline은 최초 응답에서만 정한다.
    assertThat(next.getPaths().get(0).getSegments().get(0).getStartPointOrder()).isZero();
    assertThat(next.getPaths().get(0).getBaselineVersion()).isNull();
    assertThat(next.getPaths().get(0).getCompleted()).isTrue();
    assertThat(next.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("변경분에서 처음 발견한 경로는 좌표 없이 최초 구간 조회가 필요함을 알린다")
  void changes_discovers_path_without_loading_coordinates() {
    // given: 웹이 아직 모르는 경로가 있다.
    seedPath();
    // when: 알려진 경로 없이 변경분을 조회한다.
    SearchPathPageServiceResponse response = service.getSearchPathChanges(request(List.of(OP)));
    // then: 최초 로딩 기준과 변경 진행은 없으며 별도 이력 조회로 넘긴다.
    assertThat(response.getPaths()).hasSize(1);
    assertThat(response.getPaths().get(0).getBaselineVersion()).isNull();
    assertThat(response.getPaths().get(0).getCompleted()).isNull();
    assertThat(response.getPaths().get(0).getSegments()).isEmpty();
    assertThat(response.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("페이지 사이 보정이 회차 상한을 넘으면 다음 회차에서 해당 구간을 회수한다")
  void correction_above_fixed_target_is_received_in_next_round() {
    // given: 버전 1 이후 두 구간 중 뒤 구간만 받았다. 이번 회차 상한은 3이다.
    seedPath();
    SearchPathPageServiceRequest start =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(1L)
                        .build()))
            .build();
    SearchPathPageServiceResponse first = service.getSearchPathChanges(start);
    assertThat(first.getPaths().get(0).getAppliedVersion()).isNull();
    assertThat(first.getPaths().get(0).getTargetVersion()).isEqualTo(3L);
    jdbcTemplate.update("UPDATE search_path SET version = 4");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET version = 2, last_changed_path_version = 4 WHERE start_point_order = 0");
    // when: 상한 3의 남은 페이지를 마친 뒤, 완료한 진행으로 다음 회차를 연다.
    SearchPathPageServiceResponse completed =
        service.getSearchPathChanges(continuation(first, true));
    SearchPathPageServiceResponse recovered =
        service.getSearchPathChanges(continuation(completed, true));
    // then: 첫 회차에서는 최신 상태만 알리고, 다음 회차에서 보정 구간을 누락 없이 받는다.
    assertThat(completed.getPaths().get(0).getSegments()).isEmpty();
    assertThat(completed.getPaths().get(0).getAppliedVersion()).isEqualTo(3L);
    assertThat(completed.isHasMore()).isTrue();
    assertThat(recovered.getPaths().get(0).getAppliedVersion()).isEqualTo(4L);
    assertThat(recovered.getPaths().get(0).getSegments()).hasSize(1);
    assertThat(recovered.getPaths().get(0).getSegments().get(0).getVersion()).isEqualTo(2L);
    assertThat(recovered.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("이력 페이지의 경로 SQL 실행 수는 응답에 담긴 경로·구간 수와 무관하다")
  void segments_page_statement_count_does_not_grow_with_returned_paths() {
    // given: 경로 하나만 있는 사건의 첫 페이지 SQL 수를 센다.
    seedPath();
    statementCounter.start();
    SearchPathPageServiceResponse one = service.getSearchPathSegments(request(List.of(OP)));
    int singlePathStatements = statementCounter.stop();
    assertThat(one.getPaths()).hasSize(1);
    // when: 같은 차수에 종료된 경로를 하나 더 두고 첫 페이지를 다시 조회한다.
    seedSecondPath();
    statementCounter.start();
    SearchPathPageServiceResponse two = service.getSearchPathSegments(request(List.of(OP)));
    int twoPathStatements = statementCounter.stop();
    // then: 응답 경로와 구간은 늘어도 경로 SQL 실행 수는 같다.
    assertThat(two.getPaths()).hasSize(2);
    assertThat(two.getPaths()).allSatisfy(path -> assertThat(path.getSegments()).hasSize(1));
    assertThat(twoPathStatements).isEqualTo(singlePathStatements);
  }

  @Test
  @DisplayName("이력·변경분 페이지는 구간과 좌표 결과를 기본 100행 단위로 나눠 받지 않는다")
  void board_segments_and_points_are_not_fetched_in_default_row_batches() {
    // given: 이력과 변경분 조회 모두 구간·좌표 SQL을 실행하는 경로를 둔다.
    seedPath();
    jdbcTemplate.update("UPDATE search_path SET version = 4");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET last_changed_path_version = 4 WHERE start_point_order = 2");
    SearchPathPageServiceRequest changes =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(3L)
                        .build()))
            .build();
    // when: 트랜잭션 안에서 이력과 변경분 페이지를 조회한다.
    SearchPathPageServiceResponse changed;
    Map<String, List<Integer>> fetchSizes;
    fetchSizeRecorder.start();
    try {
      service.getSearchPathSegments(request(List.of(OP)));
      changed = service.getSearchPathChanges(changes);
    } finally {
      fetchSizes = fetchSizeRecorder.stop();
    }
    assertThat(changed.getPaths())
        .singleElement()
        .satisfies(path -> assertThat(path.getSegments()).hasSize(1));
    // then: 두 SQL은 100행마다 DB와 다시 왕복하지 않도록 10000행 단위로 받는다.
    assertThat(fetchSizes.get("findBoardSegments")).hasSize(2).containsOnly(10000);
    assertThat(fetchSizes.get("findBoardPointRuns")).hasSize(2).containsOnly(10000);
  }

  @Test
  @DisplayName("응답에 담을 구간의 좌표가 일부 없으면 빈 좌표로 반환하지 않고 준비되지 않음으로 거부한다")
  void missing_point_inside_returned_segment_is_not_ready() {
    // given: 첫 페이지에 담길 끝 구간(2..3)의 좌표 하나가 없다.
    seedPath();
    jdbcTemplate.update(
        "DELETE FROM search_path_gps_point WHERE search_path_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff' AND point_order = 3");
    // when/then: 구간 좌표를 채울 수 없으므로 조회를 거부한다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of(OP))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
  }

  @Test
  @DisplayName("응답에 담을 구간의 좌표가 하나도 없으면 빈 좌표로 반환하지 않고 준비되지 않음으로 거부한다")
  void segment_without_any_point_is_not_ready() {
    // given: 첫 페이지에 담길 끝 구간(2..3)의 좌표가 모두 없다.
    seedPath();
    jdbcTemplate.update(
        "DELETE FROM search_path_gps_point WHERE search_path_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff' AND point_order IN (2, 3)");
    // when/then: 구간 좌표를 채울 수 없으므로 조회를 거부한다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of(OP))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
  }

  @Test
  @DisplayName("앞서 방문한 경로의 좌표 누락은 뒤 경로의 구간 크기 오류보다 먼저 알린다")
  void earlier_missing_point_precedes_later_oversized_segment() {
    // given: 먼저 방문하는 경로의 구간 좌표가 빠졌고, 다음 경로의 끝 구간은 좌표 한도 120을 넘는다.
    seedPath();
    seedSecondPath();
    jdbcTemplate.update(
        "DELETE FROM search_path_gps_point WHERE search_path_id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee' AND point_order = 1");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET end_point_order = 122 WHERE search_path_id = 'ffffffff-ffff-ffff-ffff-ffffffffffff' AND start_point_order = 2");
    // when/then: 경로 방문 순서대로 판단해 앞 경로의 준비되지 않음을 반환한다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of(OP))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
  }

  @Test
  @DisplayName("떨어진 두 구간만 바뀌면 사이 구간 없이 각 구간의 원본 좌표를 반환한다")
  void separated_changed_segments_keep_their_own_coordinates() {
    // given: 세 구간 중 앞(0..1)과 뒤(4..5) 구간만 기준 버전 3 이후 바뀌었다.
    seedPath();
    jdbcTemplate.update("UPDATE search_path SET version = 5");
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT 'ffffffff-ffff-ffff-ffff-ffffffffffff',n,'p'||n,
          '2026-04-28T00:00:00Z'::timestamptz + n*interval '5 seconds',126.9+n*0.0001,35.1,1,now()
        FROM generate_series(4,5) n
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
          last_changed_path_version,movement_type,movement_type_source,geometry,version,started_at,ended_at,created_at,updated_at)
        VALUES ('00000000-0000-0000-0000-000000000003','ffffffff-ffff-ffff-ffff-ffffffffffff',4,5,5,'FOOT','AUTO',
          ST_SetSRID(ST_MakeLine(ST_MakePoint(126.9004,35.1),ST_MakePoint(126.9005,35.1)),4326),1,now(),now(),now(),now())
        """);
    jdbcTemplate.update(
        "UPDATE search_path_segment SET version = 2, last_changed_path_version = 4 WHERE start_point_order = 0");
    jdbcTemplate.update(
        "UPDATE search_path_segment SET last_changed_path_version = 3 WHERE start_point_order = 2");
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(3L)
                        .build()))
            .build();
    // when: 변경분을 조회한다. 경로당 한 차례 구간 한도 1이라 두 회차로 나눠 받는다.
    SearchPathPageServiceResponse first = service.getSearchPathChanges(request);
    SearchPathPageServiceResponse second = service.getSearchPathChanges(continuation(first, true));
    // then: 바뀐 두 구간만 각자의 좌표로 받고, 사이 구간(2..3)은 받지 않는다.
    List<SearchPathPageServiceResponse.Segment> received =
        java.util.stream.Stream.of(first, second)
            .flatMap(page -> page.getPaths().stream())
            .flatMap(path -> path.getSegments().stream())
            .toList();
    assertThat(received)
        .extracting(SearchPathPageServiceResponse.Segment::getStartPointOrder)
        .containsExactly(4, 0);
    assertThat(received.get(0).getCoordinates())
        .containsExactly(List.of(126.9004, 35.1), List.of(126.9005, 35.1));
    assertThat(received.get(1).getCoordinates())
        .containsExactly(List.of(126.9, 35.1), List.of(126.9001, 35.1));
    assertThat(second.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("기준 버전 이후 변경이 없는 경로는 변경분 응답의 경로 한도를 차지하지 않는다")
  void unchanged_path_since_baseline_is_not_visited_by_changes() {
    // given: 기준 버전 3을 받은 뒤 경로가 바뀌지 않았고, 변경 진행은 아직 없다.
    seedPath();
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(3L)
                        .build()))
            .build();
    // when: 변경분을 조회한다.
    SearchPathPageServiceResponse response = service.getSearchPathChanges(request);
    // then: 반영할 변경이 없으므로 경로를 반환하지 않고 조회를 끝낸다.
    assertThat(response.getPaths()).isEmpty();
    assertThat(response.isHasMore()).isFalse();
    assertThat(response.getNextSearchPathId()).isNull();
  }

  @Test
  @DisplayName("구간 변경 없이 경로 상태만 바뀌어도 변경 회차를 완료한다")
  void status_only_change_completes_empty_segment_page() {
    // given: 기준 버전 3 이후 일시정지만 반영됐다.
    seedPath();
    jdbcTemplate.update("UPDATE search_path SET version = 4, status = 'PAUSED'");
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(3L)
                        .build()))
            .build();
    // when: 변경분을 조회한다.
    SearchPathPageServiceResponse response = service.getSearchPathChanges(request);
    // then: 구간이 비어도 상태·완료 버전은 갱신한다.
    assertThat(response.getPaths().get(0).getSegments()).isEmpty();
    assertThat(response.getPaths().get(0).getStatus().name()).isEqualTo("PAUSED");
    assertThat(response.getPaths().get(0).getAppliedVersion()).isEqualTo(4L);
    assertThat(response.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("서버보다 높은 반영 버전을 보내면 요청 전체를 거부한다")
  void future_version_rejects_entire_query() {
    // given: 서버 버전 3보다 높은 기준 버전 4를 보냈다.
    seedPath();
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .baselineVersion(4L)
                        .build()))
            .build();
    // when & then: 값을 보정하거나 부분 성공하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathChanges(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEARCH_PATH_QUERY));
  }

  @Test
  @DisplayName("조회 범위 밖 경로가 포함되면 존재 여부를 구분하지 않고 거부한다")
  void out_of_scope_path_rejects_entire_query() {
    // given: 차수 선택은 비었지만 경로 진행 정보를 보냈다.
    seedPath();
    SearchPathPageServiceRequest request =
        request(List.of()).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .build()))
            .build();
    // when & then: 범위 밖 경로만 제외한 성공 응답을 만들지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEARCH_PATH_QUERY));
  }

  @Test
  @DisplayName("순번 보완이 끝나지 않은 구간이 있으면 새 조회 전체를 거부한다")
  void unprepared_segment_rejects_new_query() {
    // given: 일부 구간의 순번이 아직 보완되지 않았다.
    seedPath();
    jdbcTemplate.update(
        "UPDATE search_path_segment SET start_point_order = NULL, end_point_order = NULL WHERE start_point_order = 0");
    // when & then: 준비된 뒤 구간만 반환하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of(OP))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SEARCH_PATH_QUERY_NOT_READY));
  }

  @Test
  @DisplayName("종료 사건은 선택한 차수가 없어도 좌표 조회를 거부한다")
  void closed_incident_rejects_even_empty_scope() {
    // given: 접근 가능했던 사건이 종료됐다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = now() WHERE id = ?", INCIDENT);
    // when & then: 빈 결과로 종료 제한을 우회하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INCIDENT_CLOSED));
  }

  @Test
  @DisplayName("첫 페이지는 경로 끝 구간과 같은 조회 시점의 기준 버전을 반환한다")
  void first_page_returns_tail_segment_and_baseline() {
    // given: 한 경로에 두 구간과 네 좌표가 쌓여 있다.
    seedPath();
    // when: 경로당 한 구간의 시험 한도로 최초 조회한다.
    SearchPathPageServiceResponse response = service.getSearchPathSegments(request(List.of(OP)));
    // then: 뒤쪽 구간을 먼저 받고 이전 구간은 다음 페이지로 남긴다.
    assertThat(response.getPaths()).hasSize(1);
    SearchPathPageServiceResponse.Path path = response.getPaths().get(0);
    assertThat(path.getBaselineVersion()).isEqualTo(3L);
    assertThat(path.getSegments()).hasSize(1);
    assertThat(path.getSegments().get(0).getStartPointOrder()).isEqualTo(2);
    assertThat(path.getSegments().get(0).getCoordinates()).hasSize(2);
    assertThat(path.getBeforeStartPointOrder()).isEqualTo(2);
    assertThat(path.getCompleted()).isFalse();
    assertThat(response.isHasMore()).isTrue();
  }

  @Test
  @DisplayName("선택한 차수가 없으면 전체 경로 대신 정상 빈 결과를 반환한다")
  void no_selected_period_returns_empty_page() {
    // given: 접근 가능한 사건에서 차수를 하나도 선택하지 않았다.
    SearchPathPageServiceRequest request = request(List.of());
    // when: 최초 구간을 조회한다.
    SearchPathPageServiceResponse response = service.getSearchPathSegments(request);
    // then: 조회 범위를 자동으로 넓히지 않는다.
    assertThat(response.getPaths()).isEmpty();
    assertThat(response.getNextSearchPathId()).isNull();
    assertThat(response.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("실제 구간의 시작 순번이 아닌 이어받기 위치는 거부한다")
  void cursor_inside_segment_is_rejected() {
    // given: 시작 순번은 0, 2인데 구간 중간인 1을 보냈다.
    seedPath();
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder()
            .paths(
                List.of(
                    PathProgress.builder()
                        .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                        .beforeStartPointOrder(1)
                        .completed(false)
                        .build()))
            .build();
    // when & then: 서버가 발급하지 않은 위치를 묵인하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEARCH_PATH_QUERY));
  }

  @Test
  @DisplayName("경로 ID가 중복되면 하나를 임의로 선택하지 않고 전체 요청을 거부한다")
  void duplicate_path_progress_is_rejected() {
    // given: 같은 경로의 진행을 두 번 보냈다.
    seedPath();
    PathProgress path =
        PathProgress.builder().id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")).build();
    SearchPathPageServiceRequest request =
        request(List.of(OP)).toBuilder().paths(List.of(path, path)).build();
    // when & then: 잘못된 입력을 합치거나 부분 성공으로 처리하지 않는다.
    assertThatThrownBy(() -> service.getSearchPathSegments(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEARCH_PATH_QUERY));
  }

  @Test
  @DisplayName("파기 완료 기록이 있으면 사건 상태와 관계없이 경로를 반환하지 않는다")
  void completed_purge_blocks_query() {
    // given: 사건 상태와 파기 상태가 어긋나 있어도 파기 제한을 우선한다.
    jdbcTemplate.execute(
        """
        INSERT INTO incident_data_purge(id,incident_id,status,closed_at,purge_due_at,environment_policy,created_at,updated_at)
        VALUES ('00000000-0000-0000-0000-00000000bb01','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
          'COMPLETED',now(),now(),'PRODUCTION_IMMEDIATE',now(),now())
        """);
    try {
      // when & then: 빈 경로 조회도 거부한다.
      assertThatThrownBy(() -> service.getSearchPathSegments(request(List.of())))
          .isInstanceOfSatisfying(
              BusinessException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INCIDENT_CLOSED));
    } finally {
      jdbcTemplate.update(
          "DELETE FROM incident_data_purge WHERE id = '00000000-0000-0000-0000-00000000bb01'");
    }
  }

  @Test
  @DisplayName("원본 좌표가 하나인 구간도 이력과 변경분 조회에서 순번과 원본 좌표를 유지한다")
  void single_point_segment_preserves_source_coordinate_and_order_in_both_queries() {
    // given: 앞 구간 뒤에 순번 2 하나로 이루어진 마지막 구간이 있다.
    seedPath();
    jdbcTemplate.update("DELETE FROM search_path_gps_point WHERE point_order = 3");
    jdbcTemplate.update(
        """
        UPDATE search_path_segment SET end_point_order = 2,
          geometry = ST_GeomFromText('LINESTRING(126.9002 35.1,126.9002 35.1)',4326)
        WHERE start_point_order = 2
        """);

    // when: 최초 이력과 기준 버전 2 이후 변경분을 실제 Mapper·DB로 조회한다.
    SearchPathPageServiceResponse segments = service.getSearchPathSegments(request(List.of(OP)));
    SearchPathPageServiceResponse changes =
        service.getSearchPathChanges(
            request(List.of(OP)).toBuilder()
                .paths(
                    List.of(
                        PathProgress.builder()
                            .id(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                            .baselineVersion(2L)
                            .build()))
                .build());

    // then: 표시용 중복 좌표는 저장·조회 원본과 경로 진행에 섞이지 않는다.
    for (SearchPathPageServiceResponse response : List.of(segments, changes)) {
      SearchPathPageServiceResponse.Segment segment =
          response.getPaths().get(0).getSegments().get(0);
      assertThat(segment.getStartPointOrder()).isEqualTo(2);
      assertThat(segment.getEndPointOrder()).isEqualTo(2);
      assertThat(segment.getCoordinates()).containsExactly(List.of(126.9002, 35.1));
      assertThat(segment.getStartedAt()).isEqualTo(segment.getEndedAt());
      assertThat(response.getPaths().get(0).getBeforeStartPointOrder()).isEqualTo(2);
    }
  }

  private SearchPathPageServiceRequest request(List<UUID> opIds) {
    return SearchPathPageServiceRequest.builder()
        .incidentId(INCIDENT)
        .authentication(
            new SuriMapAuthentication(
                ACCOUNT.toString(),
                AccountType.PATROL_CAR,
                OrganizationType.POLICE_SUBSTATION,
                Channel.WEB,
                null,
                List.of()))
        .opIds(opIds)
        .paths(List.of())
        .build();
  }

  private SearchPathPageServiceRequest continuation(
      SearchPathPageServiceResponse response, boolean changes) {
    return request(List.of(OP)).toBuilder()
        .nextSearchPathId(response.getNextSearchPathId())
        .paths(
            response.getPaths().stream()
                .map(
                    path ->
                        PathProgress.builder()
                            .id(path.getId())
                            .baselineVersion(changes ? path.getBaselineVersion() : null)
                            .appliedVersion(changes ? path.getAppliedVersion() : null)
                            .targetVersion(changes ? path.getTargetVersion() : null)
                            .beforeStartPointOrder(path.getBeforeStartPointOrder())
                            .completed(path.getCompleted())
                            .build())
                .toList())
        .build();
  }

  private void seedPath() {
    jdbcTemplate.execute(
        """
        INSERT INTO search_path(id,duty_shift_id,account_id,status,version,started_at,created_at,updated_at)
        VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff',
          '60000000-0000-0000-0000-000000002621','62000000-0000-0000-0000-000000002621',
          'RECORDING',3,now(),now(),now())
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
          last_changed_path_version,movement_type,movement_type_source,geometry,version,started_at,ended_at,created_at,updated_at)
        SELECT ('00000000-0000-0000-0000-' || lpad((n+1)::text,12,'0'))::uuid,
          'ffffffff-ffff-ffff-ffff-ffffffffffff',n*2,n*2+1,n+2,'FOOT','AUTO',
          ST_SetSRID(ST_MakeLine(ST_MakePoint(126.9+n*0.0002,35.1),
            ST_MakePoint(126.9001+n*0.0002,35.1)),4326),1,now(),now(),now(),now()
        FROM generate_series(0,1) n
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT 'ffffffff-ffff-ffff-ffff-ffffffffffff',n,'p'||n,
          '2026-04-28T00:00:00Z'::timestamptz + n*interval '5 seconds',126.9+n*0.0001,35.1,1,now()
        FROM generate_series(0,3) n
        """);
  }

  private void seedSecondPath() {
    jdbcTemplate.execute(
        """
        INSERT INTO search_path(id,duty_shift_id,account_id,status,version,started_at,created_at,updated_at)
        VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
          '60000000-0000-0000-0000-000000002621','62000000-0000-0000-0000-000000002621',
          'ENDED',2,now(),now(),now())
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_segment(id,search_path_id,start_point_order,end_point_order,
          last_changed_path_version,movement_type,movement_type_source,geometry,version,started_at,ended_at,created_at,updated_at)
        VALUES ('00000000-0000-0000-0000-0000000000e1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',0,1,2,'FOOT','AUTO',
          ST_SetSRID(ST_MakeLine(ST_MakePoint(126.95,35.1),ST_MakePoint(126.9501,35.1)),4326),1,now(),now(),now(),now())
        """);
    jdbcTemplate.execute(
        """
        INSERT INTO search_path_gps_point(search_path_id,point_order,point_id,client_ts,lon,lat,speed_mps,created_at)
        SELECT 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',n,'e'||n,
          '2026-04-28T00:00:00Z'::timestamptz + n*interval '5 seconds',126.95+n*0.0001,35.1,1,now()
        FROM generate_series(0,1) n
        """);
  }

  @TestConfiguration
  static class SnapshotConfig {
    @Bean
    SnapshotPause snapshotPause() {
      return new SnapshotPause();
    }

    @Bean
    StatementCounter statementCounter() {
      return new StatementCounter();
    }

    @Bean
    FetchSizeRecorder fetchSizeRecorder() {
      return new FetchSizeRecorder();
    }
  }

  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "query",
          args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
  static class SnapshotPause implements Interceptor {
    private volatile boolean enabled;
    private CountDownLatch loaded = new CountDownLatch(0);
    private CountDownLatch resume = new CountDownLatch(0);

    public void start() {
      loaded = new CountDownLatch(1);
      resume = new CountDownLatch(1);
      enabled = true;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      Object result = invocation.proceed();
      MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
      if (enabled && statement.getId().endsWith(".findBoardPathMetadata")) {
        enabled = false;
        loaded.countDown();
        if (!resume.await(15, TimeUnit.SECONDS)) {
          throw new IllegalStateException("snapshot test timed out");
        }
      }
      return result;
    }
  }

  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "query",
          args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
  static class StatementCounter implements Interceptor {
    private final java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean enabled;

    public void start() {
      count.set(0);
      enabled = true;
    }

    public int stop() {
      enabled = false;
      return count.get();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
      if (enabled && statement.getId().contains(".SearchPathMapper.")) {
        count.incrementAndGet();
      }
      return invocation.proceed();
    }
  }

  /** 실제 JDBC 문장이 실행될 때 적용된 fetch size를 Mapper 문장별로 기록한다. */
  @Intercepts(
      @Signature(
          type = StatementHandler.class,
          method = "query",
          args = {Statement.class, ResultHandler.class}))
  static class FetchSizeRecorder implements Interceptor {
    private final Map<String, List<Integer>> fetchSizes = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    public void start() {
      fetchSizes.clear();
      enabled = true;
    }

    public Map<String, List<Integer>> stop() {
      enabled = false;
      return Map.copyOf(fetchSizes);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      if (enabled) {
        MappedStatement statement =
            (MappedStatement)
                SystemMetaObject.forObject(invocation.getTarget())
                    .getValue("delegate.mappedStatement");
        String id = statement.getId().substring(statement.getId().lastIndexOf('.') + 1);
        int fetchSize = ((Statement) invocation.getArgs()[0]).getFetchSize();
        fetchSizes.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(fetchSize);
      }
      return invocation.proceed();
    }
  }
}
