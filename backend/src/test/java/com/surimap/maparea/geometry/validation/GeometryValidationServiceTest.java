package com.surimap.maparea.geometry.validation;

import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.exception.MapBoundaryRequiredException;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.policy.GeometryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.surimap.maparea.fixture.GeometryFixtures.MINIMUM_POLYGON_AREA_M2_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GeometryValidationService의 S2 도메인 Polygon 검증 흐름을 검증한다.
 */
class GeometryValidationServiceTest {

    /** S2 하네스 도형 정책이다. */
    private final GeometryPolicy policy = GeometryPolicy.s2HarnessDefault();

    /** 좌표와 ring 수준의 1차 검증기다. */
    private final GeometryValidator validator = new GeometryValidator(policy);

    /** PostGIS 기반 공간 검증 포트 mock이다. */
    private GeometrySpatialPort spatialPort;

    /** S2 도메인 도형 검증 서비스다. */
    private GeometryValidationService service;

    /**
     * 각 테스트 실행 전 mock과 service를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        spatialPort = mock(GeometrySpatialPort.class);
        service = new GeometryValidationService(validator, policy, spatialPort);
    }

    @Test
    void map_boundary_polygon을_검증하고_결과를_반환한다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);

        GeometryValidationResult result = service.validateMapBoundaryPolygon(validPolygon());

        assertThat(result.canonicalOuterRing()).hasSize(4);
        assertThat(result.polygon()).isNotNull();
        assertThat(result.polygon().getSRID()).isEqualTo(4326);
        assertThat(result.wkt()).startsWith("POLYGON");
        verify(spatialPort).isAreaAtLeastM2(result.wkt(), MINIMUM_POLYGON_AREA_M2_VALUE);
    }

    @Test
    void non_polygon_geometry는_invalid_geometry다() {
        GeoJsonPolygon polygon = new GeoJsonPolygon("LineString", validPolygon().coordinates());

        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(polygon))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("GeoJSON geometry type은 Polygon이어야 합니다.");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void null_polygon은_invalid_geometry다() {
        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(null))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("GeoJSON Polygon은 null일 수 없습니다.");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void outer_ring이_없는_polygon은_invalid_geometry다() {
        GeoJsonPolygon polygon = new GeoJsonPolygon("Polygon", List.of());

        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(polygon))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("GeoJSON Polygon outer ring은 비어 있을 수 없습니다.");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void self_intersection_polygon은_invalid_geometry다() {
        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(selfIntersectionPolygon()))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("Polygon은 자기 교차 없이 유효해야 합니다.");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void zero_area_polygon은_invalid_geometry다() {
        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(zeroAreaPolygon()))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("Polygon");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void minimum_area_400m2_미만이면_invalid_geometry다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(false);

        assertThatThrownBy(() -> service.validateMapBoundaryPolygon(validPolygon()))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("Polygon 면적은 최소 400㎡ 이상이어야 합니다.");

        verify(spatialPort).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
    }

    @Test
    void search_area가_active_boundary_내부면_검증이_통과한다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);
        when(spatialPort.covers(eq(activeBoundaryWkt()), any())).thenReturn(true);

        GeometryValidationResult result = service.validateSearchAreaPolygon(validPolygon(), activeBoundaryWkt());

        assertThat(result).isNotNull();
        verify(spatialPort).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
        verify(spatialPort).covers(eq(activeBoundaryWkt()), any());
    }

    @Test
    void search_area가_active_boundary_밖이면_invalid_geometry다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);
        when(spatialPort.covers(eq(activeBoundaryWkt()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.validateSearchAreaPolygon(validPolygon(), activeBoundaryWkt()))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("search_area Polygon은 active map_boundary 내부에 있어야 합니다.");

        verify(spatialPort).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
        verify(spatialPort).covers(eq(activeBoundaryWkt()), any());
    }

    @Test
    void active_boundary_wkt가_없으면_map_boundary_required다() {
        assertThatThrownBy(() -> service.validateSearchAreaPolygon(validPolygon(), null))
                .isInstanceOf(MapBoundaryRequiredException.class)
                .hasMessageContaining("active map_boundary geometry가 필요합니다.")
                .satisfies(error -> assertThat(((MapBoundaryRequiredException) error).errorCode())
                        .isEqualTo("map_boundary_required"));

        verifyNoInteractions(spatialPort);
    }

    @Test
    void split_child_polygon이_2개_미만이면_invalid_geometry다() {
        assertThatThrownBy(() -> service.validateSplitChildren(activeBoundaryWkt(), List.of(validPolygon())))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("split child Polygon은 최소 2개 이상이어야 합니다.");

        verifyNoInteractions(spatialPort);
    }

    @Test
    void split_child_polygon들이_parent_내부이고_서로_겹치지_않으면_통과한다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);
        when(spatialPort.covers(eq(activeBoundaryWkt()), any())).thenReturn(true);
        when(spatialPort.overlapsByArea(any(), any())).thenReturn(false);

        List<GeometryValidationResult> results = service.validateSplitChildren(
                activeBoundaryWkt(),
                List.of(validPolygon(), anotherValidPolygon())
        );

        assertThat(results).hasSize(2);
        verify(spatialPort, times(2)).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
        verify(spatialPort, times(2)).covers(eq(activeBoundaryWkt()), any());
        verify(spatialPort).overlapsByArea(any(), any());
    }

    @Test
    void split_child_polygon이_parent_밖이면_invalid_geometry다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);
        when(spatialPort.covers(eq(activeBoundaryWkt()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.validateSplitChildren(
                activeBoundaryWkt(),
                List.of(validPolygon(), anotherValidPolygon())
        ))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("split child Polygon은 parent Polygon 내부에 있어야 합니다.");

        verify(spatialPort, times(2)).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
        verify(spatialPort).covers(eq(activeBoundaryWkt()), any());
    }

    @Test
    void split_child_polygon끼리_겹치면_invalid_geometry다() {
        when(spatialPort.isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE))).thenReturn(true);
        when(spatialPort.covers(eq(activeBoundaryWkt()), any())).thenReturn(true);
        when(spatialPort.overlapsByArea(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.validateSplitChildren(
                activeBoundaryWkt(),
                List.of(validPolygon(), anotherValidPolygon())
        ))
                .isInstanceOf(InvalidGeometryException.class)
                .hasMessageContaining("split child Polygon끼리는 서로 겹칠 수 없습니다.");

        verify(spatialPort, times(2)).isAreaAtLeastM2(any(), eq(MINIMUM_POLYGON_AREA_M2_VALUE));
        verify(spatialPort, times(2)).covers(eq(activeBoundaryWkt()), any());
        verify(spatialPort).overlapsByArea(any(), any());
    }

    private GeoJsonPolygon validPolygon() {
        return new GeoJsonPolygon(
                "Polygon",
                List.of(List.of(
                        point("126.950000", "37.550000"),
                        point("126.960000", "37.550000"),
                        point("126.960000", "37.560000"),
                        point("126.950000", "37.550000")
                ))
        );
    }

    private GeoJsonPolygon anotherValidPolygon() {
        return new GeoJsonPolygon(
                "Polygon",
                List.of(List.of(
                        point("126.970000", "37.550000"),
                        point("126.980000", "37.550000"),
                        point("126.980000", "37.560000"),
                        point("126.970000", "37.550000")
                ))
        );
    }

    private GeoJsonPolygon selfIntersectionPolygon() {
        return new GeoJsonPolygon(
                "Polygon",
                List.of(List.of(
                        point("126.950000", "37.550000"),
                        point("126.970000", "37.570000"),
                        point("126.950000", "37.570000"),
                        point("126.970000", "37.550000"),
                        point("126.950000", "37.550000")
                ))
        );
    }

    private GeoJsonPolygon zeroAreaPolygon() {
        return new GeoJsonPolygon(
                "Polygon",
                List.of(List.of(
                        point("126.950000", "37.550000"),
                        point("126.960000", "37.550000"),
                        point("126.970000", "37.550000"),
                        point("126.950000", "37.550000")
                ))
        );
    }

    private String activeBoundaryWkt() {
        return "POLYGON ((126.900000 37.500000, 127.080000 37.500000, 127.080000 37.620000, 126.900000 37.620000, 126.900000 37.500000))";
    }

    private List<BigDecimal> point(String lon, String lat) {
        return List.of(new BigDecimal(lon), new BigDecimal(lat));
    }
}
