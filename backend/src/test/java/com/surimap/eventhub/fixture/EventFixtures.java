package com.surimap.eventhub.fixture;

import java.util.UUID;

/**
 * S4 Realtime Event Hub 하네스 픽스처 상수 (EventFixtures).
 *
 * <p>S4.json harness_fixtures 섹션의 UUID를 그대로 Java 상수로 선언한다. fixture ID를 임의로 축약하거나 재명명하지
 * 않는다.
 *
 * <p>참조: docs/spec/specs/S4.json §harness_fixtures
 */
public final class EventFixtures {

  private EventFixtures() {}

  // ── SC-08: 지원 요청 empty FCM recipient skip ─────────────────────────

  /** SC-08/SC-09 공통 사건 ID */
  public static final UUID INCIDENT_ID_01 = UUID.fromString("10000000-0000-4000-8000-000000000001");

  /** SC-08 이벤트 ID (SUPPORT_REQUEST_CREATED) */
  public static final UUID SC08_EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000811");

  /** SC-08 이벤트 타입 */
  public static final String SC08_EVENT_TYPE = "SUPPORT_REQUEST_CREATED";

  // ── SC-09: Outbox replay convergence ──────────────────────────────────

  /** SC-09 이벤트 ID (PATH_APPENDED) */
  public static final UUID SC09_EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000901");

  /** SC-09 이벤트 타입 */
  public static final String SC09_EVENT_TYPE = "PATH_APPENDED";

  // ── 종료·파기 사건 ─────────────────────────────────────────────────────

  /** CLOSED 사건 ID (closed_and_purged_stream fixture) */
  public static final UUID CLOSED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000012");

  /** CLOSED 사건 terminal event ID (INCIDENT_CLOSED replay 종료 지점) */
  public static final UUID CLOSED_TERMINAL_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000001212");

  /** PURGED 사건 ID (closed_and_purged_stream fixture) */
  public static final UUID PURGED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000013");

  // ── 중복 이벤트 dedupe ────────────────────────────────────────────────

  /** duplicate_event_dedupe fixture 이벤트 ID */
  public static final UUID DEDUPE_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000701");
}
