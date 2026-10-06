import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import { ApiHttpError } from '../../../../shared/api/client';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { createIncidentScopedFallbackBoard } from '../constants/mockSituationBoard';
import { mergeWithPreviousCriticalSlots } from '../../../board/model/incidentBoardMerge';
import { openIncidentBoardEventStream, type BoardEventEnvelope } from '../../../board/api/incidentBoardEventStream';
import { shouldSubscribeIncidentBoardEvents, useSituationBoardData } from './useSituationBoardData';
import { incidentBoardQueryKeys, boardSlotsWithoutPaths } from '../../../board/api/incidentBoardApi';
import { refreshSituationBoards } from '../../../../app/board/refreshSituationBoards';

// 이 파일은 기존 board/SSE 갱신을 검증한다. 경로 페이지 실행은 별도 실제 QueryClient 검사로 다룬다.
vi.mock('../../../path/api/searchPathPagesApi', () => ({
  useSearchPathPages: () => ({ data: { paths: [], hasMoreSegments: false }, isError: false }),
  refreshSearchPathPages: vi.fn(),
  restrictSearchPathPages: vi.fn(),
}));

const { fetchBoard } = vi.hoisted(() => {
  const fetchBoard = vi.fn<typeof fetch>();
  vi.stubGlobal('fetch', fetchBoard);
  return { fetchBoard };
});
vi.mock('../../../board/api/incidentBoardEventStream', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../board/api/incidentBoardEventStream')>()),
  openIncidentBoardEventStream: vi.fn(),
}));

let queryClient: QueryClient;

beforeEach(() => {
  queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, gcTime: Infinity, retry: false } } });
  vi.spyOn(queryClient, 'invalidateQueries');
  queryClient.setQueryData(incidentBoardQueryKeys.detail({ incidentId: 'incident-001', includeSlots: boardSlotsWithoutPaths }), boardResponse({}));
  fetchBoard.mockImplementation(() => new Promise<Response>(() => {}));
});

function renderBoardHook<Result, Props>(callback: (props: Props) => Result, options?: { initialProps: Props }) {
  return renderHook(callback, {
    ...options,
    wrapper: ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children),
  });
}

afterEach(() => {
  cleanup();
  queryClient.clear();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

test('조회 중 여러 변경을 받으면, 현재 요청을 유지하고 성공 후 한 번 더 조회한다', async () => {
  // given: 실제 QueryClient와 상황판을 연결하고 HTTP 응답만 보류한다.
  const responses: Array<(response: Response) => void> = [];
  fetchBoard.mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)));
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const { result, rerender } = renderBoardHook(({ refreshVersion }) => useSituationBoardData('incident-001', [], refreshVersion), {
    initialProps: { refreshVersion: 0 },
  });
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];

  // when: 연결 복구·이벤트 48건·구역 저장이 첫 재조회가 끝나기 전에 겹친다.
  act(() => {
    subscription.onOpen?.();
    for (let index = 0; index < 48; index += 1) {
      subscription.onEvent({ ...boardEvent(), eventId: `event-${index}` }, { lastEventId: `${index + 1}`, eventType: 'PERSON_FOUND' });
    }
  });
  rerender({ refreshVersion: 1 });

  // then: HTTP 요청은 하나만 진행하고, 완료 뒤 후속 조회로 최신 버전을 반영한다.
  expect(fetchBoard).toHaveBeenCalledTimes(1);
  await act(async () => responses[0](Response.json({ ...boardResponse({}), boardResponseVersion: 2 })));
  await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(2));
  await act(async () => responses[1](Response.json({ ...boardResponse({}), boardResponseVersion: 3 })));
  await waitFor(() => expect(result.current.apiBoard?.boardResponseVersion).toBe(3));
  expect(fetchBoard).toHaveBeenCalledTimes(2);
});

test('조회 중 화면을 떠나면, HTTP 요청을 취소하고 대기 중 변경을 다시 조회하지 않는다', async () => {
  // given: 변경 알림을 받아 재조회 중인 화면이다.
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const { unmount } = renderBoardHook(() => useSituationBoardData('incident-001', []));
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
  act(() => {
    subscription.onOpen?.();
    subscription.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });
  });
  const signal = fetchBoard.mock.calls[0][1]?.signal;

  // when: 같은 조회를 쓰는 마지막 화면을 닫는다.
  await act(async () => unmount());

  // then: 브라우저 요청도 취소하며 후속 요청을 남기지 않는다.
  expect(signal?.aborted).toBe(true);
  expect(fetchBoard).toHaveBeenCalledTimes(1);
});

test('같은 조회를 쓰는 화면이 남아 있으면, 진행 중 요청과 후속 갱신을 유지한다', async () => {
  // given: 두 화면이 같은 캐시를 사용하고 일반 재조회가 이미 진행 중이다.
  const responses: Array<(response: Response) => void> = [];
  fetchBoard.mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)));
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const first = renderBoardHook(() => useSituationBoardData('incident-001', []));
  const second = renderBoardHook(() => useSituationBoardData('incident-001', []));
  void queryClient.refetchQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId: 'incident-001', includeSlots: boardSlotsWithoutPaths }), exact: true });
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
  act(() => subscription.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' }));
  const signal = fetchBoard.mock.calls[0][1]?.signal;

  // when: 한 화면만 나간 뒤 기존 요청을 완료한다.
  first.unmount();
  expect(signal?.aborted).toBe(false);
  expect(fetchBoard).toHaveBeenCalledTimes(1);
  await act(async () => responses[0](Response.json({ ...boardResponse({}), boardResponseVersion: 2 })));

  // then: 남은 화면이 같은 요청을 이어받고 추가 변경도 후속 조회로 반영한다.
  await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(2));
  await act(async () => responses[1](Response.json({ ...boardResponse({}), boardResponseVersion: 3 })));
  await waitFor(() => expect(second.result.current.apiBoard?.boardResponseVersion).toBe(3));
  expect(fetchBoard).toHaveBeenCalledTimes(2);
});

test('후속 조회 중 새 변경이 와도, 이미 반영한 저장 작업의 완료를 미루지 않는다', async () => {
  // given: SSE 재조회 중 저장 작업도 상황판 갱신을 기다린다.
  const responses: Array<(response: Response) => void> = [];
  fetchBoard.mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)));
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const { result } = renderBoardHook(() => useSituationBoardData('incident-001', []));
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
  act(() => subscription.onOpen?.());
  let saveRefreshCompleted = false;
  void refreshSituationBoards(queryClient, { incidentId: 'incident-001' }).then(() => { saveRefreshCompleted = true; });
  await act(async () => responses[0](Response.json({ ...boardResponse({}), boardResponseVersion: 2 })));
  await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(2));
  expect(saveRefreshCompleted).toBe(false);

  // when: 저장 이후의 후속 조회 중에 또 다른 변경이 도착한다.
  act(() => subscription.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' }));
  expect(fetchBoard).toHaveBeenCalledTimes(2);
  await act(async () => responses[1](Response.json({ ...boardResponse({}), boardResponseVersion: 3 })));

  // then: 저장 결과는 완료하고, 새 변경만 세 번째 조회로 반영한다.
  await waitFor(() => expect(saveRefreshCompleted).toBe(true));
  expect(fetchBoard).toHaveBeenCalledTimes(3);
  await act(async () => responses[2](Response.json({ ...boardResponse({}), boardResponseVersion: 4 })));
  await waitFor(() => expect(result.current.apiBoard?.boardResponseVersion).toBe(4));
  expect(fetchBoard).toHaveBeenCalledTimes(3);
});

test.each([401, 403])('재조회 대기 중 SSE가 HTTP %s로 거부되면, 진행 중 요청과 후속 조회를 취소한다', async (status) => {
  // given: 현재 요청 뒤에 변경 반영용 후속 조회가 대기한다.
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  renderBoardHook(() => useSituationBoardData('incident-001', []));
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
  act(() => {
    subscription.onOpen?.();
    subscription.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });
  });
  const signal = fetchBoard.mock.calls[0][1]?.signal;

  // when: SSE 인증·권한 검사가 거부된다.
  await act(async () => subscription.onError?.(new ApiHttpError(status, 'forbidden', {})));

  // then: 늦게 남은 갱신 요청도 시작하지 않는다.
  expect(signal?.aborted).toBe(true);
  expect(fetchBoard).toHaveBeenCalledTimes(1);
});

test('조회가 실패하면, 대기 중 변경으로 즉시 재요청하지 않고 이전 자료와 오류를 표시한다', async () => {
  // given: 첫 재조회 중 여러 변경이 도착했다.
  const responses: Array<(response: Response) => void> = [];
  fetchBoard.mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)));
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const { result } = renderBoardHook(() => useSituationBoardData('incident-001', []));
  const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
  act(() => {
    subscription.onOpen?.();
    subscription.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });
  });

  // when: 진행 중이던 조회가 HTTP 500으로 실패한다.
  await act(async () => responses[0](Response.json({ error: 'server_error' }, { status: 500 })));

  // then: 새 이벤트가 재시도 대기를 우회하지 않고, 자동 복구 후 미반영 변경도 조회한다.
  await waitFor(() => expect(result.current.syncStatus?.tone).toBe('error'));
  expect(result.current.apiBoard?.boardResponseVersion).toBe(1);
  expect(fetchBoard).toHaveBeenCalledTimes(1);
  act(() => subscription.onEvent({ ...boardEvent(), eventId: 'next-event' }, { lastEventId: '902', eventType: 'PERSON_FOUND' }));
  expect(fetchBoard).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(2), { timeout: 2500 });
  await act(async () => responses[1](Response.json({ ...boardResponse({}), boardResponseVersion: 2 })));
  await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(3));
  await act(async () => responses[2](Response.json({ ...boardResponse({}), boardResponseVersion: 3 })));
  await waitFor(() => expect(result.current.apiBoard?.boardResponseVersion).toBe(3));
  expect(result.current.syncStatus).toBeNull();
  expect(fetchBoard).toHaveBeenCalledTimes(3);
});

test.each(['INCIDENT_CLOSED', 'INCIDENT_PURGED'])(
  '조회 중 %s가 도착하면, 구독은 바로 닫고 후속 조회에서 종료 상태를 반영한다',
  async (eventType) => {
    // given: 지도 재조회 응답을 기다리고 있다.
    const responses: Array<(response: Response) => void> = [];
    fetchBoard.mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)));
    const close = vi.fn();
    vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close });
    const { result } = renderBoardHook(() => useSituationBoardData('incident-001', []));
    const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
    act(() => subscription.onOpen?.());

    // when: 기존 응답보다 늦게 종료 이벤트가 도착한다.
    act(() => subscription.onEvent({ ...boardEvent(), type: eventType }, { lastEventId: '901', eventType }));

    // then: 구독을 즉시 닫고, 종료 전 응답 뒤에 최종 상태를 한 번 더 조회한다.
    expect(close).toHaveBeenCalledTimes(1);
    expect(fetchBoard).toHaveBeenCalledTimes(1);
    await act(async () => responses[0](Response.json(boardResponse({}))));
    await waitFor(() => expect(fetchBoard).toHaveBeenCalledTimes(2));
    await act(async () => responses[1](Response.json(boardResponse({
      incident_terminal: [{
        incidentId: 'incident-001',
        terminalStatus: eventType === 'INCIDENT_PURGED' ? 'PURGED' : 'CLOSED',
        closedStatus: eventType === 'INCIDENT_PURGED' ? 'purged' : 'closed',
        writeDisabledReason: eventType === 'INCIDENT_PURGED' ? 'purged' : 'incident_closed',
        localPurgeState: 'not_started',
      }],
    }))));
    await waitFor(() => expect(shouldSubscribeIncidentBoardEvents(result.current.apiBoard)).toBe(false));
    expect(fetchBoard).toHaveBeenCalledTimes(2);
  },
);

test('SSE 계측을 켜면 중복 수신도 기록하되 같은 이벤트의 재조회는 반복하지 않는다', () => {
  // given: 계측 수집기를 연결한 상황판이다.
  vi.mocked(openIncidentBoardEventStream).mockReturnValue({ closed: new Promise<void>(() => {}), close: vi.fn() });
  const records: Array<{ stage: string; duplicate: boolean; eventId: string }> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent && event.detail.stage === 'sse_received') records.push(event.detail);
  };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    renderBoardHook(() => useSituationBoardData('incident-001', []));
    const subscription = vi.mocked(openIncidentBoardEventStream).mock.calls[0][0];
    const event = { ...boardEvent(), payload: { version: 7, coordinates: [127, 35] } };

    // when: 같은 SSE 이벤트를 두 번 수신한다.
    subscription.onEvent(event, { lastEventId: '901', eventType: event.type });
    subscription.onEvent(event, { lastEventId: '901', eventType: event.type });

    // then: 수신 2회와 실제 재조회 요청 1회를 구분하고 payload를 기록하지 않는다.
    expect(records.map((record) => record.duplicate)).toEqual([false, true]);
    expect(records.every((record) => record.stage === 'sse_received' && record.eventId === event.eventId)).toBe(true);
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(1);
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
  const { rerender } = renderBoardHook(({ incidentId }) => useSituationBoardData(incidentId, []), {
    initialProps: { incidentId: 'incident-001' },
  });
  openStream.mock.calls[0][0].onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });

  // when: 이미 조회된 다른 사건의 상황판으로 이동한다.
  queryClient.setQueryData(incidentBoardQueryKeys.detail({ incidentId: 'incident-002', includeSlots: boardSlotsWithoutPaths }), { ...boardResponse({}), incidentId: 'incident-002' });
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
  const { unmount } = renderBoardHook(() => useSituationBoardData('incident-001', []));
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
    renderBoardHook(() => useSituationBoardData('incident-001', []));
    const connection = openStream.mock.calls[0][0];
    connection.onEvent(boardEvent(), { lastEventId: '901', eventType: 'PERSON_FOUND' });

    // when: 스트림 오류 또는 재전송 불가를 통보받고 재연결한다.
    connection.onRefetchRequired?.({ reason });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
    });

    // then: 현재 상태를 다시 조회하고, 네트워크 오류이면 이어서 수신한다.
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(2);
    expect(openStream.mock.calls[1][0].lastEventId).toBe(reason === 'gone_refetch_required' ? null : '901');
    // 재연결에서 같은 이벤트가 재전송돼도 중복 반영하지 않는다.
    openStream.mock.calls[1][0].onEvent(boardEvent(), { lastEventId: '902', eventType: 'PERSON_FOUND' });
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(2);
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

  // when: 거부된 연결 이후 9초가 지난다.
  renderBoardHook(() => useSituationBoardData('incident-001', []));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(9_000);
  });

  // then: 권한 없는 요청을 반복하지 않는다.
  expect(fetchStream).toHaveBeenCalledTimes(1);
  expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
});

test('재전송 불가 후 연결이 다시 열리면, 연결 대기 중 변경된 상태도 재조회한다', async () => {
  // given: 재전송 불가로 전체 상태를 조회한 뒤 재연결을 기다린다.
  vi.useFakeTimers();
  const openStream = vi.mocked(openIncidentBoardEventStream);
  openStream
    .mockImplementation(() => ({ closed: new Promise<void>(() => {}), close: vi.fn() }))
    .mockReturnValueOnce({ closed: Promise.resolve(), close: vi.fn() });
  renderBoardHook(() => useSituationBoardData('incident-001', []));
  openStream.mock.calls[0][0].onRefetchRequired?.({ reason: 'gone_refetch_required' });
  expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(1);

  // when: 재연결이 완료된다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3_000);
  });
  openStream.mock.calls[1][0].onOpen?.();

  // then: 연결이 없던 동안의 변경도 현재 상태로 보완한다.
  expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(2);
});

test.each(['INCIDENT_CLOSED', 'INCIDENT_PURGED'])(
  '상황판이 %s를 수신하면, 마지막 상태를 조회하고 구독을 중단한다',
  async (eventType) => {
    // given: 연결은 종료되지만 상황판 재조회 응답은 아직 도착하지 않았다.
    vi.useFakeTimers();
    const close = vi.fn();
    const openStream = vi.mocked(openIncidentBoardEventStream);
    openStream.mockReturnValue({ closed: Promise.resolve(), close });
    renderBoardHook(() => useSituationBoardData('incident-001', []));

    // when: 종료 이벤트를 받고 재연결 대기 시간이 지난다.
    openStream.mock.calls[0][0].onEvent({ ...boardEvent(), type: eventType }, { lastEventId: '901', eventType });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(9_000);
    });

    // then: 화면 종료 상태를 갱신하되 연결을 반복하지 않는다.
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(1);
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
