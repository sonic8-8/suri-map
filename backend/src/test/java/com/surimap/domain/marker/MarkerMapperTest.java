package com.surimap.domain.marker;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MarkerMapperTest extends PostGisIntegrationTestSupport {

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
  @DisplayName("마커 테이블을 생성하면, 좌표 형식과 조회용 인덱스가 준비된다")
  void loadSchema_markerTable_preservesGeometryAndIndexes() {
    // given: Flyway가 마커 테이블을 만든 실제 PostgreSQL/PostGIS DB다.
    // when: 좌표 컬럼과 인덱스 구성을 조회한다.
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

    // then: Point 좌표, SRID 4326과 마커 조회용 인덱스가 존재한다.
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
  @DisplayName("사전 등록 마커를 저장하면, 마커 정보와 Point 좌표를 다시 읽을 수 있다")
  void insertSeed_referenceMarker_loadsSavedPoint() {
    // given: 사전 등록할 마커를 준비한다.
    // when: 저장한 뒤 같은 ID로 조회한다.
    markerMapper.insertSeed(
        Marker.fromSeed(MarkerSeedFixtures.INCIDENT_ID, MarkerSeedFixtures.referenceClueSeed()));

    List<Marker> records = markerMapper.findByIds(List.of(MARKER_ID));

    // then: 저장한 마커와 좌표를 같은 도메인 객체로 읽는다.
    assertThat(records).hasSize(1);
    Marker record = records.get(0);
    assertThat(record.getId()).isEqualTo(MARKER_ID);
    assertThat(record.getMarkerSource()).isEqualTo(MarkerSource.MOCK_SEED.name());
    assertThat(record.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(record.getVersion()).isEqualTo(1L);
    assertThat(record.getLocation()).isNotNull();
    assertThat(record.getLocation().getSRID()).isEqualTo(4326);
    assertThat(record.getLocation().getGeometryType()).isEqualTo("Point");
  }

  @Test
  @DisplayName("현장 마커를 저장하면, 계정·업무폰·수색 차수와 좌표를 함께 읽을 수 있다")
  void insertCreate_fieldMarker_loadsContextAndPoint() {
    // given: 생성할 현장 마커를 준비한다.
    // when: 실제 Mapper로 저장한다.
    markerMapper.insertCreate(
        Marker.builder()
            .incidentId(MarkerGeometryFixtures.INCIDENT_ID)
            .id(CREATE_MARKER_ID)
            .operationalPeriodId(MarkerGeometryFixtures.OP1_ID)
            .dutyShiftId(null)
            .markerType(MarkerType.CLUE)
            .supportRequestType(null)
            .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
            .memo("S14P31C106-71 field clue")
            .occurredAt(CLIENT_TS)
            .createdByAccountId(CREATE_ACCOUNT_ID)
            .policePhoneId(CREATE_POLICE_PHONE_ID)
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(1L)
            .build());

    List<Marker> records = markerMapper.findByIds(List.of(CREATE_MARKER_ID));

    // then: 저장한 마커와 좌표를 같은 도메인 객체로 읽는다.
    assertThat(records).hasSize(1);
    Marker record = records.get(0);
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
    assertThat(record.getLocation().getX()).isEqualTo(126.913400);
    assertThat(record.getLocation().getY()).isEqualTo(35.163100);
  }

  @Test
  @DisplayName("같은 버전으로 동시에 수정하면, 먼저 저장한 변경만 남는다")
  void updateMarker_sameExpectedVersion_preservesFirstWrite() {
    // given: 같은 버전의 마커를 읽은 두 요청이다.
    insertCreateMarker();
    Marker first = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    Marker second = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    first.update(1L, "NOTE", MarkerGeometryFixtures.PRECISION_OVER_6DP, "먼저 저장한 메모");
    second.update(1L, "CLUE", null, "늦게 저장한 메모");

    // when: 두 요청이 각각 예상 버전 1로 저장을 시도한다.
    int firstUpdated = markerMapper.updateMarker(first, 1L);
    int secondUpdated = markerMapper.updateMarker(second, 1L);

    // then: SQL의 버전 조건이 뒤늦은 요청을 거부한다.
    assertThat(firstUpdated).isEqualTo(1);
    assertThat(secondUpdated).isZero();
    Marker saved = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    assertThat(saved.getMarkerType()).isEqualTo("NOTE");
    assertThat(saved.getMemo()).isEqualTo("먼저 저장한 메모");
    assertThat(saved.getLocation().getX()).isEqualTo(126.9134007);
    assertThat(saved.getLocation().getY()).isEqualTo(35.1631007);
    assertThat(saved.getLocation().getSRID()).isEqualTo(4326);
    assertThat(saved.getStatus()).isEqualTo("UPDATED");
    assertThat(saved.getVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("마커를 삭제하면, 행은 남기고 이후 수정과 중복 삭제를 거부한다")
  void deleteMarker_currentVersion_preservesRowAndRejectsFurtherWrites() {
    // given: 삭제할 현장 마커를 조회한다.
    insertCreateMarker();
    Marker marker = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    marker.delete(1L);

    // when: 현재 버전으로 삭제 상태를 저장한다.
    int deleted = markerMapper.deleteMarker(marker, 1L);

    // then: 행과 기존 내용은 남고, SQL도 삭제한 행의 변경을 차단한다.
    assertThat(deleted).isEqualTo(1);
    Marker saved = markerMapper.findById(CREATE_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(saved.getMemo()).isEqualTo("S14P31C106-71 field clue");
    assertThat(saved.getLocation().getX()).isEqualTo(126.913400);
    assertThat(markerMapper.deleteMarker(marker, 2L)).isZero();
    assertThat(markerMapper.updateMarker(marker, 2L)).isZero();
  }

  private void insertCreateMarker() {
    markerMapper.insertCreate(
        Marker.builder()
            .incidentId(MarkerGeometryFixtures.INCIDENT_ID)
            .id(CREATE_MARKER_ID)
            .operationalPeriodId(MarkerGeometryFixtures.OP1_ID)
            .dutyShiftId(null)
            .markerType(MarkerType.CLUE)
            .supportRequestType(null)
            .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
            .memo("S14P31C106-71 field clue")
            .occurredAt(CLIENT_TS)
            .createdByAccountId(CREATE_ACCOUNT_ID)
            .policePhoneId(CREATE_POLICE_PHONE_ID)
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(1L)
            .build());
  }
}
