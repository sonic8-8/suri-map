package com.surimap.marker.notification.adapter;

import com.surimap.marker.notification.port.FcmDispatcherPort;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FCM 발송 모의체 (Mock FCM Dispatcher).
 *
 * <p>외부 FCM SDK 호출 없이 메모리에 발송 기록을 남긴다. 테스트에서 "누구에게, 어떤 payload로, 어떤 eventId로 보냈는지" 검증할 수 있다.
 *
 * <p>하네스 시나리오(SC-02, SC-08)에서 mock FCM dispatcher 미수신을 실패로 판정하는 failure injection도 지원한다.
 */
public class MockFcmDispatcher implements FcmDispatcherPort {

  /** 발송된 모든 기록 */
  private final List<CapturedDispatch> dispatches = new CopyOnWriteArrayList<>();

  /** 실패를 주입할 eventId 집합 (failure injection용) */
  private final Set<String> failureInjections = new HashSet<>();

  // ── send ───────────────────────────────────────────────

  @Override
  public DispatchResult send(List<String> recipients, Map<String, Object> payload, String eventId) {
    Objects.requireNonNull(recipients, "recipients must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");

    if (recipients.isEmpty()) {
      throw new IllegalArgumentException("recipients must not be empty");
    }

    // failure injection 확인
    if (failureInjections.contains(eventId)) {
      return DispatchResult.allFailed(eventId, recipients);
    }

    // 성공 기록 저장
    CapturedDispatch capture =
        new CapturedDispatch(eventId, List.copyOf(recipients), Map.copyOf(payload));
    dispatches.add(capture);

    return DispatchResult.allSuccess(eventId, recipients.size());
  }

  // ── 조회 API (테스트 검증용) ──────────────────────────────

  /** 전체 발송 기록 */
  public List<CapturedDispatch> getAllDispatches() {
    return List.copyOf(dispatches);
  }

  /** 특정 eventId의 발송 기록 */
  public Optional<CapturedDispatch> findByEventId(String eventId) {
    return dispatches.stream().filter(d -> d.eventId().equals(eventId)).findFirst();
  }

  /** 특정 recipient가 수신한 모든 발송 기록 */
  public List<CapturedDispatch> findByRecipient(String recipient) {
    return dispatches.stream().filter(d -> d.recipients().contains(recipient)).toList();
  }

  /** 특정 이벤트 타입의 발송 기록 */
  public List<CapturedDispatch> findByEventType(String eventType) {
    return dispatches.stream().filter(d -> eventType.equals(d.payload().get("type"))).toList();
  }

  /** 발송 기록이 없는지 (failure injection 검증용) */
  public boolean hasNoDispatchFor(String eventId) {
    return dispatches.stream().noneMatch(d -> d.eventId().equals(eventId));
  }

  /** 총 발송 횟수 */
  public int getDispatchCount() {
    return dispatches.size();
  }

  // ── failure injection ──────────────────────────────────

  /** 특정 eventId에 대해 발송 실패를 주입한다 */
  public void injectFailureFor(String eventId) {
    failureInjections.add(eventId);
  }

  /** failure injection 해제 */
  public void clearFailureInjection(String eventId) {
    failureInjections.remove(eventId);
  }

  // ── 리셋 ───────────────────────────────────────────────

  /** 모든 발송 기록과 failure injection을 초기화한다 */
  public void reset() {
    dispatches.clear();
    failureInjections.clear();
  }

  // ── 캡처 레코드 ────────────────────────────────────────

  /**
   * 하나의 FCM 발송 기록.
   *
   * @param eventId 이벤트 추적 ID
   * @param recipients 수신 FCM 토큰 목록
   * @param payload FCM data payload
   */
  public record CapturedDispatch(
      String eventId, List<String> recipients, Map<String, Object> payload) {
    /** payload에서 특정 필드 추출 */
    public Object getPayloadField(String key) {
      return payload.get(key);
    }

    /** 특정 recipient가 수신 대상에 포함되는지 */
    public boolean wasDeliveredTo(String recipient) {
      return recipients.contains(recipient);
    }

    /** FCM payload에 캡처된 recipient account id 목록 */
    public List<String> recipientAccountIds() {
      return stringListPayload("recipientAccountIds");
    }

    /** FCM payload에 캡처된 recipient policePhone id 목록 */
    public List<String> recipientPolicePhoneIds() {
      return stringListPayload("recipientPolicePhoneIds");
    }

    private List<String> stringListPayload(String key) {
      Object value = payload.get(key);
      if (!(value instanceof List<?> values)) {
        return List.of();
      }
      return values.stream().map(String.class::cast).toList();
    }
  }
}
