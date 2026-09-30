import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { createIncidentScopedFallbackBoard } from '../constants/mockSituationBoard';
import { mergeWithPreviousCriticalSlots } from '../../../board/model/incidentBoardMerge';
import { openIncidentBoardEventStream, type BoardEventEnvelope } from '../../../board/api/incidentBoardEventStream';
import { shouldSubscribeIncidentBoardEvents, useSituationBoardData } from './useSituationBoardData';

const { readBoard, invalidateQueries } = vi.hoisted(() => ({ readBoard: vi.fn(), invalidateQueries: vi.fn() }));
vi.mock('@tanstack/react-query', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@tanstack/react-query')>()),
  useQueryClient: () => ({ invalidateQueries }),
}));
vi.mock('../../../board/api/incidentBoardApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../board/api/incidentBoardApi')>()),
  useIncidentBoardQuery: () => ({ data: readBoard(), isError: false, isFetching: false, isLoading: false }),
}));
vi.mock('../../../board/api/incidentBoardEventStream', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../board/api/incidentBoardEventStream')>()),
  openIncidentBoardEventStream: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

test('SSE 계측을 켜면 중복 수신도 기록하되 같은 이벤트의 재조회는 반복하지 않는다', () => {
  // given: 계측 수집기를 연결한 상황판이다.
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  readBoard.mockReturnValue(boardResponse({}));
  const records: Array<{ stage: string; duplicate: boolean; eventId: string }> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent) records.push(event.detail);
  };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    renderHook(() => useSituationBoardData('incident-001', []));
    const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
    const event = { ...boardEvent(), payload: { version: 7, coordinates: [127, 35] } };

    // when: 같은 SSE 이벤트를 두 번 수신한다.
    subscription.onEvent(event, { lastEventId: '901', eventType: event.type });
    subscription.onEvent(event, { lastEventId: '901', eventType: event.type });

    // then: 수신 2회와 실제 재조회 요청 1회를 구분하고 payload를 기록하지 않는다.
    expect(records.map((record) => record.duplicate)).toEqual([false, true]);
    expect(records.every((record) => record.stage === 'sse_received' && record.eventId === event.eventId)).toBe(true);
    expect(invalidateQueries).toHaveBeenCalledTimes(1);
    expect(records[0]).toMatchObject({ sourceEntityType: 'marker', sourceEntityId: 'marker-001', sourceVersion: 7 });
    expect(JSON.stringify(records)).not.toContain('payload');
    expect(JSON.stringify(records)).not.toContain('coordinates');
  } finally {
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    window.removeEventListener('suri-map:board-measurement', collect);
  }
});

test('다른 사건으로 이동하면, 이전 구독을 닫고 수신 순번 없이 연결한다', () => {
  // given: 첫 사건에서 순번 901을 수신한 상황판이다.
  const openStream = vi.mocked(openIncidentBoardEventStream);
  const close = vi.fn();
  openStream.mockImplementation(() => ({ closed: new Promise<void>(() => {}), close }));
  readBoard.mockReturnValue(boardResponse({}));
  const { rerender } = renderHook(({ incidentId }) => useSituationBoardData(incidentId, []), {
    initialProps: { incidentId: 'incident-001' },
  });
  openStream.mock.calls[0][0].onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });

  // when: 이미 조회된 다른 사건의 상황판으로 이동한다.
  readBoard.mockReturnValue({ ...boardResponse({}), incidentId: 'incident-002' });
  rerender({ incidentId: 'incident-002' });

  // then: 이전 사건의 순번을 새 사건에 보내지 않는다.
  expect(close).toHaveBeenCalledTimes(1);
  expect(openStream.mock.calls[1][0]).toMatchObject({ incidentId: 'incident-002', lastEventId: null });
});

test('상황판 재연결 대기 중 화면을 떠나면, 타이머와 구독을 정리한다', async () => {
  // given: 첫 연결이 정상 종료돼 재연결을 기다린다.
  vi.useFakeTimers();
  const close = vi.fn();
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: Promise.resolve(), close });
  readBoard.mockReturnValue(boardResponse({}));
  const { unmount } = renderHook(() => useSituationBoardData('incident-001', []));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0);
  });
  expect(vi.getTimerCount()).toBe(1);

  // when: 상황판에서 나간다.
  unmount();

  // then: 예약된 재연결도 남기지 않는다.
  expect(close).toHaveBeenCalledTimes(1);
  expect(vi.getTimerCount()).toBe(0);
});

test.each(['event_stream_error', 'gone_refetch_required'] as const)(
  '%s로 연결이 끊기면, 재조회하고 재전송 불가일 때만 수신 순번을 초기화한다',
  async (reason) => {
    // given: 첫 연결에서 이벤트 901을 수신했다.
    vi.useFakeTimers();
    const openStream = vi.mocked(openIncidentBoardEventStream);
    openStream
      .mockImplementation(() => ({ closed: new Promise<void>(() => {}), close: vi.fn() }))
      .mockReturnValueOnce({ closed: Promise.resolve(), close: vi.fn() });
    readBoard.mockReturnValue(boardResponse({}));
    renderHook(() => useSituationBoardData('incident-001', []));
    const connection = openStream.mock.calls[0][0];
    connection.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });

    // when: 스트림 오류 또는 재전송 불가를 통보받고 재연결한다.
    connection.onRefetchRequired?.({ reason });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
    });

    // then: 현재 상태를 다시 조회하고, 네트워크 오류이면 이어서 수신한다.
    expect(invalidateQueries).toHaveBeenCalledTimes(2);
    expect(openStream.mock.calls[1][0].lastEventId).toBe(reason === 'gone_refetch_required' ? null : '901');
    // 재연결에서 같은 이벤트가 재전송돼도 중복 반영하지 않는다.
    openStream.mock.calls[1][0].onEvent(boardEvent(), { lastEventId: '902', eventType: 'PERSON_FOUND' });
    expect(invalidateQueries).toHaveBeenCalledTimes(2);
  },
);

test.each([401, 403])('상황판 SSE가 HTTP %s로 거부되면, 재연결과 불필요한 재조회를 멈춘다', async (status) => {
  // given: 실제 SSE 전송 코드에 인증·권한 오류 응답을 전달한다.
  vi.useFakeTimers();
  const actual = await vi.importActual<typeof import('../../../board/api/incidentBoardEventStream')>(
    '../../../board/api/incidentBoardEventStream',
  );
  vi.mocked(openIncidentBoardEventStream).mockImplementation(actual.openIncidentBoardEventStream);
  const fetchStream = vi.fn(async () => new Response('{}', { status }));
  vi.stubGlobal('fetch', fetchStream);
  readBoard.mockReturnValue(boardResponse({}));

  // when: 거부된 연결 이후 9초가 지난다.
  renderHook(() => useSituationBoardData('incident-001', []));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(9_000);
  });

  // then: 권한 없는 요청을 반복하지 않는다.
  expect(fetchStream).toHaveBeenCalledTimes(1);
  expect(invalidateQueries).not.toHaveBeenCalled();
});

test('재전송 불가 후 연결이 다시 열리면, 연결 대기 중 변경된 상태도 재조회한다', async () => {
  // given: 재전송 불가로 전체 상태를 조회한 뒤 재연결을 기다린다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentBoardEventStream);
  openStream
    .mockImplementation(() => ({ closed: new Promise<void>(() => {}), close: vi.fn() }))
    .mockReturnValueOnce({ closed: Promise.resolve(), close: vi.fn() });
  readBoard.mockReturnValue(boardResponse({}));
  renderHook(() => useSituationBoardData('incident-001', []));
  openStream.mock.calls[0][0].onRefetchRequired?.({ reason: 'gone_refetch_required' });
  expect(invalidateQueries).toHaveBeenCalledTimes(1);

  // when: 재연결이 완료된다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3_000);
  });
  openStream.mock.calls[1][0].onOpen?.();

  // then: 연결이 없던 동안의 변경도 현재 상태로 보완한다.
  expect(invalidateQueries).toHaveBeenCalledTimes(2);
});

test.each(['INCIDENT_CLOSED', 'INCIDENT_PURGED'])(
  '상황판이 %s를 수신하면, 마지막 상태를 조회하고 구독을 중단한다',
  async (eventType) => {
    // given: 연결은 종료되지만 상황판 재조회 응답은 아직 도착하지 않았다.
    vi.useFakeTimers();
    const close = vi.fn();
    const openStream = vi.mocked(openIncidentBoardEventStream);
    openStream.mockReturnValue({ closed: Promise.resolve(), close });
    readBoard.mockReturnValue(boardResponse({}));
    renderHook(() => useSituationBoardData('incident-001', []));

    // when: 종료 이벤트를 받고 재연결 대기 시간이 지난다.
    openStream.mock.calls[0][0].onEvent({ ...boardEvent(), type: eventType }, { lastEventId: '901', eventType });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(9_000);
    });

    // then: 화면 종료 상태를 갱신하되 연결을 반복하지 않는다.
    expect(invalidateQueries).toHaveBeenCalledTimes(1);
    expect(close).toHaveBeenCalledTimes(1);
    expect(openStream).toHaveBeenCalledTimes(1);
  },
);

function boardEvent(): BoardEventEnvelope {
  return {
    eventId: '40000000-0000-4000-8000-000000000811',
    incidentId: 'incident-001',
    type: 'PERSON_FOUND',
    payloadFormatVersion: 1,
    sourceEntityType: 'marker',
    sourceEntityId: 'marker-001',
    occurredAt: '2026-09-14T00:00:00Z',
    payload: {},
  };
}

describe('useSituationBoardData', () => {
  test('fallback board does not include placeholder markers', () => {
    const board = createIncidentScopedFallbackBoard('incident-001');

    expect(board.recentMarkers).toEqual([]);
  });

  test('keeps previous critical slots when a refetch response omits them', () => {
    const previous = boardResponse({
      marker: [{ id: 'marker-001', markerType: 'CLUE' }],
      path: [{ id: 'path-001', pathType: 'FOOT' }],
    });
    const current = boardResponse({
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.marker).toEqual(previous.slots.marker);
    expect(merged?.slots.path).toEqual(previous.slots.path);
    expect(merged?.slots.area).toEqual(current.slots.area);
  });

  test('keeps previous critical slots when a refetch response returns empty collections', () => {
    const previous = boardResponse({
      marker: [{ id: 'marker-001', markerType: 'CLUE' }],
      path: [{ id: 'path-001', pathType: 'FOOT' }],
    });
    const current = boardResponse({
      marker: [],
      path: {},
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.marker).toEqual(previous.slots.marker);
    expect(merged?.slots.path).toEqual(previous.slots.path);
  });

  test('keeps package badge and terminal slots when a refetch response omits them', () => {
    const previous = boardResponse({
      package_badge: [{ id: 'package-001', status: 'READY', version: 1, sequence: 1 }],
      incident_terminal: [{ id: 'terminal-001', status: 'OPEN', version: 1, sequence: 1 }],
    });
    const current = boardResponse({
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.package_badge).toEqual(previous.slots.package_badge);
    expect(merged?.slots.incident_terminal).toEqual(previous.slots.incident_terminal);
  });

  test('does not keep previous critical slots for a different incident', () => {
    const previous = boardResponse({ marker: [{ id: 'marker-001' }] });
    const current = {
      ...boardResponse({}),
      incidentId: 'incident-002',
    };

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged).toBe(current);
    expect(merged?.slots.marker).toBeUndefined();
  });

  test('subscribes board events while incident terminal is open', () => {
    const board = boardResponse({
      incident_terminal: [
        {
          incidentId: 'incident-001',
          terminalStatus: 'OPEN',
          closedStatus: 'not_closed',
          writeDisabledReason: 'none',
          localPurgeState: 'not_started',
        },
      ],
    });

    expect(shouldSubscribeIncidentBoardEvents(board)).toBe(true);
  });

  test('stops board event subscription after incident terminal is closed', () => {
    const board = boardResponse({
      incident_terminal: [
        {
          incidentId: 'incident-001',
          terminalStatus: 'CLOSED',
          closedStatus: 'closed',
          closedAt: '2026-05-20T06:16:39.613400Z',
          writeDisabledReason: 'incident_closed',
          localPurgeState: 'not_started',
        },
      ],
    });

    expect(shouldSubscribeIncidentBoardEvents(board)).toBe(false);
  });
});

function boardResponse(slots: Record<string, unknown>): SituationBoardResponseDto {
  return {
    incidentId: 'incident-001',
    boardResponseVersion: 1,
    serverTs: '2026-05-01T00:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    slots,
    sourceVersions: {},
    geometryHash: null,
    sourceHashes: {},
    slotSources: {},
  };
}
