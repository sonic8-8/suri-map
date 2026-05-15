package com.surimap.marker.seed;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.INCIDENT_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ALIAS;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.seed.SeedMarker;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L5-T05B ReferenceMarkerSeed 구현 test. */
@DisplayName("L5-T05B ReferenceMarkerSeed service")
class ReferenceMarkerSeedServiceTest {

  private final InMemoryMarkerRepository repository = new InMemoryMarkerRepository();
  private final ReferenceMarkerSeed referenceMarkerSeed =
      new ReferenceMarkerSeedService(repository);

  @Test
  @DisplayName("incident import seed marker를 MOCK_SEED ACTIVE row로 저장한다")
  void incident_import_seed_marker를_mock_seed_active_row로_저장한다() {
    ReferenceMarkerSeedResult result =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));

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
    assertThat(marker.location().getX()).isEqualTo(126.913400);
    assertThat(marker.location().getY()).isEqualTo(35.163100);
    assertThat(marker.photoSummary()).isEmpty();
    assertThat(repository.records()).hasSize(1);
  }

  @Test
  @DisplayName("fixture alias는 하네스 seed marker ID를 보존한다")
  void fixture_alias는_하네스_seed_marker_id를_보존한다() {
    assertThat(MarkerSeedFixtures.INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
    assertThat(MarkerSeedFixtures.OP1_ALIAS).isEqualTo("op-precinct-001-op1");
    assertThat(MARKER_ALIAS).isEqualTo("mk-precinct-clue-001");
  }

  @Test
  @DisplayName("동일 markerId seed 재호출은 중복 row를 만들지 않는다")
  void 동일_markerId_seed_재호출은_중복_row를_만들지_않는다() {
    referenceMarkerSeed.createForIncident(
        INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));
    ReferenceMarkerSeedResult second =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));

    assertThat(second.markers()).extracting(MarkerView::id).containsExactly(MARKER_ID);
    assertThat(repository.records()).hasSize(1);
  }

  @Test
  @DisplayName("초기 기준점 seed는 active overall_search_area 없이도 저장한다")
  void 초기_기준점_seed는_active_overall_search_area_없이도_저장한다() {
    ReferenceMarkerSeedResult result =
        referenceMarkerSeed.createForIncident(
            UUID.randomUUID(), List.of(MarkerSeedFixtures.referenceClueSeed()));

    assertThat(result.markers()).hasSize(1);
    assertThat(repository.records()).hasSize(1);
  }

  @Test
  @DisplayName("초기 기준점 seed 좌표 자체가 유효하지 않으면 저장하지 않는다")
  void 초기_기준점_seed_좌표_자체가_유효하지_않으면_저장하지_않는다() {
    assertThatThrownBy(
            () ->
                referenceMarkerSeed.createForIncident(
                    INCIDENT_ID,
                    List.of(seedWithLocation(com.surimap.marker.domain.fixture.MarkerGeometryFixtures.NAN_POINT))))
        .isInstanceOf(InvalidGeometryException.class);

    assertThat(repository.records()).isEmpty();
  }

  private static SeedMarker seedWithLocation(Point location) {
    return new SeedMarker(
        MARKER_ID,
        OP1_ID,
        null,
        MarkerType.CLUE,
        null,
        location,
        MarkerSeedFixtures.MEMO,
        MarkerSeedFixtures.OCCURRED_AT,
        MarkerSeedFixtures.ACCOUNT_ID,
        null);
  }
}
