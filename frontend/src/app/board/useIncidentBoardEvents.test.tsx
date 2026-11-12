import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { apiClient } from '../../shared/api';
import { measureBoardMapUpdate, recordBoardMeasurement } from '../../features/board/model/boardMeasurement';
import type { Map as MapLibreMap } from 'maplibre-gl';
import {
  boardReadRetryDelay,
  isRetryableBoardRead,
  waitForBoardReadRetry,
} from '../../features/board/model/boardReadRecovery';
import { useIncidentBoardQuery, type IncidentBoardResponse } from '../../features/board/api/incidentBoardApi';
import { refreshSearchPathPages, useSearchPathPages } from '../../features/path/api/searchPathPagesApi';
import type { SearchPathPage, SearchPathPageRow } from '../../features/path/model/searchPathPages';
import { useIncidentBoardEvents } from './useIncidentBoardEvents';

let client: QueryClient;
let maxActiveRequests: number;
let requestUrls: string[];
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(0);
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  maxActiveRequests = 0;
  requestUrls = [];
});
afterEach(() => {
  try {
    expect(maxActiveRequests).toBe(1);
    for (const url of requestUrls)
      expect(url).toMatch(/\/incidents\/incident-1\/(events|board|board\/search-paths\/(segments|changes)\/query)$/);
    for (const query of client.getQueryCache().findAll({ queryKey: ['searchPathPages'] })) {
      expect(query.state.error).toBeNull();
      expect(query.state.data).toHaveProperty('error', null);
    }
  } finally {
    cleanup();
    client.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    delete window.__SURI_MAP_PATH_TRACE__;
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    vi.useRealTimers();
  }
});

function segment(id: string, order: number, version = '1'): SearchPathPageRow['segments'][number] {
  return {
    id,
    version,
    startPointOrder: order,
    endPointOrder: order + 1,
    movementType: 'FOOT',
    geometry: {
      type: 'LineString',
      coordinates: [
        [127, 35 + order / 1000],
        [127, 35 + (order + 1) / 1000],
      ],
    },
    startedAt: '2026-10-15T00:00:00Z',
    endedAt: '2026-10-15T00:00:05Z',
  };
}
function page(segments: SearchPathPageRow['segments'], historyComplete = false, version = '1'): SearchPathPage {
  return {
    nextSearchPathId: null,
    hasMore: false,
    paths: [
      {
        id: 'path-1',
        accountId: 'account-1',
        opId: 'op-1',
        status: 'RECORDING',
        version,
        baselineVersion: '1',
        segments,
        segmentsProgress: { beforeStartPointOrder: historyComplete ? null : 4, completed: historyComplete },
        changesProgress: {
          appliedVersion: version,
          targetVersion: version,
          beforeStartPointOrder: null,
          completed: true,
        },
      },
    ],
  };
}
const recent = segment('recent', 4);
const history = segment('history', 2);
const oldest = segment('oldest', 0);
const newGps = segment('new-gps', 6, '2');

test('지도 계측을 끈 짧은 추적은, SSE와 조회를 연결하고 만료 후 기록하지 않는다', async () => {
  // given: 특정 사건만 60초 관찰하고 실제 SSE·조회 훅을 연결한다.
  window.__SURI_MAP_PATH_TRACE__ = { incidentId: 'incident-1', expiresAt: 60_000 };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
  const events: Array<Record<string, unknown>> = [];
  const collect = (event: Event) => events.push((event as CustomEvent<Record<string, unknown>>).detail);
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    const { respond, send, advance } = await renderConnectedPaths();
    await respond(0, page([recent]));
    events.length = 0;

    // when: 변경분을 받는 동안 새 알림이 들어오고 다음 변경분을 받는다.
    await send();
    await advance(400);
    await respond(1, page([]));
    await respond(2, page([newGps], false, '2'));

    // then: 같은 요청의 시작·응답·반영을 연결하고 선택 당시 대기 상태를 확인한다.
    expect(events.map((event) => event.stage)).toEqual([
      'sse_received',
      'path_refresh_requested',
      'path_page_response_received',
      'path_page_completed',
      'path_page_started',
      'path_page_response_received',
      'path_page_completed',
      'path_page_started',
    ]);
    for (const event of events) {
      expect(event.elapsedMs).toEqual(expect.any(Number));
      expect(event.timeOriginMs).toEqual(expect.any(Number));
      expect(event).not.toHaveProperty('paths');
    }
    expect(events[0].elapsedMs).toBeLessThan(events[2].elapsedMs as number);
    expect(events[1].queryScope).toBe('["searchPathPages","incident-1",["op-1"]]');
    expect(events.map((event) => event.stage)).toContain('sse_received');
    expect(events).toContainEqual(expect.objectContaining({ stage: 'path_refresh_requested', dirty: true }));
    const started = events.find((event) => event.stage === 'path_page_started');
    expect(started?.queryScope).toBe(events[1].queryScope);
    expect(started).toMatchObject({ kind: 'changes', dirty: true, hasMoreSegments: true, consecutiveChanges: 1 });
    expect(events).toContainEqual(
      expect.objectContaining({ stage: 'path_page_response_received', requestId: started?.requestId }),
    );
    expect(events).toContainEqual(
      expect.objectContaining({ stage: 'path_page_completed', requestId: started?.requestId }),
    );
    const count = events.length;
    recordBoardMeasurement('sse_received', { incidentId: 'another-incident' });
    recordBoardMeasurement('map_data_submitted', { incidentId: 'incident-1' });
    expect(events).toHaveLength(count);
    recordBoardMeasurement('sse_received', {
      incidentId: 'incident-1',
      payload: { secret: 'hidden' },
      coordinates: [127, 35],
      token: 'hidden',
    });
    expect(events.at(-1)).not.toHaveProperty('payload');
    expect(events.at(-1)).not.toHaveProperty('coordinates');
    expect(events.at(-1)).not.toHaveProperty('token');
    const map = {} as MapLibreMap;
    const data = { type: 'FeatureCollection' as const, features: [] };
    expect(measureBoardMapUpdate(map, 'paths', [], data)).toBe(data);
    await advance(60_000);
    const expiredCount = events.length;
    await send('event-after-expiry');
    expect(events).toHaveLength(expiredCount);
    delete window.__SURI_MAP_PATH_TRACE__;
    recordBoardMeasurement('sse_received', { incidentId: 'incident-1' });
    expect(events).toHaveLength(expiredCount);
    for (const expiresAt of [Date.now() + 60_001, Number.NaN]) {
      window.__SURI_MAP_PATH_TRACE__ = { incidentId: 'incident-1', expiresAt };
      recordBoardMeasurement('sse_received', { incidentId: 'incident-1' });
      expect(events).toHaveLength(expiredCount);
    }
  } finally {
    window.removeEventListener('suri-map:board-measurement', collect);
  }
});

test('조회 시작 기록을 받으며 갱신을 요청하면, 그 신호도 다음 변경분 조회에 반영한다', async () => {
  // given: 기록 수신 함수가 첫 변경분 시작 시 갱신을 요청한다.
  window.__SURI_MAP_PATH_TRACE__ = { incidentId: 'incident-1', expiresAt: 60_000 };
  let refreshed = false;
  const collect = (event: Event) => {
    const detail = (event as CustomEvent<Record<string, unknown>>).detail;
    if (detail.stage === 'path_page_started' && detail.kind === 'changes' && !refreshed) {
      refreshed = true;
      refreshSearchPathPages(client, 'incident-1');
    }
  };
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    const { respond, calls } = await renderConnectedPaths();
    // when: 최초 이력과 변경분 응답을 순서대로 받는다.
    await respond(0, page([recent]));
    await respond(1, page([]));
    // then: 기록 도중 들어온 신호를 지우지 않고 변경분을 한 번 더 조회한다.
    expect(refreshed).toBe(true);
    expect(calls.map((call) => call.kind)).toEqual(['segments', 'changes', 'changes']);
  } finally {
    window.removeEventListener('suri-map:board-measurement', collect);
  }
});

async function renderConnectedPaths() {
  const stream = new TransformStream<Uint8Array, Uint8Array>();
  const writer = stream.writable.getWriter();
  const fetchSse = vi.fn<typeof fetch>().mockImplementation(async (url, options) => {
    requestUrls.push(String(url));
    // 실제 fetch처럼 구독 해제 시 진행 중인 reader.read도 종료한다.
    options?.signal?.addEventListener(
      'abort',
      () => {
        void writer.abort().catch(() => {});
      },
      { once: true },
    );
    return new Response(stream.readable, { headers: { 'Content-Type': 'text/event-stream' } });
  });
  vi.stubGlobal('fetch', fetchSse);
  const calls: Array<{ kind: string; at: number; resolve: (value: SearchPathPage) => void }> = [];
  let active = 0;
  const post = vi.spyOn(apiClient, 'post').mockImplementation((url) => {
    requestUrls.push(url);
    active++;
    maxActiveRequests = Math.max(maxActiveRequests, active);
    return new Promise((resolve) =>
      calls.push({
        kind: url.includes('/segments/') ? 'segments' : 'changes',
        at: Date.now(),
        resolve: (value) => {
          active--;
          resolve(value);
        },
      }),
    );
  });
  const view = renderHook(
    () => {
      const paths = useSearchPathPages('incident-1', ['op-1'], true, {
        isRetryable: isRetryableBoardRead,
        retryDelay: boardReadRetryDelay,
        waitForRetry: waitForBoardReadRetry,
        recordMeasurement: recordBoardMeasurement,
      });
      useIncidentBoardEvents('incident-1', true);
      return paths;
    },
    {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    },
  );
  async function advance(ms = 0) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ms);
    });
  }
  async function respond(index: number, value: SearchPathPage) {
    await act(async () => {
      calls[index].resolve(value);
    });
    await advance();
  }
  async function send(eventId = 'event-1') {
    await act(async () => {
      await writer.write(
        new TextEncoder().encode(
          `id: 1\nevent: PATH_APPENDED\ndata: ${JSON.stringify({
            eventId,
            incidentId: 'incident-1',
            type: 'PATH_APPENDED',
            payloadFormatVersion: 1,
            sourceEntityType: 'search_path',
            sourceEntityId: 'path-1',
            occurredAt: '2026-10-15T00:00:00Z',
            payload: { version: 2 },
          })}\n\n`,
        ),
      );
    });
    await advance();
  }
  // 연결을 시작할 때 예약한 경로 변경분 조회를 먼저 처리해 이후 업무 이벤트와 구분한다.
  await advance();
  expect(fetchSse).toHaveBeenCalledTimes(1);
  expect(String(fetchSse.mock.calls[0][0])).toMatch(/\/incidents\/incident-1\/events$/);
  expect(calls.map((c) => c.kind)).toEqual(['segments']);
  return { ...view, calls, post, advance, respond, send };
}

test.each([false, true])(
  '변경분 조회 중 알림 수신이 %s이면, 이력보다 추가 변경 조회를 먼저 진행한다',
  async (receiveEvent) => {
    // given: 이력은 남았지만 진행 중 변경분 응답은 더 볼 변경이 없다고 답한다.
    const board = await renderConnectedPaths();
    await board.respond(0, { ...page([recent]), hasMore: true });
    expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes']);
    await board.advance(100);
    const signalAt = Date.now();
    // when: 변경분 응답 전에 실제 SSE 프레임을 보내거나 보내지 않은 대조를 실행한다.
    if (receiveEvent) await board.send();
    expect(board.post).toHaveBeenCalledTimes(2);
    await board.advance(400);
    await board.respond(1, page([]));
    // then: 알림이 있으면 현재 요청의 잔여 400ms만 기다리고 새 좌표부터 반영한다.
    if (receiveEvent) {
      expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'changes']);
      expect(board.calls[2].at - signalAt).toBe(400);
      expect(board.result.current.data?.paths[0].version).toBe('1');
      await board.respond(2, page([newGps], false, '2'));
      expect(board.result.current.data?.paths[0].version).toBe('2');
      expect(board.result.current.data?.paths[0].segments).toEqual([recent, newGps]);
      expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'changes', 'segments']);
      await board.advance(700);
      await board.respond(3, page([history], true));
      expect(board.result.current.data?.paths[0].segments).toEqual([history, recent, newGps]);
      await board.send();
      expect(board.post).toHaveBeenCalledTimes(4);
    } else {
      expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'segments']);
      await board.advance(700);
      await board.respond(2, page([history], true));
      expect(board.post).toHaveBeenCalledTimes(3);
      expect(board.result.current.data?.paths[0].segments).toEqual([history, recent]);
    }
    expect(board.result.current.data?.hasMoreSegments).toBe(false);
    expect(board.result.current.data?.hasMoreChanges).toBe(false);
    expect(board.result.current.isFetching).toBe(false);
  },
);

test('이력 조회 중 알림이 도착하면, 그 응답을 받은 직후 변경분을 조회한다', async () => {
  // given: 최초 변경 확인까지 마쳤고 과거 이력 페이지 하나만 남았다.
  const board = await renderConnectedPaths();
  await board.respond(0, { ...page([recent]), hasMore: true });
  await board.respond(1, page([]));
  expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'segments']);
  await board.advance(100);
  const signalAt = Date.now();
  // when: 이력 응답을 아직 받지 않은 상태에서 알림을 수신한다.
  await board.send();
  await board.advance(400);
  expect(board.post).toHaveBeenCalledTimes(3);
  await board.respond(2, page([history], true));
  // then: 이력 요청의 남은 400ms만 기다리고 신규 좌표까지 반영한다.
  expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'segments', 'changes']);
  expect(board.calls[3].at - signalAt).toBe(400);
  await board.respond(3, page([newGps], true, '2'));
  expect(board.result.current.data?.paths[0].segments).toEqual([history, recent, newGps]);
  expect(board.result.current.isFetching).toBe(false);
});

test('조회 종료 뒤 알림이 도착하면, 변경분을 즉시 조회하고 같은 알림은 다시 처리하지 않는다', async () => {
  // given: 이력과 최초 변경분 조회를 모두 마쳤다.
  const board = await renderConnectedPaths();
  await board.respond(0, page([history, recent], true));
  await board.respond(1, page([], true));
  expect(board.result.current.isFetching).toBe(false);
  await board.advance(100);
  const signalAt = Date.now();
  // when: 같은 업무 이벤트를 두 번 수신한다.
  await board.send();
  await board.send();
  // then: 대기 중인 이력 요청 없이 변경분 한 건만 시작한다.
  expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'changes']);
  expect(board.calls[2].at - signalAt).toBe(0);
  await board.respond(2, page([newGps], true, '2'));
  await board.send();
  expect(board.post).toHaveBeenCalledTimes(3);
  expect(board.result.current.data?.paths[0].version).toBe('2');
  expect(board.result.current.data?.paths[0].segments).toEqual([history, recent, newGps]);
  expect(board.result.current.isFetching).toBe(false);
});

test.each([
  { phase: 'changes', receiveEvent: false, expectedWait: 400 },
  { phase: 'changes', receiveEvent: true, expectedWait: 400 },
  { phase: 'segments', receiveEvent: false, expectedWait: 400 },
  { phase: 'segments', receiveEvent: true, expectedWait: 400 },
])(
  '$phase 조회 중 알림 수신이 $receiveEvent이면, 이력이 더 남아 있어도 변경 조회 필요 여부를 구분한다',
  async ({ phase, receiveEvent, expectedWait }) => {
    // given: 과거 이력 페이지가 둘 남고, 최초 변경분은 hasMore=false다.
    const board = await renderConnectedPaths();
    await board.respond(0, { ...page([recent]), hasMore: true });
    if (phase === 'segments') await board.respond(1, page([]));
    await board.advance(100);
    const signalAt = Date.now();
    const countBeforeSignal = board.calls.length;
    // when: 실제 이벤트를 보내거나 같은 응답 순서의 무신호 대조를 실행한다.
    if (receiveEvent) await board.send();
    await board.advance(400);
    expect(board.post).toHaveBeenCalledTimes(countBeforeSignal);
    let historyIndex = 2;
    if (phase === 'changes') {
      await board.respond(1, page([]));
      if (receiveEvent) {
        expect(board.calls[2].kind).toBe('changes');
        expect(board.calls[2].at - signalAt).toBe(expectedWait);
        await board.respond(2, page([newGps], false, '2'));
        historyIndex = 3;
      }
    }
    const partialHistory = page([history]);
    partialHistory.paths[0].segmentsProgress = { beforeStartPointOrder: 2, completed: false };
    expect(board.calls[historyIndex].kind).toBe('segments');
    expect(board.post.mock.calls[historyIndex][1]).toMatchObject({
      paths: [{ id: 'path-1', segmentsProgress: { beforeStartPointOrder: 4, completed: false } }],
    });
    await board.respond(historyIndex, { ...partialHistory, hasMore: true });
    expect(board.result.current.data?.hasMoreSegments).toBe(true);
    // then: 변경분을 받은 뒤에도 남은 이력의 이어받기 위치와 최신 구간을 보존한다.
    if (receiveEvent) {
      if (phase === 'segments') {
        expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'segments', 'changes']);
        expect(board.calls[3].at - signalAt).toBe(expectedWait);
        await board.respond(3, page([newGps], false, '2'));
      }
      expect(board.result.current.data?.paths[0].version).toBe('2');
      expect(board.result.current.data?.hasMoreSegments).toBe(true);
      expect(board.calls[4].kind).toBe('segments');
      expect(board.post.mock.calls[4][1]).toMatchObject({
        paths: [{ id: 'path-1', segmentsProgress: { beforeStartPointOrder: 2, completed: false } }],
      });
      await board.respond(4, page([oldest], true));
      expect(board.result.current.data?.paths[0].segments).toEqual([oldest, history, recent, newGps]);
      expect(board.post).toHaveBeenCalledTimes(5);
    } else {
      expect(board.calls.map((c) => c.kind)).toEqual(['segments', 'changes', 'segments', 'segments']);
      await board.respond(3, page([oldest], true));
      expect(board.result.current.data?.paths[0].segments).toEqual([oldest, history, recent]);
      expect(board.post).toHaveBeenCalledTimes(4);
    }
    expect(board.result.current.data?.hasMoreSegments).toBe(false);
    expect(board.result.current.data?.hasMoreChanges).toBe(false);
    expect(board.result.current.isFetching).toBe(false);
  },
);

function boardResponse(lastSseSequence: number, opIds: readonly string[]): IncidentBoardResponse {
  return {
    incidentId: 'incident-1',
    boardResponseVersion: 1,
    serverTs: '2026-11-12T00:00:00Z',
    activeOpId: opIds[0] ?? null,
    selectedOpIds: opIds,
    geometryHash: 'hash-board',
    slots: {},
    slotSources: {},
    sourceVersions: {},
    sourceHashes: {},
    lastSseSequence,
  };
}

async function advanceTimers(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

// 상황판 조회, 경로 조회, 지도 갱신 연결을 실제 화면처럼 함께 연결한다. 지도 갱신 연결은 첫 상황판 응답이 온 뒤에 시작된다.
function renderBoardWithEvents() {
  const connections: Array<{ lastEventId: string | null; writer: WritableStreamDefaultWriter<Uint8Array> | null }> = [];
  const rejectedConnections: Response[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn<typeof fetch>().mockImplementation(async (url, options) => {
      requestUrls.push(String(url));
      const lastEventId = (options?.headers as Record<string, string> | undefined)?.['Last-Event-ID'] ?? null;
      const rejected = rejectedConnections.shift();
      if (rejected) {
        connections.push({ lastEventId, writer: null });
        return rejected;
      }
      const stream = new TransformStream<Uint8Array, Uint8Array>();
      const writer = stream.writable.getWriter();
      options?.signal?.addEventListener('abort', () => void writer.abort().catch(() => {}), { once: true });
      connections.push({ lastEventId, writer });
      return new Response(stream.readable, { headers: { 'Content-Type': 'text/event-stream' } });
    }),
  );
  const boardReads: Array<{ opIds: readonly string[]; resolve: (value: IncidentBoardResponse) => void }> = [];
  vi.spyOn(apiClient, 'get').mockImplementation((url, options) => {
    requestUrls.push(url);
    const opIds = (options?.query?.opIds ?? []) as readonly string[];
    return new Promise((resolve) => boardReads.push({ opIds, resolve }));
  });
  const pathCalls: string[] = [];
  const pathResponses: Array<(value: SearchPathPage) => void> = [];
  let active = 0;
  vi.spyOn(apiClient, 'post').mockImplementation((url) => {
    requestUrls.push(url);
    pathCalls.push(url.includes('/segments/') ? 'segments' : 'changes');
    active++;
    maxActiveRequests = Math.max(maxActiveRequests, active);
    return new Promise((resolve) =>
      pathResponses.push((value) => {
        active--;
        resolve(value);
      }),
    );
  });
  const view = renderHook(
    ({ opIds }: { opIds: readonly string[] }) => {
      const board = useIncidentBoardQuery({ incidentId: 'incident-1', opIds });
      useSearchPathPages('incident-1', opIds, true, {
        isRetryable: isRetryableBoardRead,
        retryDelay: boardReadRetryDelay,
        waitForRetry: waitForBoardReadRetry,
        recordMeasurement: recordBoardMeasurement,
      });
      useIncidentBoardEvents('incident-1', Boolean(board.data));
    },
    {
      initialProps: { opIds: ['op-1'] },
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    },
  );
  async function respondBoard(index: number, lastSseSequence: number) {
    await act(async () => {
      boardReads[index].resolve(boardResponse(lastSseSequence, boardReads[index].opIds));
    });
    await advanceTimers();
  }
  async function respondPath(index: number, value: SearchPathPage) {
    await act(async () => {
      pathResponses[index](value);
    });
    await advanceTimers();
  }
  async function send(id: string, eventId: string) {
    await act(async () => {
      await connections.at(-1)?.writer?.write(
        new TextEncoder().encode(
          `id: ${id}\nevent: PATH_APPENDED\ndata: ${JSON.stringify({
            eventId,
            incidentId: 'incident-1',
            type: 'PATH_APPENDED',
            payloadFormatVersion: 1,
            sourceEntityType: 'search_path',
            sourceEntityId: 'path-1',
            occurredAt: '2026-11-12T00:00:00Z',
            payload: { version: 2 },
          })}\n\n`,
        ),
      );
    });
    await advanceTimers();
  }
  async function closeConnection() {
    await act(async () => {
      await connections.at(-1)?.writer?.close();
    });
    await advanceTimers();
  }
  function rejectNextConnection(status: number, error: string) {
    rejectedConnections.push(
      new Response(JSON.stringify({ error }), { status, headers: { 'Content-Type': 'application/json' } }),
    );
  }
  return {
    ...view,
    connections,
    boardReads,
    pathCalls,
    respondBoard,
    respondPath,
    send,
    closeConnection,
    rejectNextConnection,
  };
}

test('첫 상황판 응답을 받으면, 그 응답의 순번을 Last-Event-ID로 보내 연결한다', async () => {
  // given: 첫 상황판 응답에는 순번 7까지 반영돼 있다.
  const board = renderBoardWithEvents();
  await advanceTimers();
  expect(board.connections).toHaveLength(0);

  // when: 첫 상황판 응답을 받는다.
  await board.respondBoard(0, 7);

  // then: 연결 요청의 Last-Event-ID는 7이다.
  expect(board.connections.map((connection) => connection.lastEventId)).toEqual(['7']);
});

test('연결이 열려도, 상황판을 다시 조회하지 않는다', async () => {
  // given: 첫 상황판 응답으로 연결을 시작했다.
  const board = renderBoardWithEvents();
  await board.respondBoard(0, 7);

  // when: 연결이 열리고 시간이 지난다.
  await advanceTimers(10_000);

  // then: 상황판 조회는 첫 조회 한 번뿐이다.
  expect(board.connections).toHaveLength(1);
  expect(board.boardReads).toHaveLength(1);
});

test('경로 이력 첫 조회 중에 연결을 시작하면, 경로 변경분 조회 요청이 늘지 않는다', async () => {
  // given: 경로 이력 첫 조회가 진행 중이다.
  const board = renderBoardWithEvents();
  await advanceTimers();
  expect(board.pathCalls).toEqual(['segments']);

  // when: 첫 상황판 응답을 받아 연결을 시작한다. 그 뒤 경로 이력과 경로 변경분 응답을 받는다.
  await board.respondBoard(0, 7);
  await board.respondPath(0, page([history, recent], true));
  await board.respondPath(1, page([], true));

  // then: 예약은 첫 조회의 경로 변경분 요청에 합쳐져 경로 변경분 요청은 한 번뿐이다.
  expect(board.pathCalls).toEqual(['segments', 'changes']);
});

test('경로 이력 첫 조회를 마친 뒤 연결을 시작하면, 경로 변경분 조회를 한 번만 예약한다', async () => {
  // given: 상황판 응답보다 먼저 경로 이력 첫 조회를 마쳤다.
  const board = renderBoardWithEvents();
  await advanceTimers();
  await board.respondPath(0, page([history, recent], true));
  await board.respondPath(1, page([], true));
  expect(board.pathCalls).toEqual(['segments', 'changes']);

  // when: 첫 상황판 응답을 받아 연결을 시작한다. 그 뒤 연결이 끊겨 다시 연결한다.
  await board.respondBoard(0, 7);
  expect(board.pathCalls).toEqual(['segments', 'changes', 'changes']);
  await board.respondPath(2, page([], true));
  await board.closeConnection();
  await advanceTimers(3_000);

  // then: 경로 변경분 조회는 연결을 시작할 때 한 번만 나간다. 다시 연결할 때는 나가지 않는다.
  expect(board.connections).toHaveLength(2);
  expect(board.pathCalls).toEqual(['segments', 'changes', 'changes']);
});

test('409를 받으면, 상황판을 다시 조회한 뒤 새 응답의 순번으로 다시 연결한다', async () => {
  // given: 첫 상황판 응답의 순번 7로 연결하면 이어 받을 이력이 없어 409가 온다.
  const board = renderBoardWithEvents();
  board.rejectNextConnection(409, 'gone_refetch_required');
  await board.respondBoard(0, 7);
  expect(board.boardReads).toHaveLength(2);

  // when: 다시 조회한 상황판 응답이 오기 전에 재연결 대기 시간이 지난다. 그 뒤 순번 12의 응답을 받는다.
  await advanceTimers(3_000);
  expect(board.connections).toHaveLength(1);
  await board.respondBoard(1, 12);
  await advanceTimers(3_000);

  // then: 다시 연결한 요청의 Last-Event-ID는 새 응답의 순번 12다.
  expect(board.connections.map((connection) => connection.lastEventId)).toEqual(['7', '12']);
});

test('수색 차수를 바꿔 상황판을 다시 조회해도, 이미 연결한 수신 위치는 바뀌지 않는다', async () => {
  // given: 경로 조회를 모두 마쳤다. 첫 상황판 응답의 순번 7로 연결했다.
  const board = renderBoardWithEvents();
  await advanceTimers();
  await board.respondPath(0, page([history, recent], true));
  await board.respondPath(1, page([], true));
  await board.respondBoard(0, 7);
  await board.respondPath(2, page([], true));

  // when: 수색 차수를 바꿔 상황판을 다시 조회한다. 순번 20의 응답을 받는다. 그 뒤 연결이 끊긴다.
  board.rerender({ opIds: ['op-2'] });
  await advanceTimers();
  expect(board.boardReads.map((read) => read.opIds)).toEqual([['op-1'], ['op-2']]);
  await board.respondBoard(1, 20);
  await board.closeConnection();
  await advanceTimers(3_000);

  // then: 다시 연결한 요청의 Last-Event-ID도 처음 연결한 순번 7이다.
  expect(board.connections.map((connection) => connection.lastEventId)).toEqual(['7', '7']);
});

test('새 이벤트를 받으면, 상황판과 경로 변경분을 다시 조회한다', async () => {
  // given: 경로 조회를 모두 마쳤다. 첫 상황판 응답의 순번 7로 연결했다.
  const board = renderBoardWithEvents();
  await advanceTimers();
  await board.respondPath(0, page([history, recent], true));
  await board.respondPath(1, page([], true));
  await board.respondBoard(0, 7);
  await board.respondPath(2, page([], true));

  // when: 순번 8의 새 이벤트를 받는다.
  await board.send('8', 'event-8');

  // then: 상황판 조회와 경로 변경분 조회가 한 번씩 더 나간다. 다시 연결한 요청의 Last-Event-ID는 받은 순번 8이다.
  expect(board.boardReads).toHaveLength(2);
  expect(board.pathCalls).toEqual(['segments', 'changes', 'changes', 'changes']);
  await board.respondBoard(1, 8);
  await board.respondPath(3, page([newGps], true, '2'));
  await board.closeConnection();
  await advanceTimers(3_000);
  expect(board.connections.map((connection) => connection.lastEventId)).toEqual(['7', '8']);
});

test('새 알림이 계속 도착하면, 변경분 두 페이지 뒤에는 남은 과거 경로도 받는다', async () => {
  // given: 과거 경로가 남은 상태에서 변경분을 조회하고 있다.
  const board = await renderConnectedPaths();
  await board.respond(0, { ...page([recent]), hasMore: true });
  // when: 각 변경분 조회 중 서로 다른 알림이 여러 번 도착한다.
  await board.send('event-1');
  await board.respond(1, page([]));
  expect(board.calls[2].kind).toBe('changes');
  for (let i = 2; i <= 10; i++) await board.send(`event-${i}`);
  expect(board.post).toHaveBeenCalledTimes(3);
  await board.respond(2, page([newGps], false, '2'));
  // then: 알림을 합치되 이력을 건너뛰지 않고, 이후 변경 확인도 잊지 않는다.
  expect(board.calls.map((call) => call.kind)).toEqual(['segments', 'changes', 'changes', 'segments']);
  await board.respond(3, page([history], true));
  expect(board.calls[4].kind).toBe('changes');
  expect(board.post.mock.calls[4][1]).toMatchObject({
    paths: [{ id: 'path-1', changesProgress: { appliedVersion: '2', completed: true } }],
  });
  await board.respond(4, page([], true, '2'));
  expect(board.result.current.data?.paths[0].segments).toEqual([history, recent, newGps]);
  expect(board.result.current.isFetching).toBe(false);
});
