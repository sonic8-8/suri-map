import { QueryClient, QueryClientProvider, onlineManager } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import type { ReactNode } from 'react';
import { apiClient, ApiHttpError, ApiNetworkError } from '../../../shared/api';
import { createBoardMovementPaths } from '../../../shared/model/boardMapSlots';
import {
  useSearchPathPages,
  refreshSearchPathPages,
  restrictSearchPathPages,
  searchPathPagesKey,
} from './searchPathPagesApi';
import {
  boardReadRetryDelay as pathReadRetryDelay,
  isRetryableBoardRead as isRetryablePathRead,
  waitForBoardReadRetry,
} from '../../board/model/boardReadRecovery';
import { recordBoardMeasurement } from '../../board/model/boardMeasurement';
import {
  createSearchPathPages,
  type LoadedSearchPath,
  type SearchPathPage,
  type SearchPathPageRow,
} from '../model/searchPathPages';

let client: QueryClient;
beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity, staleTime: Infinity } } });
});
afterEach(() => {
  cleanup();
  client.clear();
  onlineManager.setOnline(true);
  vi.restoreAllMocks();
  vi.useRealTimers();
});
function renderPaths(opIds = ['op-1']) {
  return renderHook(
    ({ ids }) =>
      useSearchPathPages('incident-1', ids, true, {
        isRetryable: isRetryablePathRead,
        retryDelay: pathReadRetryDelay,
        waitForRetry: waitForBoardReadRetry,
        recordMeasurement: recordBoardMeasurement,
      }),
    {
      initialProps: { ids: opIds },
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    },
  );
}
function row(overrides: Partial<SearchPathPageRow> = {}): SearchPathPageRow {
  return {
    id: 'path-1',
    accountId: 'account-1',
    opId: 'op-1',
    status: 'RECORDING',
    version: '1',
    baselineVersion: '1',
    segments: [],
    segmentsProgress: { beforeStartPointOrder: null, completed: true },
    ...overrides,
  };
}
function page(paths: SearchPathPageRow[] = [], hasMore = false): SearchPathPage {
  return { paths, nextSearchPathId: null, hasMore };
}

test('다른 최초 기준의 캐시가 이력을 모두 받았어도, 현재 기준의 미수신 구간을 계속 조회한다', async () => {
  // given: 기준 1까지는 모두 받았지만, 기준 5에서는 마지막 구간만 받은 상태다.
  const latest: LoadedSearchPath = {
    ...row(),
    version: '5',
    baselineVersion: '5',
    segmentsProgress: { beforeStartPointOrder: 8, completed: false },
    changesProgress: null,
    segments: [
      {
        id: 'segment-5',
        version: '5',
        startPointOrder: 8,
        endPointOrder: 9,
        movementType: 'FOOT',
        geometry: {
          type: 'LineString',
          coordinates: [
            [126.9, 35.1],
            [126.91, 35.11],
          ],
        },
        startedAt: '2026-10-07T00:00:20Z',
        endedAt: '2026-10-07T00:00:22Z',
      },
    ],
  };
  const earlier: LoadedSearchPath = {
    ...latest,
    version: '1',
    baselineVersion: '1',
    segmentsProgress: { beforeStartPointOrder: 0, completed: true },
    segments: [{ ...latest.segments[0], id: 'segment-1', version: '1', startPointOrder: 0, endPointOrder: 1 }],
  };
  client.setQueryData(searchPathPagesKey('incident-1', ['op-1']), createSearchPathPages([latest]));
  client.setQueryData(searchPathPagesKey('incident-1', ['op-1', 'op-2']), {
    ...createSearchPathPages([earlier]),
    hasMoreSegments: false,
    hasMoreChanges: false,
  });
  const post = vi.spyOn(apiClient, 'post').mockImplementation(() => new Promise(() => {}));
  // when: 현재 차수 범위로 진입해 다른 범위의 캐시를 함께 활용한다.
  const { result } = renderPaths();
  // then: 두 구간은 보존하되, 아직 없는 중간 구간을 완료한 것으로 처리하지 않는다.
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
  expect(post.mock.calls[0][0]).toContain('/segments/query');
  expect(post.mock.calls[0][1]).toMatchObject({
    paths: [
      {
        id: 'path-1',
        segmentsProgress: { beforeStartPointOrder: 8, completed: false },
      },
    ],
  });
  expect(result.current.data?.paths[0].baselineVersion).toBe('5');
  expect(result.current.data?.paths[0].segments.map((segment) => segment.id)).toEqual(['segment-1', 'segment-5']);
});

test('원본 좌표가 하나인 구간을 받으면, 지도 자료를 만들고 후속 변경 조회를 완료한다', async () => {
  // given: 서버는 원본 순번은 유지하고 같은 점 두 개로 LineString을 표현한다.
  const path = row({
    segments: [
      {
        id: 'segment-1',
        version: '1',
        startPointOrder: 3,
        endPointOrder: 3,
        movementType: 'UNKNOWN',
        geometry: {
          type: 'LineString',
          coordinates: [
            [126.9, 35.1],
            [126.9, 35.1],
          ],
        },
        startedAt: '2026-10-07T00:00:00Z',
        endedAt: '2026-10-07T00:00:00Z',
      },
    ],
    segmentsProgress: { beforeStartPointOrder: 3, completed: true },
  });
  const post = vi
    .spyOn(apiClient, 'post')
    .mockResolvedValueOnce(page([path]))
    .mockResolvedValue(page());
  // when: 실제 조회 훅으로 구간 응답을 받아 지도 입력으로 변환한다.
  const { result } = renderPaths();
  await waitFor(() => expect(result.current.isFetching).toBe(false));
  const paths = createBoardMovementPaths({
    incidentId: 'incident-1',
    serverTs: '2026-10-07T00:00:00Z',
    activeOpId: 'op-1',
    slots: { path: result.current.data?.paths },
  });
  // then: 형식 오류로 멈추지 않고 원본 순번과 위치를 보존한다.
  expect(result.current.data?.error).toBeNull();
  expect(result.current.data?.hasMoreChanges).toBe(false);
  expect(result.current.data?.paths[0].segments[0]).toMatchObject({ startPointOrder: 3, endPointOrder: 3 });
  expect(paths).toHaveLength(1);
  expect(paths[0].coordinates).toEqual([
    [126.9, 35.1],
    [126.9, 35.1],
  ]);
  expect(post.mock.calls[1][0]).toContain('/changes/query');
});

test('이력 조회 중 변경 신호가 몰리면, 한 요청씩 이력과 변경분을 번갈아 받는다', async () => {
  // given: 서버 응답을 직접 완료할 때까지 요청이 진행 중이다.
  const responses: Array<(value: SearchPathPage) => void> = [];
  const post = vi.spyOn(apiClient, 'post').mockImplementation(() => new Promise((resolve) => responses.push(resolve)));
  const { result } = renderPaths();
  // when: 첫 페이지를 받기 전 변경 신호가 50개 도착한다.
  act(() => {
    for (let i = 0; i < 50; i++) refreshSearchPathPages(client, 'incident-1');
  });
  expect(post).toHaveBeenCalledTimes(1);
  await act(async () =>
    responses[0](page([row({ segmentsProgress: { beforeStartPointOrder: 6, completed: false } })], true)),
  );
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  expect(post.mock.calls[1][0]).toContain('/changes/query');
  await act(async () =>
    responses[1](
      page([
        row({
          changesProgress: { appliedVersion: '1', targetVersion: '1', beforeStartPointOrder: null, completed: true },
        }),
      ]),
    ),
  );
  await waitFor(() => expect(post).toHaveBeenCalledTimes(3));
  expect(post.mock.calls[2][0]).toContain('/segments/query');
  await act(async () => responses[2](page([row({ baselineVersion: undefined })])));
  // then: 모든 이력 완료와 변경 완료를 각각 유지하고 추가 요청은 쌓이지 않는다.
  await waitFor(() => expect(result.current.isFetching).toBe(false));
  expect(result.current.data?.hasMoreSegments).toBe(false);
  expect(result.current.data?.paths[0].changesProgress?.appliedVersion).toBe('1');
  expect(post).toHaveBeenCalledTimes(3);
});

test('화면 두 개가 같은 범위를 조회하면, 요청을 공유하고 마지막 화면 이탈 때 취소한다', async () => {
  // given: 한 요청을 두 화면이 공유한다.
  const post = vi.spyOn(apiClient, 'post').mockImplementation(() => new Promise(() => {}));
  const first = renderPaths();
  const second = renderPaths();
  expect(post).toHaveBeenCalledTimes(1);
  const signal = post.mock.calls[0][2]?.signal;
  // when: 한 화면만 나간다.
  first.unmount();
  // then: 남은 화면은 요청을 유지하고 마지막 이탈 때만 중단한다.
  expect(signal?.aborted).toBe(false);
  second.unmount();
  expect(signal?.aborted).toBe(true);
});

test('권한을 잃으면, 진행 중 조회를 취소하고 받은 위치를 지운다', async () => {
  const post = vi
    .spyOn(apiClient, 'post')
    .mockResolvedValueOnce(page([row()]))
    .mockImplementation(() => new Promise(() => {}));
  const { result } = renderPaths();
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  const signal = post.mock.calls[1][2]?.signal;
  act(() => restrictSearchPathPages(client, 'incident-1'));
  await waitFor(() => expect(result.current.data?.restricted).toBe(true));
  expect(result.current.data?.paths).toEqual([]);
  expect(signal?.aborted).toBe(true);
  refreshSearchPathPages(client, 'incident-1');
  expect(post).toHaveBeenCalledTimes(2);
});

test('차수 선택을 바꾸면, 공통 차수의 진행은 유지하고 새 순환을 시작한다', async () => {
  const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url) =>
    url.includes('/segments/')
      ? page([row()])
      : page([
          row({
            changesProgress: {
              appliedVersion: '1',
              targetVersion: '1',
              beforeStartPointOrder: null,
              completed: true,
            },
          }),
        ]),
  );
  const { result, rerender } = renderPaths();
  await waitFor(() => expect(result.current.isFetching).toBe(false));
  post.mockImplementation(() => new Promise(() => {}));
  rerender({ ids: ['op-1', 'op-2'] });
  await waitFor(() => expect(post).toHaveBeenCalledTimes(3));
  expect(post.mock.calls[2][1]).toMatchObject({
    opIds: ['op-1', 'op-2'],
    nextSearchPathId: null,
    paths: [{ id: 'path-1', segmentsProgress: { beforeStartPointOrder: null, completed: true } }],
  });
});

test('일시적인 조회 실패는, 이벤트가 없어도 기다린 뒤 재시도한다', async () => {
  vi.useFakeTimers();
  vi.spyOn(Math, 'random').mockReturnValue(0);
  const post = vi
    .spyOn(apiClient, 'post')
    .mockRejectedValueOnce(new ApiHttpError(503, 'unavailable', {}))
    .mockResolvedValue(page());
  const { result, unmount } = renderPaths();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0);
  });
  expect(result.current.data?.error).toBe('unavailable');
  act(() => refreshSearchPathPages(client, 'incident-1'));
  expect(post).toHaveBeenCalledTimes(1);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(999);
  });
  expect(post).toHaveBeenCalledTimes(1);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2);
  });
  expect(post).toHaveBeenCalledTimes(3); // 이력 재시도 성공 후 변경분 확인
  expect(result.current.data?.error).toBeNull();
  unmount();
});

test('이전에 방문한 차수 범위로 돌아와도, 계속 선택한 경로의 최신 구간과 진행을 유지한다', async () => {
  // given: 1차를 조회한 뒤 1·2차 화면에서 같은 1차 경로의 버전 3을 받았다.
  const post = vi
    .spyOn(apiClient, 'post')
    .mockResolvedValueOnce(page([row()]))
    .mockResolvedValue(page());
  const view = renderPaths(['op-1']);
  await waitFor(() => expect(view.result.current.isFetching).toBe(false));
  const corrected = row({
    version: '3',
    changesProgress: {
      appliedVersion: '3',
      targetVersion: '3',
      beforeStartPointOrder: 0,
      completed: true,
    },
    segments: [
      {
        id: 'segment-1',
        version: '2',
        startPointOrder: 0,
        endPointOrder: 1,
        movementType: 'VEHICLE',
        geometry: {
          type: 'LineString',
          coordinates: [
            [127, 35],
            [128, 36],
          ],
        },
        startedAt: '2026-10-07T00:00:00Z',
        endedAt: '2026-10-07T00:00:05Z',
      },
    ],
  });
  post.mockResolvedValueOnce(page()).mockResolvedValueOnce(page([corrected]));
  view.rerender({ ids: ['op-1', 'op-2'] });
  await waitFor(() => expect(view.result.current.data?.paths[0].version).toBe('3'));
  await waitFor(() => expect(view.result.current.isFetching).toBe(false));

  // when: 예전 1차 캐시가 있는 범위로 돌아오고 새 응답은 보류한다.
  post.mockImplementation(() => new Promise(() => {}));
  view.rerender({ ids: ['op-1'] });

  // then: 이전 캐시가 최신 구간을 되돌리지 않고 완료 버전 3부터 변경을 확인한다.
  await waitFor(() => expect(view.result.current.data?.paths[0].version).toBe('3'));
  expect(view.result.current.data?.paths[0].segments).toEqual(corrected.segments);
  expect(post.mock.lastCall?.[0]).toContain('/changes/query');
  expect(post.mock.lastCall?.[1]).toMatchObject({
    paths: [
      {
        id: 'path-1',
        changesProgress: {
          appliedVersion: '3',
          completed: true,
        },
      },
    ],
  });
});

test('다른 화면의 좁은 조회 범위가 갱신돼도, 현재 화면의 경로와 완료한 진행을 유지한다', async () => {
  // given: 1·2차 화면을 모두 받은 뒤 1차만 보는 화면을 추가로 연다.
  const clock = vi.spyOn(Date, 'now').mockReturnValue(1000);
  const firstPath = row();
  const secondPath = row({ id: 'path-2', opId: 'op-2' });
  const post = vi.spyOn(apiClient, 'post').mockImplementation(async (url, body) => {
    const request = body as { opIds: string[] };
    const paths = request.opIds.includes('op-2') ? [firstPath, secondPath] : [firstPath];
    return page(
      paths.map((path) =>
        url.includes('/changes/')
          ? {
              ...path,
              changesProgress: {
                appliedVersion: '1',
                targetVersion: '1',
                beforeStartPointOrder: null,
                completed: true,
              },
            }
          : path,
      ),
    );
  });
  const bothPeriods = renderPaths(['op-1', 'op-2']);
  await waitFor(() => expect(bothPeriods.result.current.isFetching).toBe(false));
  clock.mockReturnValue(2000);
  const firstPeriod = renderPaths(['op-1']);
  await waitFor(() => expect(firstPeriod.result.current.isFetching).toBe(false));

  // when: 넓은 범위의 재조회 응답이 아직 오지 않았다.
  post.mockImplementation(() => new Promise(() => {}));
  act(() => {
    void bothPeriods.result.current.refetch();
  });
  await waitFor(() => expect(bothPeriods.result.current.isFetching).toBe(true));

  // then: 2차 자료를 버리지 않으며 완료한 이력 대신 두 경로의 변경분부터 확인한다.
  expect(bothPeriods.result.current.data?.paths.map((path) => path.id)).toEqual(['path-1', 'path-2']);
  expect(post.mock.lastCall?.[0]).toContain('/changes/query');
  expect(post.mock.lastCall?.[1]).toMatchObject({
    paths: [
      { id: 'path-1', changesProgress: { appliedVersion: '1', completed: true } },
      { id: 'path-2', changesProgress: { appliedVersion: '1', completed: true } },
    ],
  });
});

test('숨김 중 재시도 대기시간이 지나면, 복귀 후 재시도하고 화면 이탈 때 타이머를 정리한다', async () => {
  vi.useFakeTimers();
  vi.spyOn(Math, 'random').mockReturnValue(0);
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
  const post = vi.spyOn(apiClient, 'post').mockRejectedValue(new ApiHttpError(503, 'unavailable', {}));
  const { unmount } = renderPaths();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5000);
  });
  expect(post).toHaveBeenCalledTimes(1);
  visibility.mockReturnValue('visible');
  await act(async () => {
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
  });
  expect(post).toHaveBeenCalledTimes(2);
  unmount();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(30000);
  });
  expect(post).toHaveBeenCalledTimes(2);
});

test('재시도 가능한 오류만 반복하며, 서버 안내에 기존 무작위 대기를 더한다', () => {
  expect(pathReadRetryDelay(1, new ApiHttpError(429, 'busy', {}, '60'), 0.5)).toBe(61500);
  expect(pathReadRetryDelay(5, new Error(), 1)).toBe(30000);
  expect(isRetryablePathRead(new ApiNetworkError(new TypeError()))).toBe(true);
  expect(isRetryablePathRead(new ApiHttpError(401, 'unauthorized', {}))).toBe(false);
  expect(isRetryablePathRead(new ApiNetworkError(new DOMException('', 'AbortError')))).toBe(false);
  expect(isRetryablePathRead(new SyntaxError())).toBe(false);
});

test('브라우저가 오프라인이라고 판단해도, 실제 HTTP 조회는 시도한다', async () => {
  // given: 브라우저의 연결 판정이 오프라인이다.
  onlineManager.setOnline(false);
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(page());
  // when: 상황판 경로를 조회한다.
  const { result } = renderPaths();
  // then: 판정만으로 요청을 보류하지 않고 실제 성공을 반영한다.
  await waitFor(() => expect(result.current.isFetching).toBe(false));
  expect(post).toHaveBeenCalledTimes(2);
});

test('잘못된 진행 정보가 거부되면, 이벤트로 자동 초기화하거나 반복 요청하지 않는다', async () => {
  const post = vi.spyOn(apiClient, 'post').mockRejectedValue(new ApiHttpError(400, 'invalid_search_path_query', {}));
  const { result } = renderPaths();
  await waitFor(() => expect(result.current.isError).toBe(true));
  act(() => refreshSearchPathPages(client, 'incident-1'));
  expect(result.current.data?.error).toBe('invalid_search_path_query');
  expect(post).toHaveBeenCalledTimes(1);
});
