package com.surimap.marker.notification.port;

import java.util.List;
import java.util.Map;

/**
 * FCM 발송 포트.
 *
 * <p>FCM data message 발송 경계를 추상화한다. Phase -1에서는 {@code MockFcmDispatcher}만 존재하며, 외부 FCM SDK나 인프라 없이
 * payload와 recipient를 기록한다.
 *
 * <p>S4 EventFanout이 이 포트를 호출하여 알림을 전송하고, S5는 결과만 반환한다. retry/failure orchestration은 S4 소관.
 */
public interface FcmDispatcherPort {

  /**
   * FCM 메시지를 전송한다.
   *
   * @param recipients mock FCM recipient 목록 (예: "fcm:dev-alpha-phone-01")
   * @param payload FCM data payload (type, incidentId, status, version, policePhoneId 등)
   * @param eventId 이벤트 추적용 ID
   * @return 전송 결과
   */
  DispatchResult send(List<String> recipients, Map<String, Object> payload, String eventId);

  /** FCM 전송 결과. */
  record DispatchResult(
      String eventId, int successCount, int failureCount, List<String> failedRecipients) {
    public static DispatchResult allSuccess(String eventId, int count) {
      return new DispatchResult(eventId, count, 0, List.of());
    }

    public static DispatchResult allFailed(String eventId, List<String> recipients) {
      return new DispatchResult(eventId, 0, recipients.size(), recipients);
    }

    public boolean isFullySuccessful() {
      return failureCount == 0;
    }
  }
}
