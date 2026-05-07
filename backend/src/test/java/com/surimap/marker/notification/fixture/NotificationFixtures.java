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

  public static final String INCIDENT_ID = "inc-precinct-first-001";
  public static final String OP_ID = "op-precinct-001-op1";
  public static final String POLICE_PHONE_ID = "dev-precinct-phone-01";
  public static final String ACCOUNT_ID = "acct-precinct-team";

  // ── SC-08: 지원 요청 알림 ────────────────────────────

  public static final String SUPPORT_MARKER_ID = "mk-precinct-support-001";
  public static final String SUPPORT_NOTIFICATION_ID = "notif-precinct-support-001";
  public static final String SUPPORT_EVENT_ID = "evt-s5-support-request-001";

  /** 지원 요청 수신 정책: 지휘관 + 현장지휘관 */
  public static final String SUPPORT_RECIPIENT_POLICY = "COMMANDERS_AND_FIELD_COMMANDERS";

  /** 지원 요청 수신 대상 계정 */
  public static final List<String> SUPPORT_RECIPIENT_ACCOUNT_IDS =
      List.of("acct-cmd-alpha", "acct-support-cmd", "acct-support-car", "acct-support-team");

  /** 지원 요청 수신 대상 폴리폰 */
  public static final List<String> SUPPORT_RECIPIENT_POLICE_PHONE_IDS =
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

  public static final String PERSON_FOUND_MARKER_ID = "mk-precinct-person-found-001";
  public static final String PERSON_FOUND_NOTIFICATION_ID = "notif-precinct-person-found-001";
  public static final String PERSON_FOUND_EVENT_ID = "evt-s5-person-found-001";

  /** 실종자 발견 수신 정책: 사건 배정 전체 */
  public static final String PERSON_FOUND_RECIPIENT_POLICY = "ALL_INCIDENT_ASSIGNED";

  /** 실종자 발견 수신 대상 계정 */
  public static final List<String> PERSON_FOUND_RECIPIENT_ACCOUNT_IDS =
      List.of(
          "acct-precinct-cmd",
          "acct-precinct-car",
          "acct-precinct-team",
          "acct-cmd-alpha",
          "acct-team-alpha",
          "acct-support-cmd",
          "acct-support-car",
          "acct-support-team");

  /** 실종자 발견 수신 대상 폴리폰 */
  public static final List<String> PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS =
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
      List.of("acct-support-cmd", "acct-support-car", "acct-support-team");

  /** 지원 배정 수신 대상 (지휘 계정 제외) */
  public static final List<String> ASSIGNMENT_RECIPIENT_ACCOUNT_IDS =
      List.of("acct-support-car", "acct-support-team");

  /** 지원 배정 수신 대상 폴리폰 */
  public static final List<String> ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS =
      List.of("dev-support-car-01", "dev-support-phone-01");

  /** 지원 배정 제외 폴리폰 (지휘 계정) */
  public static final List<String> ASSIGNMENT_EXCLUDED_POLICE_PHONE_IDS =
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
