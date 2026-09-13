import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

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
