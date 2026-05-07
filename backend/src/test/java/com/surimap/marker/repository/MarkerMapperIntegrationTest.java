package com.surimap.marker.repository;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** L5-T05B marker table and MyBatis persistence integration test. */
@DisplayName("L5-T05B marker mapper integration")
@Tag("integration")
class MarkerMapperIntegrationTest extends PostGisIntegrationTestSupport {

  @Autowired private MarkerMapper markerMapper;

  @BeforeEach
  void cleanMarkerTable() {
    jdbcTemplate.execute("TRUNCATE TABLE marker");
  }

  @Test
  @DisplayName("marker migration은 Point, version, status, index 계약을 준비한다")
  void marker_migration은_point_version_status_index_계약을_준비한다() {
    Integer srid =
        jdbcTemplate.queryForObject(
            "SELECT srid FROM geometry_columns "
                + "WHERE f_table_name = 'marker' AND f_geometry_column = 'location'",
            Integer.class);
    String geometryType =
        jdbcTemplate.queryForObject(
            "SELECT upper(type) FROM geometry_columns "
                + "WHERE f_table_name = 'marker' AND f_geometry_column = 'location'",
            String.class);
    List<String> indexes =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'marker'", String.class);

    assertThat(srid).isEqualTo(4326);
    assertThat(geometryType).isEqualTo("POINT");
    assertThat(indexes)
        .contains(
            "idx_marker_op_status",
            "idx_marker_type",
            "idx_marker_police_phone",
            "idx_marker_location");
  }

  @Test
  @DisplayName("seed marker를 저장하고 PostGIS Point로 다시 읽는다")
  void seed_marker를_저장하고_postgis_point로_다시_읽는다() {
    markerMapper.insertSeed(
        MarkerSeedRecord.from(
            MarkerSeedFixtures.INCIDENT_ID, MarkerSeedFixtures.referenceClueSeed()));

    List<MarkerRecord> records = markerMapper.findByIds(List.of(MARKER_ID));

    assertThat(records).hasSize(1);
    MarkerRecord record = records.get(0);
    assertThat(record.getId()).isEqualTo(MARKER_ID);
    assertThat(record.getMarkerSource()).isEqualTo(MarkerSource.MOCK_SEED.name());
    assertThat(record.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(record.getVersion()).isEqualTo(1L);
    assertThat(record.getLocation()).isNotNull();
    assertThat(record.getLocation().getSRID()).isEqualTo(4326);
    assertThat(record.getLocation().getGeometryType()).isEqualTo("Point");
  }
}
