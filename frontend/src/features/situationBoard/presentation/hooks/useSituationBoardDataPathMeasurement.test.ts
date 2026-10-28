import { QueryClient, QueryClientProvider, onlineManager } from '@tanstack/react-query';
import { act, cleanup, render, renderHook, waitFor } from '@testing-library/react';
import { createElement, useEffect, useMemo, type ReactNode } from 'react';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { afterEach, expect, test, vi } from 'vitest';

import { apiClient, ApiHttpError } from '../../../../shared/api/client';
import { createMovementPathFeatureMapper } from '../../../../shared/model/boardMapFeatures';
import { markBoardPathSourceCurrent, recordBoardOpenClicked } from '../../../board/model/boardMeasurement';
import type { openIncidentBoardEventStream } from '../../../board/api/incidentBoardEventStream';
import { searchPathPagesKey } from '../../../path/api/searchPathPagesApi';
import { syncMovementPathSource } from '../components/map/movementPathSource';
import { useSituationBoardData } from './useSituationBoardData';

type StreamOptions = Parameters<typeof openIncidentBoardEventStream>[0];
const { stream } = vi.hoisted(() => ({ stream: { options: null as StreamOptions | null } }));
vi.mock('../../../board/api/incidentBoardEventStream', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../board/api/incidentBoardEventStream')>()),
  openIncidentBoardEventStream: (options: StreamOptions) => {
    stream.options = options;
    return { closed: new Promise<void>(() => {}), close: () => {} };
  },
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
  onlineManager.setOnline(true);
});

// 실제 QueryClient, 경로 조회 훅, 상황판 데이터 훅, SSE 처리기를 연결하고 HTTP와 지도 idle만 대신한다.
test.each([
  { waitForReact: false, label: '전달되기 전' },
  { waitForReact: true, label: '전달된 뒤' },
])(
  '새 구간 알림 뒤 조회 상태가 React에 $label 발생한 idle은, 변경분을 받기 전이라 끝 시점으로 기록하지 않는다',
  async ({ waitForReact }) => {
    // given: 상황판 열기를 누르고 최초 이력과 변경분을 모두 받아 source도 반영했다. 첫 idle은 아직이다.
    const client = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity, staleTime: Infinity, retry: false } } });
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      incidentId: 'incident-1',
      boardResponseVersion: 1,
      serverTs: '2026-10-07T00:00:00Z',
      activeOpId: 'op-1',
      selectedOpIds: ['op-1'],
      geometryHash: '',
      slots: {},
      slotSources: {},
      sourceVersions: {},
      sourceHashes: {},
    });
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ paths: [], nextSearchPathId: null, hasMore: false });
    const ends: unknown[] = [];
    const collect = (event: Event) => {
      if (event instanceof CustomEvent && event.detail.stage === 'all_paths_map_idle') ends.push(event.detail);
    };
    const idleListeners: Array<() => void> = [];
    const map = {
      once: (name: string, listener: () => void) => {
        if (name === 'idle') idleListeners.push(listener);
      },
      triggerRepaint: () => {},
    } as unknown as MapLibreMap;
    const idle = () => idleListeners.splice(0).forEach((listener) => listener());
    const pathFetchStatus = () => client.getQueryState(searchPathPagesKey('incident-1', ['op-1']))?.fetchStatus;
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
    window.addEventListener('suri-map:board-measurement', collect);
    try {
      recordBoardOpenClicked('incident-1', 10);
      renderHook(() => useSituationBoardData('incident-1', []), {
        wrapper: ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children),
      });
      await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
      await waitFor(() => expect(pathFetchStatus()).toBe('idle'));
      markBoardPathSourceCurrent(map, true);
      expect(idleListeners).toHaveLength(1);
      const pending: Array<(value: unknown) => void> = [];
      post.mockImplementation(() => new Promise((resolve) => pending.push(resolve)));

      // when: 새 구간 저장 알림을 받아 변경분 조회가 시작되고, 그 응답 전에 idle이 발생한다.
      const notify = () => {
        stream.options?.onEvent(
          {
            eventId: 'new-segment-1',
            incidentId: 'incident-1',
            type: 'SEARCH_PATH_SEGMENT_UPDATED',
            payloadFormatVersion: 1,
            sourceEntityType: 'search_path_segment',
            sourceEntityId: 'segment-1',
            occurredAt: '2026-10-07T00:00:01Z',
            payload: { version: 2 },
          },
          { eventType: 'SEARCH_PATH_SEGMENT_UPDATED', lastEventId: '1' },
        );
        expect(pathFetchStatus()).toBe('fetching');
      };
      if (waitForReact) {
        await act(async () => {
          notify();
          await new Promise((resolve) => setTimeout(resolve, 10));
        });
      } else {
        act(notify);
      }
      act(idle);

      // then: 변경분을 아직 받지 않았으므로 끝 시점을 남기지 않는다.
      expect(pathFetchStatus()).toBe('fetching');
      expect(ends).toHaveLength(0);

      // when: 그 변경분을 받은 뒤 idle이 발생한다.
      await waitFor(() => expect(pending).toHaveLength(1));
      await act(async () => pending[0]({ paths: [], nextSearchPathId: null, hasMore: false }));
      await waitFor(() => expect(pathFetchStatus()).toBe('idle'));
      await waitFor(() => expect(idleListeners).toHaveLength(1));
      act(idle);

      // then: 이때의 idle을 끝 시점으로 한 번 남긴다.
      expect(ends).toHaveLength(1);
    } finally {
      window.removeEventListener('suri-map:board-measurement', collect);
      client.clear();
    }
  },
);

// 위 연결에 실제 경로 도형 생성과 syncMovementPathSource를 더해, 자식 지도 effect가 부모 알림보다 먼저 source를 갱신하게 한다.
test.each([
  { response: 'changed', waitForReact: false },
  { response: 'changed', waitForReact: true },
  { response: 'invalid', waitForReact: false },
  { response: 'invalid', waitForReact: true },
] as const)(
  '변경분 응답이 $response 이고 React 알림을 기다림이 $waitForReact 일 때, 그 응답을 반영하지 않은 source의 idle은 끝 시점으로 기록하지 않는다',
  async ({ response, waitForReact }) => {
    // given: 상황판 열기를 누르고 경로 하나의 최초 이력과 변경분을 모두 받아 source에 반영했다. 첫 idle은 아직이다.
    const client = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity, staleTime: Infinity, retry: false } } });
    const key = searchPathPagesKey('incident-1', ['op-1']);
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      incidentId: 'incident-1',
      boardResponseVersion: 1,
      serverTs: '2026-10-07T00:00:00Z',
      activeOpId: 'op-1',
      selectedOpIds: ['op-1'],
      geometryHash: '',
      slots: {},
      slotSources: {},
      sourceVersions: {},
      sourceHashes: {},
    });
    const segment = (version: number) => ({
      id: `segment-${version}`,
      version: String(version),
      startPointOrder: version - 1,
      endPointOrder: version,
      movementType: 'FOOT',
      geometry: {
        type: 'LineString',
        coordinates: [
          [127 + (version - 1) / 100, 37],
          [127 + version / 100, 37],
        ],
      },
      startedAt: '2026-10-07T00:00:00Z',
      endedAt: '2026-10-07T00:00:05Z',
    });
    const row = {
      id: 'path-1',
      accountId: 'account-1',
      opId: 'op-1',
      status: 'RECORDING',
      version: '1',
      baselineVersion: '1',
      segments: [segment(1)],
      segmentsProgress: { beforeStartPointOrder: null, completed: true },
    };
    const empty = { paths: [], nextSearchPathId: null, hasMore: false };
    const post = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ ...empty, paths: [row] }).mockResolvedValue(empty);
    const sourceListeners = new Map<string, (event: { sourceDataType?: string }) => void>();
    const idleListeners: Array<() => void> = [];
    let loaded = true;
    const source = {
      type: 'geojson',
      loaded: () => loaded,
      setData: vi.fn(() => {
        loaded = false;
      }),
      updateData: vi.fn(() => {
        loaded = false;
      }),
      on: (name: string, listener: (event: { sourceDataType?: string }) => void) => sourceListeners.set(name, listener),
    };
    const map = {
      getSource: () => source,
      on: () => {},
      once: (name: string, listener: () => void) => {
        if (name === 'idle') idleListeners.push(listener);
      },
      triggerRepaint: () => {},
    } as unknown as MapLibreMap;
    const finish = () => {
      loaded = true;
      sourceListeners.get('data')?.({ sourceDataType: 'content' });
    };
    const idle = () => idleListeners.splice(0).forEach((listener) => listener());
    const sourceUpdates = () => source.setData.mock.calls.length + source.updateData.mock.calls.length;
    const ends: unknown[] = [];
    const collect = (event: Event) => {
      if (event instanceof CustomEvent && event.detail.stage === 'all_paths_map_idle') ends.push(event.detail);
    };
    let view: ReturnType<typeof useSituationBoardData> | undefined;
    function MapPaths({ paths }: { paths: ReturnType<typeof useSituationBoardData>['board']['movementPaths'] }) {
      const mapper = useMemo(() => createMovementPathFeatureMapper(), []);
      const data = useMemo(() => mapper(paths, 'op-1'), [mapper, paths]);
      useEffect(() => syncMovementPathSource(map, 'operational-movement-path', data, []), [data]);
      return null;
    }
    function Board() {
      view = useSituationBoardData('incident-1', []);
      return createElement(MapPaths, { paths: view.board.movementPaths });
    }
    const cacheSegments = () =>
      client.getQueryData<{ paths: Array<{ segments: unknown[] }> }>(key)?.paths[0]?.segments.length;
    const renderedSegments = () =>
      (view?.apiBoard?.slots.path as Array<{ segments: unknown[] }> | undefined)?.[0]?.segments.length;
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
    window.addEventListener('suri-map:board-measurement', collect);
    try {
      recordBoardOpenClicked('incident-1', 10);
      render(createElement(QueryClientProvider, { client }, createElement(Board)));
      await waitFor(() => expect(renderedSegments()).toBe(1));
      await waitFor(() => expect(client.getQueryState(key)?.fetchStatus).toBe('idle'));
      act(() => {
        finish();
        finish();
      });
      expect(idleListeners).toHaveLength(1);
      const sourceUpdatesBeforeResponse = sourceUpdates();
      const pending: Array<(value: unknown) => void> = [];
      post.mockImplementation(() => new Promise((resolve) => pending.push(resolve)));

      // when: 새 구간 저장 알림 뒤 변경분 응답 처리가 끝나고, React 구독 알림 타이머 전에 idle이 발생한다.
      await act(async () => {
        stream.options?.onEvent(
          {
            eventId: 'new-segment-2',
            incidentId: 'incident-1',
            type: 'SEARCH_PATH_SEGMENT_UPDATED',
            payloadFormatVersion: 1,
            sourceEntityType: 'search_path_segment',
            sourceEntityId: 'segment-2',
            occurredAt: '2026-10-07T00:00:01Z',
            payload: { version: 2 },
          },
          { eventType: 'SEARCH_PATH_SEGMENT_UPDATED', lastEventId: '2' },
        );
        for (let i = 0; i < 100 && pending.length === 0; i++) await Promise.resolve();
        expect(pending).toHaveLength(1);
        pending[0](
          response === 'changed'
            ? {
                ...empty,
                paths: [
                  {
                    ...row,
                    version: '2',
                    segments: [segment(2)],
                    changesProgress: {
                      appliedVersion: '2',
                      targetVersion: '2',
                      beforeStartPointOrder: null,
                      completed: true,
                    },
                  },
                ],
              }
            : { paths: 'invalid_response' },
        );
        for (let i = 0; i < 100 && client.getQueryState(key)?.fetchStatus === 'fetching'; i++) await Promise.resolve();
        expect(client.getQueryState(key)?.fetchStatus).toBe('idle');
        expect(cacheSegments()).toBe(response === 'changed' ? 2 : 1);
        if (response === 'invalid') expect(client.getQueryState(key)?.status).toBe('error');
        if (!waitForReact) {
          expect(renderedSegments()).toBe(1);
          expect(sourceUpdates()).toBe(sourceUpdatesBeforeResponse);
          idle();
          // then: 받은 응답이 화면과 source에 아직 없으므로 끝 시점을 남기지 않는다.
          expect(ends).toHaveLength(0);
        }
      });
      if (!waitForReact) return;

      if (response === 'invalid') {
        // then: 화면이 실패를 반영한 뒤의 idle도 유효한 변경분을 받지 못했으므로 남기지 않는다.
        await waitFor(() => expect(view?.syncStatus?.tone).toBe('error'));
        act(idle);
        expect(ends).toHaveLength(0);
        return;
      }
      // then: 새 자료를 source에 보냈지만 반영 전 idle은 남기지 않고, 반영 뒤 idle만 남긴다.
      await waitFor(() => expect(renderedSegments()).toBe(2));
      expect(sourceUpdates()).toBeGreaterThan(sourceUpdatesBeforeResponse);
      act(idle);
      expect(ends).toHaveLength(0);
      act(finish);
      act(idle);
      expect(ends).toHaveLength(1);
    } finally {
      window.removeEventListener('suri-map:board-measurement', collect);
      client.clear();
    }
  },
);

function createSourceMap() {
  let loaded = true;
  const sourceListeners = new Map<string, (event: { sourceDataType: string }) => void>();
  const idleListeners: Array<() => void> = [];
  const source = {
    type: 'geojson',
    loaded: () => loaded,
    setData: vi.fn(() => {
      loaded = false;
    }),
    updateData: vi.fn(() => {
      loaded = false;
    }),
    on: (name: string, listener: (event: { sourceDataType: string }) => void) => sourceListeners.set(name, listener),
  };
  const map = {
    getSource: () => source,
    on: () => {},
    once: (name: string, listener: () => void) => {
      if (name === 'idle') idleListeners.push(listener);
    },
    triggerRepaint: () => {},
  } as unknown as MapLibreMap;
  return {
    map,
    finish: () => {
      loaded = true;
      sourceListeners.get('data')?.({ sourceDataType: 'content' });
    },
    idle: () => idleListeners.splice(0).forEach((listener) => listener()),
  };
}

test.each([
  { failure: 'invalid-response', trigger: 'reopen' },
  { failure: 'invalid-query', trigger: 'reopen' },
  { failure: 'invalid-response', trigger: 'reconnect' },
  { failure: 'invalid-query', trigger: 'reconnect' },
] as const)(
  '변경분 조회가 $failure 오류로 끝난 뒤 $trigger 로 다시 조회해도, 받지 못한 변경분이 있으므로 끝 시점을 기록하지 않는다',
  async ({ failure, trigger }) => {
    // given: 상황판 열기를 누르고 경로 하나의 최초 이력과 변경분을 받아 source에 반영했다.
    const client = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity, retry: false } } });
    const key = searchPathPagesKey('incident-1', ['op-1']);
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      incidentId: 'incident-1',
      boardResponseVersion: 1,
      serverTs: '2026-10-07T00:00:00Z',
      activeOpId: 'op-1',
      selectedOpIds: ['op-1'],
      geometryHash: '',
      slots: {},
      slotSources: {},
      sourceVersions: {},
      sourceHashes: {},
    });
    const row = {
      id: 'path-1',
      accountId: 'account-1',
      opId: 'op-1',
      status: 'RECORDING',
      version: '1',
      baselineVersion: '1',
      segments: [
        {
          id: 'segment-1',
          version: '1',
          startPointOrder: 0,
          endPointOrder: 1,
          movementType: 'FOOT',
          geometry: {
            type: 'LineString',
            coordinates: [
              [127, 37],
              [127.01, 37],
            ],
          },
          startedAt: '2026-10-07T00:00:00Z',
          endedAt: '2026-10-07T00:00:05Z',
        },
      ],
      segmentsProgress: { beforeStartPointOrder: null, completed: true },
    };
    const empty = { paths: [], nextSearchPathId: null, hasMore: false };
    const post = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ ...empty, paths: [row] }).mockResolvedValue(empty);
    let harness = createSourceMap();
    let view: ReturnType<typeof useSituationBoardData> | undefined;
    function MapPaths({ paths }: { paths: ReturnType<typeof useSituationBoardData>['board']['movementPaths'] }) {
      const mapper = useMemo(() => createMovementPathFeatureMapper(), []);
      const data = useMemo(() => mapper(paths, 'op-1'), [mapper, paths]);
      useEffect(() => syncMovementPathSource(harness.map, 'operational-movement-path', data, []), [data]);
      return null;
    }
    function Board() {
      view = useSituationBoardData('incident-1', []);
      return createElement(MapPaths, { paths: view.board.movementPaths });
    }
    const ends: unknown[] = [];
    const collect = (event: Event) => {
      if (event instanceof CustomEvent && event.detail.stage === 'all_paths_map_idle') ends.push(event.detail);
    };
    const cache = () => client.getQueryData<{ error: string | null; paths: Array<{ segments: unknown[] }> }>(key);
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
    window.addEventListener('suri-map:board-measurement', collect);
    try {
      recordBoardOpenClicked('incident-1', 10);
      const firstBoard = render(createElement(QueryClientProvider, { client }, createElement(Board)));
      await waitFor(() => expect(cache()?.paths[0]?.segments).toHaveLength(1));
      await waitFor(() => expect(client.getQueryState(key)?.fetchStatus).toBe('idle'));
      act(() => {
        harness.finish();
        harness.finish();
      });

      // when: 새 구간 저장 알림 뒤 변경분 조회가 다시 시도하지 않는 오류로 끝나고, 화면이 그 오류를 표시한 뒤 idle이 발생한다.
      if (failure === 'invalid-response') post.mockResolvedValue({ paths: 'invalid_response' });
      else post.mockRejectedValue(new ApiHttpError(400, 'invalid_search_path_query', {}));
      act(() => {
        stream.options?.onEvent(
          {
            eventId: 'new-segment-2',
            incidentId: 'incident-1',
            type: 'SEARCH_PATH_SEGMENT_UPDATED',
            payloadFormatVersion: 1,
            sourceEntityType: 'search_path_segment',
            sourceEntityId: 'segment-2',
            occurredAt: '2026-10-07T00:00:01Z',
            payload: { version: 2 },
          },
          { eventType: 'SEARCH_PATH_SEGMENT_UPDATED', lastEventId: '2' },
        );
      });
      await waitFor(() => expect(client.getQueryState(key)?.status).toBe('error'));
      await waitFor(() => expect(view?.syncStatus?.tone).toBe('error'));
      act(harness.idle);
      expect(ends).toHaveLength(0);
      const postsAfterFailure = post.mock.calls.length;

      // when: 같은 캐시로 상황판을 다시 열거나 네트워크가 다시 연결되어, HTTP 요청 없이 Query가 success가 된다.
      if (trigger === 'reopen') {
        firstBoard.unmount();
        await Promise.resolve();
        harness = createSourceMap();
        recordBoardOpenClicked('incident-1', 20);
        render(createElement(QueryClientProvider, { client }, createElement(Board)));
      } else {
        act(() => {
          onlineManager.setOnline(false);
          onlineManager.setOnline(true);
        });
      }
      await waitFor(() => expect(client.getQueryState(key)?.status).toBe('success'));
      await waitFor(() => expect(client.getQueryState(key)?.fetchStatus).toBe('idle'));
      await waitFor(() => expect(view?.syncStatus?.tone).toBe('error'));
      act(() => {
        harness.finish();
        harness.finish();
      });
      act(harness.idle);

      // then: 캐시에 오류가 남아 있고 변경분을 받지 못했으므로 끝 시점을 남기지 않는다. 화면의 오류 표시는 그대로다.
      expect(post.mock.calls.length).toBe(postsAfterFailure);
      expect(cache()?.error).toBe(
        failure === 'invalid-response' ? 'invalid_search_path_response' : 'invalid_search_path_query',
      );
      expect(ends).toHaveLength(0);
    } finally {
      window.removeEventListener('suri-map:board-measurement', collect);
      client.clear();
    }
  },
);
