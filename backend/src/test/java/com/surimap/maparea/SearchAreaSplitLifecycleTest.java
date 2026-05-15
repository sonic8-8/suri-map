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
 * S2 split_lifecycle 계약 RED 테스트.
 *
 * <p>기준 문서: docs/spec/specs/S2.json split_lifecycle 섹션.
 */
class SearchAreaSplitLifecycleTest {

  private static final GeometryPolicy POLICY = GeometryPolicy.s2HarnessDefault();
  private static final GeometryValidator VALIDATOR = new GeometryValidator(POLICY);

  private GeometrySpatialPort spatialPort;
  private GeometryValidationService geometryValidationService;
  private PublishRequestCollector eventCollector;
  private SearchAreaSplitService splitService;

  @BeforeEach
  void setUp() {
    spatialPort = mock(GeometrySpatialPort.class);
    when(spatialPort.isAreaAtLeastM2(any(), eq(GeometryFixtures.MINIMUM_POLYGON_AREA_M2_VALUE)))
        .thenReturn(true);
    when(spatialPort.covers(any(), any())).thenReturn(true);

    geometryValidationService = new GeometryValidationService(VALIDATOR, POLICY, spatialPort);
    eventCollector = new PublishRequestCollector();
    splitService = new SearchAreaSplitService(eventCollector, geometryValidationService);

    splitService.registerArea(
        BoundaryAreaFixtures.AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        GeometryFixtures.validSearchAreaPolygon(),
        "ACTIVE",
        1);
  }

  @Test
  void split_성공_시_parent는_CANCELLED_상태가_된다() {
    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.parent().status()).isEqualTo("CANCELLED");
  }

  @Test
  void split_성공_시_parent_version이_증가한다() {
    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.parent().version()).isEqualTo(2);
  }

  @Test
  void split_성공_시_parent_geometry는_원본을_보존한다() {
    GeoJsonPolygon original = GeometryFixtures.validSearchAreaPolygon();

    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.parent().geometry()).isEqualTo(original);
  }

  @Test
  void split_성공_시_child는_ACTIVE_상태로_생성된다() {
    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.children())
        .allSatisfy(child -> assertThat(child.status()).isEqualTo("ACTIVE"));
  }

  @Test
  void split_성공_시_child_version은_1이다() {
    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.children()).allSatisfy(child -> assertThat(child.version()).isEqualTo(1));
  }

  @Test
  void split_성공_시_child_parentAreaId는_parent_id다() {
    SearchAreaSplitService.SplitResponse response = splitService.split(validSplitCommand(1));

    assertThat(response.children())
        .allSatisfy(
            child -> assertThat(child.parentAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID));
  }

  @Test
  void split_성공_시_SEARCH_AREA_CHANGED가_parent_수만큼_발행된다() {
    splitService.split(validSplitCommand(1));

    List<PublishRequestCollector.PublishRequest> parentEvents =
        eventCollector.publishedByType("SEARCH_AREA_CHANGED").stream()
            .filter(
                r -> {
                  if (r.payload() instanceof SearchAreaSplitService.SplitParentResult p) {
                    return "CANCELLED".equals(p.status());
                  }
                  return false;
                })
            .toList();

    assertThat(parentEvents).hasSize(1);
  }

  @Test
  void split_성공_시_parent_이벤트_payload에_incidentId와_opId가_포함된다() {
    splitService.split(validSplitCommand(1));

    eventCollector.publishedByType("SEARCH_AREA_CHANGED").stream()
        .filter(r -> r.payload() instanceof SearchAreaSplitService.SplitParentResult)
        .map(r -> (SearchAreaSplitService.SplitParentResult) r.payload())
        .forEach(
            p -> {
              assertThat(p.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
              assertThat(p.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
            });
  }

  @Test
  void split_성공_시_child_이벤트_payload에_incidentId와_opId가_포함된다() {
    splitService.split(validSplitCommand(1));

    eventCollector.publishedByType("SEARCH_AREA_CHANGED").stream()
        .filter(r -> r.payload() instanceof SearchAreaSplitService.SplitChildResult)
        .map(r -> (SearchAreaSplitService.SplitChildResult) r.payload())
        .forEach(
            c -> {
              assertThat(c.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
              assertThat(c.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
            });
  }

  @Test
  void split_성공_시_SEARCH_AREA_CHANGED가_child_수만큼_발행된다() {
    List<GeoJsonPolygon> children =
        List.of(
            GeometryFixtures.validSearchAreaPolygon(), GeometryFixtures.validSearchAreaPolygon());
    SearchAreaSplitService.SplitCommand command =
        new SearchAreaSplitService.SplitCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            children,
            1);

    splitService.split(command);

    List<PublishRequestCollector.PublishRequest> childEvents =
        eventCollector.publishedByType("SEARCH_AREA_CHANGED").stream()
            .filter(
                r -> {
                  if (r.payload() instanceof SearchAreaSplitService.SplitChildResult c) {
                    return "ACTIVE".equals(c.status());
                  }
                  return false;
                })
            .toList();

    assertThat(childEvents).hasSize(children.size());
  }

  @Test
  void split_expectedVersion_불일치_시_실패한다() {
    assertThatThrownBy(() -> splitService.split(validSplitCommand(99)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void split_자기교차_child_geometry는_InvalidGeometryException으로_실패한다() {
    GeoJsonPolygon selfIntersecting = selfIntersectingPolygon();
    List<GeoJsonPolygon> children =
        List.of(GeometryFixtures.validSearchAreaPolygon(), selfIntersecting);
    SearchAreaSplitService.SplitCommand command =
        new SearchAreaSplitService.SplitCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            children,
            1);

    assertThatThrownBy(() -> splitService.split(command))
        .isInstanceOf(InvalidGeometryException.class);
  }

  // --- helper ---

  private SearchAreaSplitService.SplitCommand validSplitCommand(int expectedVersion) {
    List<GeoJsonPolygon> children =
        List.of(
            GeometryFixtures.validSearchAreaPolygon(), GeometryFixtures.validSearchAreaPolygon());
    return new SearchAreaSplitService.SplitCommand(
        BoundaryAreaFixtures.AREA_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        children,
        expectedVersion);
  }

  private GeoJsonPolygon selfIntersectingPolygon() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.910000", "35.162000"),
            point("126.920000", "35.173000"),
            point("126.920000", "35.162000"),
            point("126.910000", "35.173000"),
            point("126.910000", "35.162000"));
    return new GeoJsonPolygon("Polygon", List.of(ring));
  }

  private List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
