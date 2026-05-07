package com.surimap.marker.repository;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** L5-T05B marker table and MyBatis persistence integration test. */
@DisplayName("L5-T05B marker mapper integration")
@Tag("integration")
class MarkerMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID CREATE_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID CREATE_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID CREATE_POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");

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

  @Test
  @DisplayName("APP marker create row는 필수 context와 Point를 저장한다")
  void app_marker_create_row는_필수_context와_point를_저장한다() {
    markerMapper.insertCreate(
        new MarkerCreateRecord(
            MarkerGeometryFixtures.INCIDENT_ID,
            CREATE_MARKER_ID,
            MarkerGeometryFixtures.OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            MarkerGeometryFixtures.VALID_MARKER_POINT,
            "S14P31C106-71 field clue",
            CLIENT_TS,
            CREATE_ACCOUNT_ID,
            CREATE_POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));

    List<MarkerRecord> records = markerMapper.findByIds(List.of(CREATE_MARKER_ID));

    assertThat(records).hasSize(1);
    MarkerRecord record = records.get(0);
    assertThat(record.getId()).isEqualTo(CREATE_MARKER_ID);
    assertThat(record.getOperationalPeriodId()).isEqualTo(MarkerGeometryFixtures.OP1_ID);
    assertThat(record.getMarkerType()).isEqualTo(MarkerType.CLUE.name());
    assertThat(record.getMarkerSource()).isEqualTo(MarkerSource.APP.name());
    assertThat(record.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(record.getVersion()).isEqualTo(1L);
    assertThat(record.getCreatedByAccountId()).isEqualTo(CREATE_ACCOUNT_ID);
    assertThat(record.getPolicePhoneId()).isEqualTo(CREATE_POLICE_PHONE_ID);
    assertThat(record.getOccurredAt()).isEqualTo(CLIENT_TS);
    assertThat(record.getLocation().getSRID()).isEqualTo(4326);
    assertThat(record.getLocation().getX()).isEqualTo(126.956500);
    assertThat(record.getLocation().getY()).isEqualTo(37.571200);
  }

  @Test
  @DisplayName("marker update는 expected version이 맞을 때 UPDATED와 version+1을 저장한다")
  void marker_update는_expected_version이_맞을_때_updated와_version_1을_저장한다() {
    insertCreateMarker();

    int updated =
        markerMapper.updateMarker(
            new MarkerUpdateRecord(
                CREATE_MARKER_ID,
                1L,
                MarkerType.NOTE,
                MarkerGeometryFixtures.VALID_MARKER_POINT,
                "mapper updated memo",
                MarkerStatus.UPDATED,
                2L));

    assertThat(updated).isEqualTo(1);
    MarkerRecord record = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    assertThat(record.getMarkerType()).isEqualTo(MarkerType.NOTE.name());
    assertThat(record.getMemo()).isEqualTo("mapper updated memo");
    assertThat(record.getStatus()).isEqualTo(MarkerStatus.UPDATED.name());
    assertThat(record.getVersion()).isEqualTo(2L);

    int staleUpdate =
        markerMapper.updateMarker(
            new MarkerUpdateRecord(
                CREATE_MARKER_ID,
                1L,
                MarkerType.CLUE,
                MarkerGeometryFixtures.VALID_MARKER_POINT,
                "stale update",
                MarkerStatus.UPDATED,
                2L));
    assertThat(staleUpdate).isZero();
  }

  @Test
  @DisplayName("marker delete는 expected version이 맞을 때 DELETED와 version+1을 저장한다")
  void marker_delete는_expected_version이_맞을_때_deleted와_version_1을_저장한다() {
    insertCreateMarker();

    int deleted =
        markerMapper.deleteMarker(
            new MarkerDeleteRecord(CREATE_MARKER_ID, 1L, MarkerStatus.DELETED, 2L));

    assertThat(deleted).isEqualTo(1);
    MarkerRecord record = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    assertThat(record.getStatus()).isEqualTo(MarkerStatus.DELETED.name());
    assertThat(record.getVersion()).isEqualTo(2L);

    int repeatedDelete =
        markerMapper.deleteMarker(
            new MarkerDeleteRecord(CREATE_MARKER_ID, 2L, MarkerStatus.DELETED, 3L));
    assertThat(repeatedDelete).isZero();
  }

  private void insertCreateMarker() {
    markerMapper.insertCreate(
        new MarkerCreateRecord(
            MarkerGeometryFixtures.INCIDENT_ID,
            CREATE_MARKER_ID,
            MarkerGeometryFixtures.OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            MarkerGeometryFixtures.VALID_MARKER_POINT,
            "S14P31C106-71 field clue",
            CLIENT_TS,
            CREATE_ACCOUNT_ID,
            CREATE_POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));
  }
}
