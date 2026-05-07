package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;

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
 * L3-T01 AC-S2-01: overall_search_area 변경 성공 시 overall_search_area row와 SEARCH_AREA_CHANGED만 생기고
 * search_area/search_area_assignment row는 변경되지 않는다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §tdd_red_tests backend[0], §acceptance_criteria AC-S2-01.
 */
@DisplayName("L3-T01 OverallSearchAreaWritePublishesEventWithoutAreaMutation")
class OverallSearchAreaWritePublishesEventWithoutAreaMutationTest {

  /**
   * S4 EventHub.publish mock: in-memory PublishRequest collector.
   *
   * <p>S2.json §dependencies S4 stub_strategy 기준.
   */
  private PublishRequestCollector eventCollector;

  private OverallSearchAreaService service;

  @BeforeEach
  void setUp() {
    eventCollector = new PublishRequestCollector();
    // TODO L3-T01: OverallSearchAreaService 생성자 signature 확인 후 wiring.
    // 의존: OverallSearchAreaMapper, GeometryValidationService, OperationalPeriodQuery, EventHub mock
    service = new OverallSearchAreaService(eventCollector);
  }

  @Test
  @DisplayName("overall_search_area 생성 성공 시 SEARCH_AREA_CHANGED가 발행된다")
  void create_성공_시_SEARCH_AREA_CHANGED가_발행된다() {
    CreateOverallSearchAreaServiceRequest request =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            /* memo= */ null,
            /* commanderAccountId= */ BoundaryAreaFixtures.INCIDENT_ID, // stub account
            Instant.parse("2026-05-07T00:00:00Z"));

    OverallSearchAreaCommandResponse response = service.create(request);

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("ACTIVE");
    // SEARCH_AREA_CHANGED event publish 요청 1건
    assertThat(eventCollector.publishedTypes()).containsExactly("SEARCH_AREA_CHANGED");
  }

  @Test
  @DisplayName("overall_search_area 생성 후 search_area row는 추가되지 않는다")
  void overall_search_area_생성_후_search_area_row는_추가되지_않는다() {
    CreateOverallSearchAreaServiceRequest request =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));

    service.create(request);

    // search_area/search_area_assignment row 변경 없음 — mutated table list는 overall_search_area만
    assertThat(eventCollector.mutatedTables())
        .doesNotContain("search_area", "search_area_assignment");
  }

  @Test
  @DisplayName("overall_search_area 수정 성공 시 SEARCH_AREA_CHANGED가 발행된다")
  void update_성공_시_SEARCH_AREA_CHANGED가_발행된다() {
    // given: create first
    CreateOverallSearchAreaServiceRequest createRequest =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));
    OverallSearchAreaCommandResponse created = service.create(createRequest);
    eventCollector.clear();

    // when: update geometry
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

    assertThat(updated.version()).isGreaterThan(created.version());
    assertThat(eventCollector.publishedTypes()).containsExactly("SEARCH_AREA_CHANGED");
  }

  @Test
  @DisplayName("overall_search_area 수정 성공 시 status는 ACTIVE를 유지한다")
  void update_후_status는_ACTIVE를_유지한다() {
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

    assertThat(updated.status()).isEqualTo("ACTIVE");
  }
}
