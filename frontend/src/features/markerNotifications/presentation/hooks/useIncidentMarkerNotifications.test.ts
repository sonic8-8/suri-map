import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import { ApiHttpError } from '../../../../shared/api/client';
import { openIncidentEventStream, type EventStreamMessage } from '../../../../shared/api/eventStream';
import { useIncidentMarkerNotifications } from './useIncidentMarkerNotifications';

vi.mock('../../../../shared/api/eventStream', () => ({
  openIncidentEventStream: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.resetAllMocks();
});

test('서버가 스트림을 정상 종료하면, 3초 뒤 다시 구독한다', async () => {
  // given: 첫 연결은 오류 없이 끝나고 다음 연결은 열린 상태로 유지된다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentEventStream);
  openStream.mockImplementation(() => new Promise<void>(() => {})).mockResolvedValueOnce();
  const onNotification = vi.fn();
  renderHook(() =>
    useIncidentMarkerNotifications({
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      enabled: true,
      onNotification,
    }),
  );

  // when: 기존 재연결 대기 시간인 3초가 지난다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3_000);
  });

  // then: 구독을 다시 시작한다.
  expect(openStream).toHaveBeenCalledTimes(2);
});

test.each([401, 403])('서버가 HTTP %s로 구독을 거부하면, 재연결을 중단한다', async (status) => {
  // given: 인증 또는 사건 접근 권한이 없다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentEventStream);
  openStream.mockRejectedValue(new ApiHttpError(status, 'access_denied', null));
  const onNotification = vi.fn();
  renderHook(() =>
    useIncidentMarkerNotifications({
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      enabled: true,
      onNotification,
    }),
  );

  // when: 재연결 대기 시간이 여러 번 지난다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(9_000);
  });

  // then: 같은 권한으로 구독을 반복하지 않는다.
  expect(openStream).toHaveBeenCalledTimes(1);
  expect(vi.getTimerCount()).toBe(0);
});

test.each(['INCIDENT_CLOSED', 'INCIDENT_PURGED'])(
  '%s를 수신하면, 연결을 닫고 후속 알림과 재연결을 막는다',
  async (eventType) => {
    // given: 구독 중인 사건에 종료 이벤트가 도착한다.
    vi.useFakeTimers();
    const openStream = vi.mocked(openIncidentEventStream);
    openStream.mockResolvedValue();
    const onNotification = vi.fn();
    renderHook(() =>
      useIncidentMarkerNotifications({
        incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
        enabled: true,
        onNotification,
      }),
    );
    const connection = openStream.mock.calls[0][0];

    // when: 종료 직후 같은 응답에 뒤따르는 알림 프레임도 전달된다.
    connection.onMessage({ id: '901', event: eventType, data: { type: eventType } });
    connection.onMessage({ id: '902', event: 'PERSON_FOUND', data: { type: 'PERSON_FOUND' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(9_000);
    });

    // then: 연결과 알림 처리가 모두 중단된다.
    expect(connection.signal.aborted).toBe(true);
    expect(onNotification).not.toHaveBeenCalled();
    expect(openStream).toHaveBeenCalledTimes(1);
  },
);

test('재연결 대기 중 화면을 떠나면, 예약된 타이머를 제거한다', async () => {
  // given: 연결 실패로 재연결을 기다리는 화면이다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentEventStream);
  openStream.mockRejectedValue(new Error('connection_lost'));
  const onNotification = vi.fn();
  const { unmount } = renderHook(() =>
    useIncidentMarkerNotifications({
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      enabled: true,
      onNotification,
    }),
  );
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0);
  });
  expect(vi.getTimerCount()).toBe(1);

  // when: 화면에서 알림 구독을 해제한다.
  unmount();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(9_000);
  });

  // then: 예약 작업과 추가 연결이 남지 않는다.
  expect(vi.getTimerCount()).toBe(0);
  expect(openStream).toHaveBeenCalledTimes(1);
});

test('재전송할 수 없는 순번이면, 순번만 초기화하고 이미 표시한 알림은 유지한다', async () => {
  // given: 표시한 알림 뒤로 재연결을 요청했지만 서버가 재전송 불가를 응답한다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentEventStream);
  openStream
    .mockImplementation(() => new Promise<void>(() => {}))
    .mockRejectedValueOnce(new ApiHttpError(409, 'gone_refetch_required', null));
  const onNotification = vi.fn();
  renderHook(() =>
    useIncidentMarkerNotifications({
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      enabled: true,
      onNotification,
    }),
  );
  const message: EventStreamMessage = {
    id: '901',
    event: 'PERSON_FOUND',
    data: { eventId: 'evt-001', type: 'PERSON_FOUND' },
  };
  openStream.mock.calls[0][0].onMessage(message);

  // when: 재연결 뒤 같은 알림이 도착한다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3_000);
  });
  const reconnected = openStream.mock.calls[1][0];
  reconnected.onMessage({ ...message, id: '902' });

  // then: 유효하지 않은 순번을 반복 전송하거나 알림을 다시 표시하지 않는다.
  expect(reconnected.lastEventId).toBeNull();
  expect(onNotification).toHaveBeenCalledTimes(1);
});

test.each(['aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '10000000-0000-4000-8000-000000000001'])(
  '사건 ID가 %s이면, 알림 구독을 시작하고 발견 마커를 전달한다',
  (incidentId) => {
    // given: 서버에서 사용하는 사건 ID로 알림 수신을 활성화한다.
    const openStream = vi.mocked(openIncidentEventStream);
    openStream.mockImplementation(() => new Promise<void>(() => {}));
    const onNotification = vi.fn();

    // when: 화면에서 실제 마커 알림 훅을 실행한다.
    renderHook(() => useIncidentMarkerNotifications({ incidentId, enabled: true, onNotification }));

    // then: 구독을 시작하고, 연결에서 받은 발견 이벤트를 알림으로 전달한다.
    expect(openStream).toHaveBeenCalledTimes(1);
    const connection = openStream.mock.calls[0][0];
    expect(connection.incidentId).toBe(incidentId);
    connection.onMessage({
      id: '901',
      event: 'PERSON_FOUND',
      data: {
        incidentId,
        eventId: '40000000-0000-4000-8000-000000000811',
        type: 'PERSON_FOUND',
        payload: { clientTs: '2026-04-28T00:05:00Z' },
      },
    });
    expect(onNotification).toHaveBeenCalledWith(
      expect.objectContaining({ title: '발견 마커 수신', markerRecordedAtLabel: '09:05' }),
    );
  },
);

test.each([
  { incidentId: '', enabled: true },
  { incidentId: 'incident-001', enabled: true },
  { incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', enabled: false },
])(
  '사건 ID($incidentId)가 없거나 형식이 잘못됐거나 알림이 비활성화되면, 구독하지 않는다',
  ({ incidentId, enabled }) => {
    // given: 구독할 수 없는 사건 ID 또는 비활성화된 화면이다.
    const onNotification = vi.fn();

    // when: 마커 알림 훅을 실행한다.
    renderHook(() => useIncidentMarkerNotifications({ incidentId, enabled, onNotification }));

    // then: 서버에 구독 요청을 보내거나 알림을 전달하지 않는다.
    expect(openIncidentEventStream).not.toHaveBeenCalled();
    expect(onNotification).not.toHaveBeenCalled();
  },
);

test('재렌더링·재연결에서는 수신 기록을 유지하고 구독을 새로 시작하면 초기화한다', async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-04-28T00:11:00Z'));
  const openStream = vi.mocked(openIncidentEventStream);
  openStream.mockImplementation(() => new Promise<void>(() => {})).mockRejectedValueOnce(new Error('connection_lost'));
  const incidentId = '10000000-0000-4000-8000-000000000001';
  const notificationMessage: EventStreamMessage = {
    id: '901',
    event: 'SUPPORT_REQUEST_CREATED',
    data: {
      incidentId,
      eventId: '40000000-0000-4000-8000-000000000811',
      type: 'SUPPORT_REQUEST_CREATED',
      occurredAt: '2026-04-28T00:10:00Z',
      payload: {
        id: '50000000-0000-4000-8000-000000000801',
        status: 'REQUESTED',
        version: 1,
        policePhoneId: '00000000-0000-0000-0000-000000000101',
        clientTs: '2026-04-28T00:05:00Z',
      },
    },
  };
  const onNotification = vi.fn();
  const { rerender, unmount } = renderHook(
    ({ enabled }) => useIncidentMarkerNotifications({ incidentId, enabled, onNotification }),
    { initialProps: { enabled: true } },
  );

  const firstConnection = openStream.mock.calls[0][0];
  firstConnection.onMessage(notificationMessage);
  expect(onNotification).toHaveBeenCalledWith(
    expect.objectContaining({
      id: notificationMessage.data.eventId,
      markerType: '지원 요청',
      reporter: '수완지구대 현장 단말',
      markerRecordedAtLabel: '09:05',
    }),
  );

  rerender({ enabled: true });
  expect(openStream).toHaveBeenCalledTimes(1);
  firstConnection.onMessage(notificationMessage);
  expect(onNotification).toHaveBeenCalledTimes(1);

  await act(async () => {
    await vi.advanceTimersByTimeAsync(3_000);
  });
  expect(openStream).toHaveBeenCalledTimes(2);
  const reconnectedStream = openStream.mock.calls[1][0];
  expect(reconnectedStream.lastEventId).toBe('901');
  reconnectedStream.onMessage({ ...notificationMessage, id: '902' });
  expect(onNotification).toHaveBeenCalledTimes(1);

  rerender({ enabled: false });
  expect(reconnectedStream.signal.aborted).toBe(true);
  rerender({ enabled: true });
  expect(openStream).toHaveBeenCalledTimes(3);
  const restartedSubscription = openStream.mock.calls[2][0];
  expect(restartedSubscription.lastEventId).toBeNull();
  restartedSubscription.onMessage(notificationMessage);
  expect(onNotification).toHaveBeenCalledTimes(2);
  expect(onNotification).toHaveBeenLastCalledWith(expect.objectContaining({ markerRecordedAtLabel: '09:05' }));

  unmount();
  expect(restartedSubscription.signal.aborted).toBe(true);
});

test.each([
  { eventType: 'SUPPORT_REQUEST_CREATED', clientTs: '2026-04-28T00:05:00Z', expected: '09:05' },
  { eventType: 'PERSON_FOUND', clientTs: '2026-04-28T00:05:00Z', expected: '09:05' },
  { eventType: 'PERSON_FOUND', clientTs: undefined, expected: '확인 불가' },
  { eventType: 'PERSON_FOUND', clientTs: 'invalid-timestamp', expected: '확인 불가' },
])(
  '$eventType 알림은 업무폰 기록 시각($clientTs)을 표시하고 없거나 잘못된 값은 확인 불가로 표시한다',
  ({ eventType, clientTs, expected }) => {
    const openStream = vi.mocked(openIncidentEventStream);
    openStream.mockImplementation(() => new Promise<void>(() => {}));
    const incidentId = '10000000-0000-4000-8000-000000000001';
    const onNotification = vi.fn();
    renderHook(() => useIncidentMarkerNotifications({ incidentId, enabled: true, onNotification }));

    openStream.mock.calls[0][0].onMessage({
      id: '901',
      event: eventType,
      data: {
        incidentId,
        eventId: '40000000-0000-4000-8000-000000000811',
        type: eventType,
        occurredAt: '2026-04-28T00:10:00Z',
        serverTs: '2026-04-28T00:10:00Z',
        payload: { clientTs },
      },
    });

    expect(onNotification).toHaveBeenCalledWith(expect.objectContaining({ markerRecordedAtLabel: expected }));
  },
);
