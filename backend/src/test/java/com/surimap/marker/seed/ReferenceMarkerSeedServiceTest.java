package com.surimap.marker.seed;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.INCIDENT_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerType;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReferenceMarkerSeedServiceTest extends PostGisIntegrationTestSupport {

  @Autowired private ReferenceMarkerSeed referenceMarkerSeed;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private MarkerQuery markerQuery;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", INCIDENT_ID);
  }

  @Test
  @DisplayName("초기 기준 마커를 등록하면, 수색구역이 없어도 활성 상태로 저장하고 조회 결과를 반환한다")
  void createForIncident_withoutSearchArea_savesAndReturnsReferenceMarker() {
    // given: 전체 수색구역이 없는 사건에 초기 기준 마커를 등록한다.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM search_area WHERE operational_period_id = ?",
                Integer.class,
                OP1_ID))
        .isZero();

    // when: 실제 서비스와 MyBatis·PostGIS를 통해 기준 마커를 저장한다.
    ReferenceMarkerSeedResult result =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));

    // then: 응답과 저장된 마커에 기존 식별자·유형·출처·상태·좌표를 유지한다.
    assertThat(result.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(result.markers()).hasSize(1);
    MarkerView marker = result.markers().get(0);
    assertThat(marker.id()).isEqualTo(MARKER_ID);
    assertThat(marker.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(marker.opId()).isEqualTo(OP1_ID);
    assertThat(marker.type()).isEqualTo(MarkerType.CLUE);
    assertThat(marker.source()).isEqualTo(MarkerSource.MOCK_SEED);
    assertThat(marker.status()).isEqualTo(MarkerStatus.ACTIVE);
    assertThat(marker.version()).isEqualTo(1L);
    assertThat(marker.location().getSRID()).isEqualTo(4326);
    assertThat(marker.location().getX()).isEqualTo(126.913400);
    assertThat(marker.location().getY()).isEqualTo(35.163100);
    assertThat(marker.photoSummary()).isEmpty();
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getMarkerSource())
        .isEqualTo("MOCK_SEED");
    assertThat(countMarkers()).isEqualTo(1);
    // then: 오프라인 패키지에서 사용하는 실제 마커 조회로도 저장한 기준 마커를 읽는다.
    assertThat(markerQuery.byIncident(INCIDENT_ID, MarkerQueryFilters.empty()).markers())
        .extracting(MarkerView::id)
        .containsExactly(MARKER_ID);
  }

  @Test
  @DisplayName("같은 기준 마커를 다시 등록하면, 저장된 마커를 반환하고 중복 저장하지 않는다")
  void createForIncident_sameMarker_returnsStoredMarkerWithoutDuplicates() {
    // given: 같은 식별자의 기준 마커가 이미 저장되어 있다.
    ReferenceMarkerSeedResult first =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));

    // when: 같은 기준 마커를 다시 등록한다.
    ReferenceMarkerSeedResult repeated =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));

    // then: 저장된 결과를 반환하며 마커 수와 버전을 늘리지 않는다.
    assertThat(repeated).isEqualTo(first);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(countMarkers()).isEqualTo(1);
  }

  @Test
  @DisplayName("초기 마커 묶음에 잘못된 좌표가 있으면, 앞의 정상 마커도 저장하지 않는다")
  void createForIncident_invalidLocationInBatch_rejectsBeforeSavingAnyMarker() {
    // given: 정상 마커 다음에 숫자로 표현할 수 없는 좌표의 마커가 있다.
    SeedMarker invalid =
        new SeedMarker(
            UUID.fromString("55555555-5555-5555-5555-555555550072"),
            OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            MarkerGeometryFixtures.NAN_POINT,
            MarkerSeedFixtures.MEMO,
            MarkerSeedFixtures.OCCURRED_AT,
            MarkerSeedFixtures.ACCOUNT_ID,
            null);

    // when & then: 모든 좌표를 먼저 검사해 일부 마커만 저장되는 것을 막는다.
    assertThatThrownBy(
            () ->
                referenceMarkerSeed.createForIncident(
                    INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed(), invalid)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);
    assertThat(countMarkers()).isZero();
  }

  @Test
  @DisplayName("등록할 초기 마커가 없으면, 마커를 저장하지 않고 빈 결과를 반환한다")
  void createForIncident_emptyList_returnsEmptyWithoutSavingMarkers() {
    // given: 사건에 등록할 기준 마커가 없다.
    // when: 빈 목록으로 초기 마커 등록을 요청한다.
    ReferenceMarkerSeedResult result =
        referenceMarkerSeed.createForIncident(INCIDENT_ID, List.of());

    // then: 사건 ID와 빈 목록을 반환하고 DB에도 마커를 남기지 않는다.
    assertThat(result.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(result.markers()).isEmpty();
    assertThat(countMarkers()).isZero();
  }

  private int countMarkers() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM marker WHERE incident_id = ?", Integer.class, INCIDENT_ID);
  }
}
