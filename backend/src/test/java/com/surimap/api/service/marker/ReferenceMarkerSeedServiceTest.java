package com.surimap.api.service.marker;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.INCIDENT_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerType;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.port.ReferenceMarkerSeed.SeedMarker;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReferenceMarkerSeedServiceTest extends PostGisIntegrationTestSupport {

  @Autowired private ReferenceMarkerSeedService referenceMarkerSeedService;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private MarkerService markerService;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update("DELETE FROM operational_period WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, started_by_account_id,
            started_at, version, created_at, updated_at
        ) VALUES (?, ?, 1, 'ACTIVE', 'INITIAL', ?, NOW(), 1, NOW(), NOW())
        """,
        OP1_ID,
        INCIDENT_ID,
        MarkerSeedFixtures.ACCOUNT_ID);
  }

  @Test
  @DisplayName("초기 기준 마커를 등록하면, 수색구역이 없어도 최초 수색 차수에 활성 상태로 저장한다")
  void createForIncident_withoutSearchArea_savesReferenceMarkerInFirstOp() {
    // given: 전체 수색구역이 없는 사건에 초기 기준 마커를 등록한다.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM search_area WHERE operational_period_id = ?",
                Integer.class,
                OP1_ID))
        .isZero();

    // when: 실제 서비스와 MyBatis·PostGIS를 통해 기준 마커를 저장한다.
    referenceMarkerSeedService.createForIncident(INCIDENT_ID, List.of(referenceClueSeed()));

    // then: 저장된 마커에 기존 식별자·유형·출처·상태·좌표를 유지한다.
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getId()).isEqualTo(MARKER_ID);
    assertThat(marker.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(marker.getOperationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(marker.getMarkerType()).isEqualTo(MarkerType.CLUE.name());
    assertThat(marker.getMarkerSource()).isEqualTo(MarkerSource.MOCK_SEED.name());
    assertThat(marker.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(marker.getLocation().getSRID()).isEqualTo(4326);
    assertThat(marker.getLocation().getX()).isEqualTo(126.913400);
    assertThat(marker.getLocation().getY()).isEqualTo(35.163100);
    assertThat(marker.getMemo()).isEqualTo(MarkerSeedFixtures.MEMO);
    assertThat(marker.getOccurredAt()).isEqualTo(MarkerSeedFixtures.OCCURRED_AT);
    assertThat(marker.getCreatedByAccountId()).isEqualTo(MarkerSeedFixtures.ACCOUNT_ID);
    assertThat(marker.getPolicePhoneId()).isNull();
    assertThat(marker.getDutyShiftId()).isNull();
    assertThat(marker.getSupportRequestType()).isNull();
    assertThat(countMarkers()).isEqualTo(1);
    // then: 오프라인 패키지에서 사용하는 실제 마커 조회로도 저장한 기준 마커를 읽는다.
    assertThat(markerService.list(INCIDENT_ID, null, null, null).getMarkers())
        .singleElement()
        .satisfies(
            response -> {
              assertThat(response.getId()).isEqualTo(MARKER_ID);
              assertThat(response.getPhotoSummary()).isEmpty();
            });
  }

  @Test
  @DisplayName("같은 기준 마커를 다시 등록하면, 기존 내용을 바꾸거나 중복 저장하지 않는다")
  void createForIncident_sameMarker_preservesStoredMarkerWithoutDuplicates() {
    // given: 같은 식별자의 기준 마커가 이미 저장되어 있다.
    referenceMarkerSeedService.createForIncident(INCIDENT_ID, List.of(referenceClueSeed()));
    jdbcTemplate.update(
        "UPDATE marker SET memo = ?, version = 2 WHERE id = ?", "수정한 기준 마커", MARKER_ID);

    // when: 같은 기준 마커를 다시 등록한다.
    referenceMarkerSeedService.createForIncident(INCIDENT_ID, List.of(referenceClueSeed()));

    // then: 처음 가져온 내용으로 되돌리지 않고 마커 수도 늘리지 않는다.
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getMemo()).isEqualTo("수정한 기준 마커");
    assertThat(marker.getVersion()).isEqualTo(2L);
    assertThat(countMarkers()).isEqualTo(1);
  }

  @Test
  @DisplayName("초기 마커 묶음에 잘못된 좌표가 있으면, 앞의 정상 마커도 저장하지 않는다")
  void createForIncident_invalidLocationInBatch_rejectsBeforeSavingAnyMarker() {
    // given: 정상 마커 다음에 숫자로 표현할 수 없는 좌표의 마커가 있다.
    SeedMarker invalid =
        new SeedMarker("CLUE", "MOCK_SEED", MarkerSeedFixtures.MEMO, Double.NaN, 35.163100);

    // when & then: 모든 좌표를 먼저 검사해 일부 마커만 저장되는 것을 막는다.
    assertThatThrownBy(
            () ->
                referenceMarkerSeedService.createForIncident(
                    INCIDENT_ID, List.of(referenceClueSeed(), invalid)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);
    assertThat(countMarkers()).isZero();
  }

  @Test
  @DisplayName("등록할 초기 마커가 없으면, 마커를 저장하지 않는다")
  void createForIncident_emptyList_doesNotSaveMarkers() {
    // given: 사건에 등록할 기준 마커가 없다.
    // when: 빈 목록으로 초기 마커 등록을 요청한다.
    referenceMarkerSeedService.createForIncident(INCIDENT_ID, List.of());

    // then: DB에 마커를 남기지 않는다.
    assertThat(countMarkers()).isZero();
  }

  @Test
  @DisplayName("사건의 최초 수색 차수가 없으면, 기준 마커를 저장하지 않고 실패한다")
  void createForIncident_missingFirstOp_rejectsWithoutSavingMarkers() {
    // given: 수색 차수가 생성되지 않은 사건이다.
    UUID incidentId = UUID.randomUUID();

    // when & then: 다른 사건의 수색 차수를 사용하거나 일부 마커만 저장하지 않는다.
    assertThatThrownBy(
            () ->
                referenceMarkerSeedService.createForIncident(
                    incidentId, List.of(referenceClueSeed())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("op1_not_found_for_reference_marker");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker WHERE incident_id = ?", Integer.class, incidentId))
        .isZero();
  }

  private static SeedMarker referenceClueSeed() {
    return new SeedMarker(
        "CLUE",
        "MOCK_SEED",
        MarkerSeedFixtures.MEMO,
        MarkerSeedFixtures.REFERENCE_POINT.getX(),
        MarkerSeedFixtures.REFERENCE_POINT.getY());
  }

  private int countMarkers() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM marker WHERE incident_id = ?", Integer.class, INCIDENT_ID);
  }
}
