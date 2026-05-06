package com.surimap.maparea.geometry.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SpatialSqlFixtures;
import com.surimap.maparea.geometry.validation.mapper.JtsGeometryTypeHandlerTestMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;

/** L3-B01 MyBatis JTS Geometry TypeHandler 검증. */
@DisplayName("L3-B01 JTS Geometry TypeHandler integration")
@Tag("integration")
class JtsGeometryTypeHandlerIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID PROBE_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeee0001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");

  @Autowired private JtsGeometryTypeHandlerTestMapper mapper;

  @Test
  @DisplayName("JTS Polygon을 MyBatis TypeHandler로 저장하고 다시 읽는다")
  void jts_polygon을_type_handler로_저장하고_조회한다() {
    Polygon polygon = SpatialSqlFixtures.searchAreaPolygon();

    mapper.insert(
        PROBE_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        ACCOUNT_ID,
        polygon);
    JtsGeometryTypeHandlerTestMapper.GeometryProbeRow row = mapper.findById(PROBE_ID);

    assertThat(row.getId()).isEqualTo(PROBE_ID);
    assertThat(row.getGeometry()).isNotNull();
    assertThat(row.getGeometry().getSRID()).isEqualTo(4326);
    assertThat(row.getGeometry().getGeometryType()).isEqualTo("Polygon");
    assertThat(row.getGeometry().getCoordinates()).hasSize(polygon.getCoordinates().length);
  }
}
