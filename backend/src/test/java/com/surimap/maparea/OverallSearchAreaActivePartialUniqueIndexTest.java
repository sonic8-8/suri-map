package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.command.OverallSearchAreaService;
import com.surimap.maparea.command.request.CreateOverallSearchAreaServiceRequest;
import com.surimap.maparea.command.response.OverallSearchAreaCommandResponse;
import com.surimap.maparea.event.PublishRequestCollector;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T01 AC-S2-01 edge case: 같은 incident의 ACTIVE overall_search_area 중복은 막고 SUPERSEDED 이력 여러 건은
 * 허용한다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §tdd_red_tests backend[5], §edge_cases 7번째 항목.
 */
@DisplayName("L3-T01 OverallSearchAreaActivePartialUniqueIndex")
class OverallSearchAreaActivePartialUniqueIndexTest {

  private PublishRequestCollector eventCollector;
  private OverallSearchAreaService service;

  @BeforeEach
  void setUp() {
    eventCollector = new PublishRequestCollector();
    service = new OverallSearchAreaService(eventCollector);
  }

  @Test
  @DisplayName("같은 incident에 ACTIVE overall_search_area를 중복 생성하면 area_state_conflict 409로 실패한다")
  void 같은_incident에_ACTIVE_overall_search_area_중복_생성은_거부된다() {
    CreateOverallSearchAreaServiceRequest first =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));

    service.create(first);

    CreateOverallSearchAreaServiceRequest duplicate =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:01:00Z"));

    assertThatThrownBy(() -> service.create(duplicate))
        .isInstanceOf(com.surimap.maparea.command.exception.AreaStateConflictException.class);
  }

  @Test
  @DisplayName("수정(update)으로 overall_search_area를 교체하면 이전 row는 SUPERSEDED로 남고 ACTIVE는 1개다")
  void update_후_SUPERSEDED_이력은_남고_ACTIVE는_1개다() {
    CreateOverallSearchAreaServiceRequest createRequest =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));
    OverallSearchAreaCommandResponse created = service.create(createRequest);

    com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest updateRequest =
        new com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest(
            created.id(),
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            created.version(),
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T01:00:00Z"));

    OverallSearchAreaCommandResponse updated = service.update(updateRequest);

    // updated row는 여전히 ACTIVE
    assertThat(updated.status()).isEqualTo("ACTIVE");
    // 이전 row 포함 ACTIVE row 수는 1
    assertThat(service.countActiveOverallAreas(BoundaryAreaFixtures.INCIDENT_ID)).isEqualTo(1);
  }

  @Test
  @DisplayName("SUPERSEDED overall_search_area 이력 여러 건은 허용된다")
  void SUPERSEDED_이력_여러_건은_허용된다() {
    CreateOverallSearchAreaServiceRequest createRequest =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));
    OverallSearchAreaCommandResponse v1 = service.create(createRequest);

    // first update
    com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest update1 =
        new com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest(
            v1.id(),
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            v1.version(),
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T01:00:00Z"));
    OverallSearchAreaCommandResponse v2 = service.update(update1);

    // second update
    com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest update2 =
        new com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest(
            v2.id(),
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            v2.version(),
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T02:00:00Z"));
    service.update(update2);

    // ACTIVE는 여전히 1, SUPERSEDED 이력은 2 이상
    assertThat(service.countActiveOverallAreas(BoundaryAreaFixtures.INCIDENT_ID)).isEqualTo(1);
    assertThat(service.countSupersededOverallAreas(BoundaryAreaFixtures.INCIDENT_ID))
        .isGreaterThanOrEqualTo(2);
  }
}
