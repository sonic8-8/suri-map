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
 * L3-T01 AC-S2-05 / FR-23: 완료 구역과 경로 사이의 빈 공간이 있어도 S2 서비스는 missing_area/recommended_area 결과를 생성하지
 * 않는다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §tdd_red_tests backend[3], §acceptance_criteria AC-S2-05,
 * §scope excluded 2번째 항목, §harness_constraints forbidden 1번째 항목.
 */
@DisplayName("L3-T01 NoAutomaticBlindspotDecision")
class NoAutomaticBlindspotDecisionTest {

  private PublishRequestCollector eventCollector;
  private OverallSearchAreaService service;

  @BeforeEach
  void setUp() {
    eventCollector = new PublishRequestCollector();
    service = new OverallSearchAreaService(eventCollector);
  }

  @Test
  @DisplayName("overall_search_area 생성 후 응답에 missing_area 필드가 없다")
  void overall_search_area_생성_응답에_missing_area_필드가_없다() {
    CreateOverallSearchAreaServiceRequest request =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));

    OverallSearchAreaCommandResponse response = service.create(request);

    // OverallSearchAreaCommandResponse는 missing_area, blindspot, recommended_area 필드를 노출하지 않는다.
    // reflection으로 필드명 존재 자체를 금지한다.
    java.lang.reflect.Field[] fields = response.getClass().getDeclaredFields();
    for (java.lang.reflect.Field f : fields) {
      String name = f.getName().toLowerCase();
      assertThat(name)
          .as("응답 DTO에 자동 판단 필드가 존재하면 안 됩니다: " + f.getName())
          .doesNotContain("missing")
          .doesNotContain("blindspot")
          .doesNotContain("recommended");
    }
  }

  @Test
  @DisplayName("OverallSearchAreaService에 missing_area 또는 blindspot 반환 메서드가 없다")
  void service에_missing_area_또는_blindspot_메서드가_없다() {
    java.lang.reflect.Method[] methods = OverallSearchAreaService.class.getDeclaredMethods();
    for (java.lang.reflect.Method m : methods) {
      String name = m.getName().toLowerCase();
      assertThat(name)
          .as("OverallSearchAreaService에 자동 판단 메서드가 존재하면 안 됩니다: " + m.getName())
          .doesNotContain("missing")
          .doesNotContain("blindspot")
          .doesNotContain("recommended");
    }
  }

  @Test
  @DisplayName("overall_search_area 생성 후 발행된 SEARCH_AREA_CHANGED payload에 missing_area가 없다")
  void SEARCH_AREA_CHANGED_payload에_missing_area가_없다() {
    CreateOverallSearchAreaServiceRequest request =
        new CreateOverallSearchAreaServiceRequest(
            BoundaryAreaFixtures.INCIDENT_ID,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            null,
            BoundaryAreaFixtures.INCIDENT_ID,
            Instant.parse("2026-05-07T00:00:00Z"));

    service.create(request);

    // PublishRequestCollector가 수집한 payload field names 에 자동 판단 필드가 없음
    assertThat(eventCollector.publishedPayloadFieldNames())
        .doesNotContain(
            "missingArea",
            "blindspot",
            "recommendedArea",
            "missing_area",
            "blind_spot",
            "recommended_area");
  }
}
