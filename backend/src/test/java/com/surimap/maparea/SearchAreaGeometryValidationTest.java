package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.maparea.event.PublishRequestCollector;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.policy.GeometryPolicy;
import com.surimap.maparea.geometry.validation.GeometrySpatialPort;
import com.surimap.maparea.geometry.validation.GeometryValidationService;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S2 수색 구역 생성·수정 시 geometry 검증이 PublishRequest 발행 여부를 결정함을 검증한다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json tdd_red_tests.backend[1] — SearchAreaGeometryValidationTest.
 *
 * <p>이 테스트는 SearchAreaService가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class SearchAreaGeometryValidationTest {

  private static final GeometryPolicy POLICY = GeometryPolicy.s2HarnessDefault();
  private static final GeometryValidator VALIDATOR = new GeometryValidator(POLICY);

  /** PostGIS 공간 검증 포트 stub — isAreaAtLeastM2는 항상 true, covers는 시나리오별로 설정한다. */
  private GeometrySpatialPort spatialPort;

  private GeometryValidationService geometryValidationService;

  /** 이벤트 발행 수집기 (test double). */
  private PublishRequestCollector eventCollector;

  /**
   * 테스트 대상 서비스.
   *
   * <p>SearchAreaService는 아직 존재하지 않는다. 이 선언이 RED 컴파일 실패의 원인이다.
   */
  private SearchAreaService searchAreaService;

  @BeforeEach
  void setUp() {
    spatialPort = mock(GeometrySpatialPort.class);

    // 기본 stub: 면적 검증은 항상 통과
    when(spatialPort.isAreaAtLeastM2(any(), eq(GeometryFixtures.MINIMUM_POLYGON_AREA_M2_VALUE)))
        .thenReturn(true);

    geometryValidationService = new GeometryValidationService(VALIDATOR, POLICY, spatialPort);
    eventCollector = new PublishRequestCollector();

    // RED: SearchAreaService는 구현되지 않아 컴파일 실패한다
    searchAreaService = new SearchAreaService(eventCollector, geometryValidationService);
  }

  @Test
  void create_유효한_polygon은_ACTIVE_search_area를_생성한다() {
    // overall_search_area covers search_area — stub 설정
    when(spatialPort.covers(any(), any())).thenReturn(true);

    GeoJsonPolygon searchAreaPolygon = GeometryFixtures.validSearchAreaPolygon();
    String overallWkt = overallSearchAreaWkt();

    SearchAreaService.CreateCommand command =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            searchAreaPolygon,
            overallWkt);

    SearchAreaService.SearchAreaResponse response = searchAreaService.create(command);

    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(eventCollector.publishedTypes()).contains("SEARCH_AREA_CHANGED");
  }

  @Test
  void create_자기교차_polygon은_invalid_geometry로_실패한다() {
    GeoJsonPolygon selfIntersecting = selfIntersectingPolygon();
    String overallWkt = overallSearchAreaWkt();

    SearchAreaService.CreateCommand command =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            selfIntersecting,
            overallWkt);

    assertThatThrownBy(() -> searchAreaService.create(command))
        .isInstanceOf(InvalidGeometryException.class);

    assertThat(eventCollector.publishedTypes()).isEmpty();
  }

  @Test
  void create_미폐합_ring은_invalid_geometry로_실패한다() {
    GeoJsonPolygon unclosed = unclosedRingPolygon();
    String overallWkt = overallSearchAreaWkt();

    SearchAreaService.CreateCommand command =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID, BoundaryAreaFixtures.OP1_ID, unclosed, overallWkt);

    assertThatThrownBy(() -> searchAreaService.create(command))
        .isInstanceOf(InvalidGeometryException.class);

    assertThat(eventCollector.publishedTypes()).isEmpty();
  }

  @Test
  void create_overall_search_area_밖_polygon은_invalid_geometry로_실패한다() {
    // overall_search_area가 search_area를 포함하지 않음 — covers false
    when(spatialPort.covers(any(), any())).thenReturn(false);

    // overall_search_area: bbox 안의 작은 영역
    GeoJsonPolygon smallOverallPolygon = GeometryFixtures.validOverallSearchAreaPolygon();
    String smallOverallWkt = toWkt(smallOverallPolygon);

    // search_area: bbox 밖 좌표를 포함하는 polygon
    GeoJsonPolygon outsidePolygon = polygonOutsideOverall();

    SearchAreaService.CreateCommand command =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            outsidePolygon,
            smallOverallWkt);

    assertThatThrownBy(() -> searchAreaService.create(command))
        .isInstanceOf(com.surimap.maparea.geometry.exception.InvalidGeometryException.class);

    assertThat(eventCollector.publishedTypes()).isEmpty();
  }

  @Test
  void update_성공_시_version이_증가한다() {
    when(spatialPort.covers(any(), any())).thenReturn(true);

    GeoJsonPolygon polygon = GeometryFixtures.validSearchAreaPolygon();
    String overallWkt = overallSearchAreaWkt();

    SearchAreaService.CreateCommand createCommand =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID, BoundaryAreaFixtures.OP1_ID, polygon, overallWkt);

    SearchAreaService.SearchAreaResponse created = searchAreaService.create(createCommand);

    eventCollector.clear();

    SearchAreaService.UpdateCommand updateCommand =
        new SearchAreaService.UpdateCommand(
            created.id(), GeometryFixtures.validSearchAreaPolygon(), overallWkt, created.version());

    SearchAreaService.SearchAreaResponse updated = searchAreaService.update(updateCommand);

    assertThat(updated.version()).isGreaterThan(created.version());
    assertThat(eventCollector.publishedTypes()).contains("SEARCH_AREA_CHANGED");
  }

  @Test
  void update_후_status는_ACTIVE를_유지한다() {
    when(spatialPort.covers(any(), any())).thenReturn(true);

    GeoJsonPolygon polygon = GeometryFixtures.validSearchAreaPolygon();
    String overallWkt = overallSearchAreaWkt();

    SearchAreaService.CreateCommand createCommand =
        new SearchAreaService.CreateCommand(
            BoundaryAreaFixtures.INCIDENT_ID, BoundaryAreaFixtures.OP1_ID, polygon, overallWkt);

    SearchAreaService.SearchAreaResponse created = searchAreaService.create(createCommand);

    SearchAreaService.UpdateCommand updateCommand =
        new SearchAreaService.UpdateCommand(
            created.id(), GeometryFixtures.validSearchAreaPolygon(), overallWkt, created.version());

    SearchAreaService.SearchAreaResponse updated = searchAreaService.update(updateCommand);

    assertThat(updated.status()).isEqualTo("ACTIVE");
  }

  // -----------------------------------------------------------------------
  // 헬퍼: invalid polygon fixture
  // -----------------------------------------------------------------------

  /**
   * 자기 교차 polygon (나비 형태).
   *
   * <p>GeometryFixtures.INVALID_CASES_WITHOUT_FIXED_COORDINATES에 명시된 polygon-self-intersecting 케이스.
   * 좌표는 bbox(126.9~127.08, 37.5~37.62) 안에 위치한다.
   */
  private static GeoJsonPolygon selfIntersectingPolygon() {
    // 나비 모양: 두 삼각형이 꼭짓점에서 교차
    // [126.950,37.570] → [126.960,37.580] → [126.960,37.570] → [126.950,37.580] → [126.950,37.570]
    List<List<BigDecimal>> ring =
        List.of(
            point("126.950000", "37.570000"),
            point("126.960000", "37.580000"),
            point("126.960000", "37.570000"),
            point("126.950000", "37.580000"),
            point("126.950000", "37.570000"));
    return new GeoJsonPolygon("Polygon", List.of(ring));
  }

  /**
   * 미폐합 ring polygon.
   *
   * <p>GeometryFixtures.INVALID_CASES_WITHOUT_FIXED_COORDINATES에 명시된 polygon-unclosed 케이스. 첫 좌표와
   * 마지막 좌표가 다르다.
   */
  private static GeoJsonPolygon unclosedRingPolygon() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.952000", "37.568000"),
            point("126.961000", "37.568000"),
            point("126.961000", "37.575000"),
            point("126.952000", "37.575000")
            // 의도적으로 첫 좌표를 반복하지 않음 → unclosed ring
            );
    return new GeoJsonPolygon("Polygon", List.of(ring));
  }

  /**
   * overall_search_area 밖에 위치하는 polygon.
   *
   * <p>GeometryFixtures.invalidCoordOutsideEnvelope() 좌표를 outer ring에 포함한다. bbox(126.9~127.08,
   * 37.5~37.62) 밖 좌표(lon=127.200000)가 포함된다.
   */
  private static GeoJsonPolygon polygonOutsideOverall() {
    List<List<BigDecimal>> ring =
        List.of(
            point("127.195000", "37.570000"),
            point("127.205000", "37.570000"),
            point("127.205000", "37.575000"),
            point("127.195000", "37.575000"),
            point("127.195000", "37.570000"));
    return new GeoJsonPolygon("Polygon", List.of(ring));
  }

  /** overall_search_area GeoJSON polygon을 WKT 문자열로 변환한다 (테스트 편의용). */
  private static String overallSearchAreaWkt() {
    // GeometryFixtures.validOverallSearchAreaPolygon()의 좌표를 WKT로 표현
    return "POLYGON ((126.948000 37.565000, 126.968000 37.565000, 126.968000 37.579000, "
        + "126.948000 37.579000, 126.948000 37.565000))";
  }

  /** GeoJsonPolygon을 단순 WKT 문자열로 변환한다 (테스트 편의용). */
  private static String toWkt(GeoJsonPolygon polygon) {
    // overall_search_area는 항상 validOverallSearchAreaPolygon fixture를 사용하므로 고정값 반환
    return overallSearchAreaWkt();
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
