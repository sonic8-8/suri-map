package com.surimap.domain.marker;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.marker.MarkerMapper.AttachedPhotoRow;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.sql.Timestamp;
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

  private static final UUID INCIDENT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa7108");
  private static final UUID INCIDENT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb7108");
  private static final UUID OP_A = UUID.fromString("11111111-1111-4111-8111-111111117108");
  private static final UUID OP_B = UUID.fromString("22222222-2222-4222-8222-222222227108");
  private static final UUID ACTIVE_MARKER_ID =
      UUID.fromString("33333333-3333-4333-8333-333333337108");
  private static final UUID UPDATED_MARKER_ID =
      UUID.fromString("44444444-4444-4444-8444-444444447108");
  private static final UUID DELETED_MARKER_ID =
      UUID.fromString("55555555-5555-4555-8555-555555557108");
  private static final UUID OTHER_INCIDENT_MARKER_ID =
      UUID.fromString("66666666-6666-4666-8666-666666667108");
  private static final UUID ACCOUNT_ID = UUID.fromString("77777777-7777-4777-8777-777777777108");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("88888888-8888-4888-8888-888888887108");

  @Autowired private MarkerMapper markerMapper;

  @BeforeEach
  void cleanMarkerTables() {
    jdbcTemplate.execute("TRUNCATE TABLE photo, marker");
  }

  @Test
  @DisplayName("마커 테이블을 생성하면, 필수 사건 ID와 좌표 형식·조회용 인덱스가 준비된다")
  void loadSchema_markerTable_preservesGeometryAndIndexes() {
    // given: Flyway가 마커 테이블을 만든 실제 PostgreSQL/PostGIS DB다.
    // when: 사건 ID·좌표 컬럼과 인덱스 구성을 조회한다.
    String incidentIdNullable =
        jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_name = 'marker' AND column_name = 'incident_id'",
            String.class);
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

    // then: 사건 ID가 필수이며 Point 좌표, SRID 4326과 조회용 인덱스가 존재한다.
    assertThat(incidentIdNullable).isEqualTo("NO");
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

  @Test
  @DisplayName("사건 ID만 지정하면, 해당 사건의 활성·수정 마커를 기록 시각 순으로 조회한다")
  void findByIncident_withoutOptionalFilters_returnsActiveAndUpdatedMarkersInOrder() {
    // given: 상태와 사건이 다른 마커를 저장한다.
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    insertMarker(UPDATED_MARKER_ID, INCIDENT_A, OP_A, "FIELD_CONDITION", "UPDATED", 2L);
    insertMarker(DELETED_MARKER_ID, INCIDENT_A, OP_A, "NOTE", "DELETED", 3L);
    insertMarker(OTHER_INCIDENT_MARKER_ID, INCIDENT_B, OP_B, "CLUE", "ACTIVE", 1L);

    // when: 실제 Mapper로 조건에 맞는 마커를 조회한다.
    List<UUID> ids =
        markerMapper.findByIncident(INCIDENT_A, null, null, null).stream()
            .map(Marker::getId)
            .toList();

    // then: 삭제한 마커와 다른 사건의 마커를 제외하고 기록 시각 순으로 반환한다.
    assertThat(ids).containsExactly(ACTIVE_MARKER_ID, UPDATED_MARKER_ID);
  }

  @Test
  @DisplayName("수색 차수·유형·상태를 지정하면, 모든 조건에 맞는 마커만 조회한다")
  void findByIncident_withOptionalFilters_returnsOnlyMatchingMarkers() {
    // given: 수색 차수와 유형·상태가 다른 마커를 저장한다.
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    insertMarker(UPDATED_MARKER_ID, INCIDENT_A, OP_A, "FIELD_CONDITION", "UPDATED", 2L);
    insertMarker(OTHER_INCIDENT_MARKER_ID, INCIDENT_A, OP_B, "CLUE", "ACTIVE", 3L);

    // when: 실제 Mapper로 조건에 맞는 마커를 조회한다.
    List<UUID> ids =
        markerMapper
            .findByIncident(INCIDENT_A, OP_A, MarkerType.FIELD_CONDITION, MarkerStatus.UPDATED)
            .stream()
            .map(Marker::getId)
            .toList();

    // then: 지정한 수색 차수·유형·상태가 모두 일치하는 마커만 반환한다.
    assertThat(ids).containsExactly(UPDATED_MARKER_ID);
  }

  @Test
  @DisplayName("마커의 첨부 사진을 조회하면, 업로드 대기 사진을 제외하고 첨부 시각 순으로 반환한다")
  void findAttachedPhotoSummaries_pendingAndAttachedPhotos_returnsAttachedPhotosInOrder() {
    // given: 첨부 시각과 업로드 상태가 다른 사진을 저장한다.
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    UUID laterPhotoId = UUID.fromString("99999999-9999-4999-8999-999999997108");
    UUID pendingPhotoId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeee7108");
    UUID earlierPhotoId = UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffff7108");
    insertPhoto(laterPhotoId, ACTIVE_MARKER_ID, "ATTACHED", "2026-04-28T00:03:00Z", 2L);
    insertPhoto(pendingPhotoId, ACTIVE_MARKER_ID, "PENDING_UPLOAD", null, 1L);
    insertPhoto(earlierPhotoId, ACTIVE_MARKER_ID, "ATTACHED", "2026-04-28T00:01:00Z", 3L);

    // when: 실제 Mapper로 마커의 첨부 사진을 조회한다.
    List<UUID> photoIds =
        markerMapper.findAttachedPhotoSummariesByMarkerIds(List.of(ACTIVE_MARKER_ID)).stream()
            .map(AttachedPhotoRow::photoId)
            .toList();

    // then: 첨부가 끝난 사진만 첨부 시각 순으로 반환한다.
    assertThat(photoIds).containsExactly(earlierPhotoId, laterPhotoId);
  }

  private void insertMarker(
      UUID markerId, UUID incidentId, UUID opId, String type, String status, long version) {
    jdbcTemplate.update(
        """
        INSERT INTO marker (
            id,
            incident_id,
            operational_period_id,
            duty_shift_id,
            marker_type,
            support_request_type,
            location,
            memo,
            occurred_at,
            created_by_account_id,
            police_phone_id,
            marker_source,
            status,
            version
        )
        VALUES (?, ?, ?, NULL, ?, NULL, ST_SetSRID(ST_MakePoint(126.9134, 35.1631), 4326),
                ?, ?, ?, ?, 'APP', ?, ?)
        """,
        markerId,
        incidentId,
        opId,
        type,
        "L5-T08 marker for " + incidentId,
        Timestamp.from(Instant.parse("2026-04-28T00:00:00Z").plusSeconds(version)),
        ACCOUNT_ID,
        POLICE_PHONE_ID,
        status,
        version);
  }

  private void insertPhoto(
      UUID photoId, UUID markerId, String status, String attachedAt, long version) {
    jdbcTemplate.update(
        """
        INSERT INTO photo (
            id,
            marker_id,
            object_key,
            status,
            attached_at,
            content_type,
            size_bytes,
            version
        )
        VALUES (?, ?, ?, ?, ?::timestamptz, 'image/jpeg', 1024, ?)
        """,
        photoId,
        markerId,
        "markers/" + INCIDENT_A + "/" + markerId + "/" + photoId + ".jpg",
        status,
        attachedAt,
        version);
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
