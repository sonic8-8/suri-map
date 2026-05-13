package com.surimap.marker.notification.fixture;

import java.util.List;
import java.util.Map;

/**
 * 알림 발송 테스트 고정 데이터 (Notification Fixtures).
 *
 * <p>S5.json의 harness fixture를 Java 상수로 재현한다. SC-08(지원 요청, 실종자 발견)과 SC-02(지원 배정) 시나리오에서 사용.
 */
public final class NotificationFixtures {

  private NotificationFixtures() {}

  // ── 공통 사건/OP 컨텍스트 ─────────────────────────────

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final String INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001";
  public static final String OP_ALIAS = "op-precinct-001-op1";
  public static final String OP_ID = "88888888-8888-8888-8888-888888880001";
  public static final String POLICE_PHONE_CODE = "dev-precinct-phone-01";
  public static final String POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  public static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";

  // ── SC-08: 지원 요청 알림 ────────────────────────────

  public static final String SUPPORT_MARKER_ALIAS = "mk-precinct-support-001";
  public static final String SUPPORT_MARKER_ID = "55555555-5555-5555-5555-555555550801";
  public static final String SUPPORT_NOTIFICATION_ALIAS = "notif-precinct-support-001";
  public static final String SUPPORT_NOTIFICATION_ID = "66666666-6666-6666-6666-666666660801";
  public static final String SUPPORT_EVENT_ID = "evt-s5-support-request-001";

  /** 지원 요청 수신 정책: 지휘관 + 현장지휘관 */
  public static final String SUPPORT_RECIPIENT_POLICY = "COMMANDERS_AND_FIELD_COMMANDERS";

  /** 지원 요청 수신 대상 계정 */
  public static final List<String> SUPPORT_RECIPIENT_ACCOUNT_IDS =
      List.of(
          "11111111-1111-1111-1111-111111110004",
          "11111111-1111-1111-1111-111111110006",
          "11111111-1111-1111-1111-111111110007",
          "11111111-1111-1111-1111-111111110008");

  /** 지원 요청 수신 대상 폴리폰 */
  public static final List<String> SUPPORT_RECIPIENT_POLICE_PHONE_IDS =
      List.of(
          "00000000-0000-0000-0000-000000000205",
          "00000000-0000-0000-0000-000000000207",
          "00000000-0000-0000-0000-000000000208");

  public static final List<String> SUPPORT_RECIPIENT_POLICE_PHONE_CODES =
      List.of("dev-alpha-phone-01", "dev-support-car-01", "dev-support-phone-01");

  /** 지원 요청 FCM 토큰 수신자 */
  public static final List<String> SUPPORT_FCM_RECIPIENTS =
      List.of("fcm:dev-alpha-phone-01", "fcm:dev-support-car-01", "fcm:dev-support-phone-01");

  /** 지원 요청 FCM payload */
  public static Map<String, Object> supportRequestPayload() {
    return Map.ofEntries(
        Map.entry("type", "SUPPORT_REQUEST_CREATED"),
        Map.entry("id", SUPPORT_NOTIFICATION_ID),
        Map.entry("markerId", SUPPORT_MARKER_ID),
        Map.entry("incidentId", INCIDENT_ID),
        Map.entry("opId", OP_ID),
        Map.entry("policePhoneId", POLICE_PHONE_ID),
        Map.entry("status", "SNAPSHOT_CREATED"),
        Map.entry("version", 1),
        Map.entry("recipientPolicy", SUPPORT_RECIPIENT_POLICY),
        Map.entry("recipientAccountIds", SUPPORT_RECIPIENT_ACCOUNT_IDS),
        Map.entry("recipientPolicePhoneIds", SUPPORT_RECIPIENT_POLICE_PHONE_IDS));
  }

  // ── SC-08: 실종자 발견 알림 ──────────────────────────

  public static final String PERSON_FOUND_MARKER_ALIAS = "mk-precinct-person-found-001";
  public static final String PERSON_FOUND_MARKER_ID = "55555555-5555-5555-5555-555555550802";
  public static final String PERSON_FOUND_NOTIFICATION_ALIAS = "notif-precinct-person-found-001";
  public static final String PERSON_FOUND_NOTIFICATION_ID =
      "66666666-6666-6666-6666-666666660802";
  public static final String PERSON_FOUND_EVENT_ID = "evt-s5-person-found-001";

  /** 실종자 발견 수신 정책: 사건 배정 전체 */
  public static final String PERSON_FOUND_RECIPIENT_POLICY = "ALL_INCIDENT_ASSIGNED";

  /** 실종자 발견 수신 대상 계정 */
  public static final List<String> PERSON_FOUND_RECIPIENT_ACCOUNT_IDS =
      List.of(
          "11111111-1111-1111-1111-111111110001",
          "11111111-1111-1111-1111-111111110002",
          "11111111-1111-1111-1111-111111110003",
          "11111111-1111-1111-1111-111111110004",
          "11111111-1111-1111-1111-111111110005",
          "11111111-1111-1111-1111-111111110006",
          "11111111-1111-1111-1111-111111110007",
          "11111111-1111-1111-1111-111111110008");

  /** 실종자 발견 수신 대상 폴리폰 */
  public static final List<String> PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS =
      List.of(
          "50000000-0000-0000-0000-000000000001",
          "00000000-0000-0000-0000-000000000101",
          "00000000-0000-0000-0000-000000000205",
          "00000000-0000-0000-0000-000000000207",
          "00000000-0000-0000-0000-000000000208");

  public static final List<String> PERSON_FOUND_RECIPIENT_POLICE_PHONE_CODES =
      List.of(
          "dev-precinct-car-01",
          "dev-precinct-phone-01",
          "dev-alpha-phone-01",
          "dev-support-car-01",
          "dev-support-phone-01");

  /** 실종자 발견 FCM 토큰 수신자 */
  public static final List<String> PERSON_FOUND_FCM_RECIPIENTS =
      List.of("fcm:dev-alpha-phone-01", "fcm:dev-support-car-01", "fcm:dev-support-phone-01");

  /** 실종자 발견 FCM payload */
  public static Map<String, Object> personFoundPayload() {
    return Map.ofEntries(
        Map.entry("type", "PERSON_FOUND"),
        Map.entry("id", PERSON_FOUND_NOTIFICATION_ID),
        Map.entry("markerId", PERSON_FOUND_MARKER_ID),
        Map.entry("incidentId", INCIDENT_ID),
        Map.entry("opId", OP_ID),
        Map.entry("policePhoneId", POLICE_PHONE_ID),
        Map.entry("status", "SNAPSHOT_CREATED"),
        Map.entry("version", 1),
        Map.entry("recipientPolicy", PERSON_FOUND_RECIPIENT_POLICY),
        Map.entry("recipientAccountIds", PERSON_FOUND_RECIPIENT_ACCOUNT_IDS),
        Map.entry("recipientPolicePhoneIds", PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS));
  }

  // ── SC-02: 지원 배정 알림 ────────────────────────────

  public static final String ASSIGNMENT_EVENT_ID = "evt-s1-assignment-support-assigned-001";

  /** 지원 배정 수신 정책: 신규 배정 지원 부대 폴리폰만 */
  public static final String ASSIGNMENT_RECIPIENT_POLICY = "NEWLY_ASSIGNED_SUPPORT_DEVICES";

  /** 지원 배정으로 변경된 계정 */
  public static final List<String> ASSIGNMENT_CHANGED_ACCOUNT_IDS =
      List.of(
          "11111111-1111-1111-1111-111111110006",
          "11111111-1111-1111-1111-111111110007",
          "11111111-1111-1111-1111-111111110008");

  /** 지원 배정 수신 대상 (지휘 계정 제외) */
  public static final List<String> ASSIGNMENT_RECIPIENT_ACCOUNT_IDS =
      List.of(
          "11111111-1111-1111-1111-111111110007",
          "11111111-1111-1111-1111-111111110008");

  /** 지원 배정 수신 대상 폴리폰 */
  public static final List<String> ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS =
      List.of(
          "00000000-0000-0000-0000-000000000207",
          "00000000-0000-0000-0000-000000000208");

  public static final List<String> ASSIGNMENT_RECIPIENT_POLICE_PHONE_CODES =
      List.of("dev-support-car-01", "dev-support-phone-01");

  /** 지원 배정 제외 폴리폰 (지휘 계정) */
  public static final List<String> ASSIGNMENT_EXCLUDED_POLICE_PHONE_IDS =
      List.of("00000000-0000-0000-0000-000000000206");

  public static final List<String> ASSIGNMENT_EXCLUDED_POLICE_PHONE_CODES =
      List.of("dev-support-cmd-phone-01");

  /** 지원 배정 FCM 토큰 수신자 */
  public static final List<String> ASSIGNMENT_FCM_RECIPIENTS =
      List.of("fcm:dev-support-car-01", "fcm:dev-support-phone-01");

  /** 지원 배정 FCM payload (PII 없음, marker_notification row 미생성) */
  public static Map<String, Object> assignmentPayload() {
    return Map.ofEntries(
        Map.entry("type", "INCIDENT_ASSIGNMENT_CHANGED"),
        Map.entry("id", INCIDENT_ID),
        Map.entry("incidentId", INCIDENT_ID),
        Map.entry("status", "ACTIVE"),
        Map.entry("version", 2),
        Map.entry("changedAccountIds", ASSIGNMENT_CHANGED_ACCOUNT_IDS),
        Map.entry("recipientPolicy", ASSIGNMENT_RECIPIENT_POLICY),
        Map.entry("recipientAccountIds", ASSIGNMENT_RECIPIENT_ACCOUNT_IDS),
        Map.entry("recipientPolicePhoneIds", ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS),
        Map.entry("pii", false));
  }
}
