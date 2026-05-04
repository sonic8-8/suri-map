package com.surimap.maparea.geometry.validation;

import com.surimap.maparea.fixture.SpatialSqlFixtures;
import com.surimap.maparea.geometry.validation.mapper.GeometrySpatialMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.surimap.maparea.fixture.GeometryFixtures.MINIMUM_POLYGON_AREA_M2_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * L3-B01 PostGIS 핵심 공간 함수 Mapper 검증.
 */
@DisplayName("L3-B01 GeometrySpatialMapper integration")
@Tag("integration")
class GeometrySpatialMapperIntegrationTest extends PostGisIntegrationTestSupport {

    @Autowired
    private GeometrySpatialMapper mapper;

    @Test
    @DisplayName("Polygon 실제 면적이 400m2 이상인지 PostGIS geography 면적으로 검증한다")
    void polygon_면적_검증() {
        assertThat(mapper.isAreaAtLeastM2(
                SpatialSqlFixtures.SEARCH_AREA_WKT,
                MINIMUM_POLYGON_AREA_M2_VALUE
        )).isTrue();

        assertThat(mapper.isAreaAtLeastM2(
                SpatialSqlFixtures.TINY_AREA_WKT,
                MINIMUM_POLYGON_AREA_M2_VALUE
        )).isFalse();
    }

    @Test
    @DisplayName("parent Polygon이 child Polygon을 cover하는지 검증한다")
    void polygon_cover_검증() {
        assertThat(mapper.covers(
                SpatialSqlFixtures.BOUNDARY_WKT,
                SpatialSqlFixtures.SEARCH_AREA_WKT
        )).isTrue();

        assertThat(mapper.covers(
                SpatialSqlFixtures.BOUNDARY_WKT,
                SpatialSqlFixtures.OUTSIDE_BOUNDARY_WKT
        )).isFalse();
    }

    @Test
    @DisplayName("면적으로 겹치는 Polygon만 overlap으로 본다")
    void polygon_overlap_by_area_검증() {
        assertThat(mapper.overlapsByArea(
                SpatialSqlFixtures.BOUNDARY_WKT,
                SpatialSqlFixtures.SEARCH_AREA_WKT
        )).isTrue();

        assertThat(mapper.overlapsByArea(
                SpatialSqlFixtures.SEARCH_AREA_WKT,
                SpatialSqlFixtures.TOUCHING_AREA_WKT
        )).isFalse();
    }
}
