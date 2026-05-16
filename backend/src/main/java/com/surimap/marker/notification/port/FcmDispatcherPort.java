package com.surimap.marker.notification.port;

import java.util.List;
import java.util.Map;

/**
 * FCM 발송 포트.
 *
 * <p>FCM data message 발송 경계를 추상화한다. 기본 프로파일은 {@code MockFcmDispatcher}로 payload와 recipient를 기록하고,
 * 운영 설정에서 Firebase Admin adapter로 실제 FCM 발송을 수행한다.
 *
 * <p>S4 EventFanout이 이 포트를 호출하여 알림을 전송하고, S5는 결과만 반환한다. retry/failure orchestration은 S4 소관.
 */
public interface FcmDispatcherPort {

  /**
   * FCM 메시지를 전송한다.
   *
   * @param recipients FCM token 목록 또는 mock recipient 목록
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
