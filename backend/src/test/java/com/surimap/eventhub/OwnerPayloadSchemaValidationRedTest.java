package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.fixture.EventFixtures;
import com.surimap.eventhub.validation.BaseEventValidator;
import com.surimap.eventhub.validation.InvalidEventEnvelopeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이벤트 타입별 payload 스키마 검증 RED 테스트 (L2-T05 / S14P31C106-159).
 *
 * <p>PATH_APPENDED, SUPPORT_REQUEST_CREATED, POLICE_PHONE_HEARTBEAT_UPDATED 이벤트 타입에 대한 payload 검증을
 * 다룬다. 아직 존재하지 않는 {@code BaseEventValidator}를 대상으로 작성한 RED 테스트다.
 *
 * <p>참조:
 *
 * <ul>
 *   <li>docs/spec/boundaries.md §4.4 Event Catalog 공통 payload 필드
 *   <li>docs/spec/specs/S4.json §harness_fixtures
 *   <li>명칭 주의: 코드는 PolicePhone/POLICE_PHONE_HEARTBEAT_UPDATED 사용
 * </ul>
 */
@DisplayName("이벤트 타입별 payload 스키마 검증")
class OwnerPayloadSchemaValidationRedTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-05-08T00:00:00Z");

  // ═══════════════════════════════════════════════════════════
  //  1. PATH_APPENDED payload
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("PATH_APPENDED payload 검증")
  class PathAppendedPayloadValidation {

    @Test
    @DisplayName("id/status/version 포함한 PATH_APPENDED payload는 validate() 통과")
    void validPathAppendedPayloadPassesValidation() {
      // S4.json sc09_outbox_replay_convergence fixture
      Map<String, Object> payload =
          Map.of(
              "id", "30000000-0000-4000-8000-000000000501",
              "status", "RECORDING",
              "version", 7);

      PublishRequest request =
          new PublishRequest(
              EventFixtures.SC09_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC09_EVENT_TYPE, // PATH_APPENDED
              1,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              OCCURRED_AT,
              payload);

      assertThatCode(() -> BaseEventValidator.validate(request)).doesNotThrowAnyException();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  2. SUPPORT_REQUEST_CREATED payload
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("SUPPORT_REQUEST_CREATED payload 검증")
  class SupportRequestCreatedPayloadValidation {

    @Test
    @DisplayName("id/status/version 포함한 SUPPORT_REQUEST_CREATED payload는 validate() 통과")
    void validSupportRequestCreatedPayloadPassesValidation() {
      // S4.json sc08_empty_fcm_recipient_skip fixture
      Map<String, Object> payload =
          Map.of(
              "id", "50000000-0000-4000-8000-000000000801",
              "status", "REQUESTED",
              "version", 1);

      PublishRequest request =
          new PublishRequest(
              EventFixtures.SC08_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE, // SUPPORT_REQUEST_CREATED
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              OCCURRED_AT,
              payload);

      assertThatCode(() -> BaseEventValidator.validate(request)).doesNotThrowAnyException();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  4. payload version null 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("payload version null 검증")
  class PayloadVersionNullValidation {

    @Test
    @DisplayName("payload에 version 값이 null이면 InvalidEventEnvelopeException이 발생한다")
    void payloadVersionNullThrowsException() {
      // HashMap을 사용해 null 값 허용 (Map.of는 null 값 불가)
      Map<String, Object> payload = new HashMap<>();
      payload.put("id", "30000000-0000-4000-8000-000000000501");
      payload.put("status", "RECORDING");
      payload.put("version", null);

      PublishRequest request =
          new PublishRequest(
              EventFixtures.SC09_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC09_EVENT_TYPE,
              1,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              OCCURRED_AT,
              payload);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }
}
