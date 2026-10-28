import { QueryClient, QueryClientProvider, onlineManager } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import type { ReactNode } from 'react';
import { apiClient, ApiHttpError } from '../../shared/api';
import { createBoardMovementPaths } from '../../shared/model/boardMapSlots';
import type { IncidentBoardResponse } from '../../features/board/api/incidentBoardApi';
import { refreshSituationBoards } from './refreshSituationBoards';
import { usePagedIncidentBoardQuery } from './usePagedIncidentBoardQuery';

let client: QueryClient;
beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity, staleTime: Infinity } } });
});
afterEach(() => {
  cleanup();
  client.clear();
  vi.restoreAllMocks();
  onlineManager.setOnline(true);
});
const metadata: IncidentBoardResponse = {
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
};
function renderBoard() {
  return renderHook(() => usePagedIncidentBoardQuery({ incidentId: 'incident-1' }), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}
const emptyPage = { paths: [], nextSearchPathId: null, hasMore: false };

test('상황판을 열면, 기존 API는 경로를 제외하고 새 API의 정상 빈 결과를 그대로 표시한다', async () => {
  // given: HTTP만 대체하고 실제 상황판·경로 조회 훅과 QueryClient를 연결한다.
  const get = vi.spyOn(apiClient, 'get').mockResolvedValue(metadata);
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(emptyPage);
  const { result } = renderBoard();
  // when: 이력·변경 조회를 완료한다.
  await waitFor(() => expect(result.current.pathLoading).toBe(false));
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  // then: 경로를 중복 조회하거나 fallback으로 채우지 않는다.
  expect(get.mock.calls[0][1]?.query?.includeSlots).not.toContain('path');
  expect(post.mock.calls[0][1]).toEqual({ opIds: ['op-1'], paths: [], nextSearchPathId: null });
  expect(result.current.data?.slots.path).toEqual([]);
  expect(createBoardMovementPaths(result.current.data ?? null)).toEqual([]);
});

test('한 페이지를 받으면, 전체 로딩 전에 지도 자료를 표시하고 부분 상태를 유지한다', async () => {
  vi.spyOn(apiClient, 'get').mockResolvedValue(metadata);
  const post = vi
    .spyOn(apiClient, 'post')
    .mockResolvedValueOnce({
      paths: [
        {
          id: 'path-1',
          accountId: 'account-1',
          opId: 'op-1',
          status: 'RECORDING',
          version: '9007199254740993',
          baselineVersion: '9007199254740993',
          segmentsProgress: { beforeStartPointOrder: 6, completed: false },
          segments: [
            {
              id: 'segment-1',
              version: '1',
              startPointOrder: 6,
              endPointOrder: 7,
              movementType: 'FOOT',
              geometry: {
                type: 'LineString',
                coordinates: [
                  [127, 35],
                  [128, 36],
                ],
              },
              startedAt: '2026-10-07T00:00:00Z',
              endedAt: null,
            },
          ],
        },
      ],
      nextSearchPathId: null,
      hasMore: true,
    })
    .mockImplementation(() => new Promise(() => {}));
  const { result } = renderBoard();
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  const paths = createBoardMovementPaths(result.current.data ?? null);
  expect(paths).toHaveLength(1);
  expect(paths[0].searchPathVersion).toBe('9007199254740993');
  expect(result.current.pathLoading).toBe(true);
  expect(result.current.pathSyncStatus?.label).toContain('일부만 표시');
});

test('SSE·저장 후 공통 재조회 요청은, 경로 변경분 API에도 전달된다', async () => {
  vi.spyOn(apiClient, 'get').mockResolvedValue(metadata);
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(emptyPage);
  const { result } = renderBoard();
  await waitFor(() => expect(result.current.paths.isFetching).toBe(false));
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  await act(async () => {
    await refreshSituationBoards(client, { incidentId: 'incident-1' });
  });
  await waitFor(() => expect(post).toHaveBeenCalledTimes(3));
  expect(post.mock.calls[2][0]).toContain('/changes/query');
});

test('최초 이력, 변경분, 새 구간 알림 뒤 조회가 모두 남지 않았을 때만, 경로 이력을 모두 받은 상태로 알린다', async () => {
  // given: 경로 응답을 하나씩 보류한다.
  vi.spyOn(apiClient, 'get').mockResolvedValue(metadata);
  const responses: Array<(value: unknown) => void> = [];
  vi.spyOn(apiClient, 'post').mockImplementation(() => new Promise((resolve) => responses.push(resolve)));
  const { result } = renderBoard();
  await waitFor(() => expect(responses).toHaveLength(1));

  // when: 최초 이력에 남은 페이지가 있다.
  await act(async () => responses[0]({ ...emptyPage, hasMore: true }));
  await waitFor(() => expect(responses).toHaveLength(2));
  // then: 아직 받는 중이다.
  expect(result.current.pathHistoryReceived).toBe(false);

  // when: 최초 이력과 변경분을 끝까지 받는다.
  await act(async () => responses[1](emptyPage));
  await waitFor(() => expect(responses).toHaveLength(3));
  expect(result.current.pathHistoryReceived).toBe(false);
  await act(async () => responses[2](emptyPage));
  // then: 모두 받은 상태가 된다.
  await waitFor(() => expect(result.current.pathHistoryReceived).toBe(true));

  // when: 새 구간 저장 알림을 받아 변경분을 다시 조회한다.
  await act(async () => {
    void refreshSituationBoards(client, { incidentId: 'incident-1' });
  });
  await waitFor(() => expect(responses).toHaveLength(4));
  // then: 그 변경분을 받을 때까지 다시 받는 중이다.
  expect(result.current.pathHistoryReceived).toBe(false);
  await act(async () => responses[3](emptyPage));
  await waitFor(() => expect(result.current.pathHistoryReceived).toBe(true));
});

test('변경분 조회 오류가 캐시에 남은 채 재연결로 Query가 success가 되어도, 경로 이력을 모두 받은 상태로 보지 않는다', async () => {
  // given: 이력을 모두 받은 뒤 새 구간 알림의 변경분 조회가 다시 시도하지 않는 오류로 끝났다.
  vi.spyOn(apiClient, 'get').mockResolvedValue(metadata);
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(emptyPage);
  const { result } = renderBoard();
  await waitFor(() => expect(result.current.pathHistoryReceived).toBe(true));
  post.mockResolvedValue({ paths: 'invalid_response' });
  await act(async () => {
    void refreshSituationBoards(client, { incidentId: 'incident-1' });
  });
  await waitFor(() => expect(result.current.paths.isError).toBe(true));
  const postsAfterFailure = post.mock.calls.length;

  // when: 네트워크가 다시 연결되어 HTTP 요청 없이 오류를 담은 캐시로 success가 된다.
  act(() => {
    onlineManager.setOnline(false);
    onlineManager.setOnline(true);
  });
  await waitFor(() => expect(result.current.paths.status).toBe('success'));
  await waitFor(() => expect(result.current.paths.isFetching).toBe(false));

  // then: 화면은 오류를 그대로 표시하고, render 때의 판정과 idle 때의 확인 모두 받은 상태가 아니다.
  expect(post.mock.calls.length).toBe(postsAfterFailure);
  expect(result.current.paths.data?.error).toBe('invalid_search_path_response');
  expect(result.current.pathSyncStatus?.tone).toBe('error');
  expect(result.current.pathHistoryReceived).toBe(false);
  expect(result.current.isPathHistoryStillReceived()).toBe(false);
});

test('경로 조회가 권한 거부되면, 예전 위치 슬롯을 화면에서 제거한다', async () => {
  vi.spyOn(apiClient, 'get').mockResolvedValue({ ...metadata, slots: { marker: { id: 'marker-1' } } });
  vi.spyOn(apiClient, 'post').mockRejectedValue(new ApiHttpError(403, 'incident_access_denied', {}));
  const { result } = renderBoard();
  await waitFor(() => expect(result.current.isLocationRestricted).toBe(true));
  expect(result.current.data?.slots).not.toHaveProperty('marker');
  expect(createBoardMovementPaths(result.current.data ?? null)).toEqual([]);
});

function renderBoardWithListOp(initialActiveOpId: string) {
  return renderHook(() => usePagedIncidentBoardQuery({ incidentId: 'incident-1' }, undefined, { initialActiveOpId }), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

test('목록에서 받은 진행 중 차수가 있으면, 상황판 응답 전에 경로 조회를 시작하고 같은 차수 응답이 오면 이어 쓴다', async () => {
  // given: 상황판 응답은 아직 오지 않았고, 목록에서 진행 중 차수를 받았다.
  let resolveBoard: (value: IncidentBoardResponse) => void = () => {};
  vi.spyOn(apiClient, 'get').mockReturnValue(new Promise((resolve) => { resolveBoard = resolve; }));
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(emptyPage);
  const { result } = renderBoardWithListOp('op-1');

  // when: 상황판 응답을 기다리지 않고 경로 이력 조회가 나간 뒤, 같은 차수의 상황판 응답이 온다.
  await waitFor(() => expect(post).toHaveBeenCalled());
  expect(post.mock.calls[0][0]).toContain('/segments/query');
  expect(post.mock.calls[0][1]).toEqual({ opIds: ['op-1'], paths: [], nextSearchPathId: null });
  await act(async () => resolveBoard(metadata));
  await waitFor(() => expect(result.current.pathLoading).toBe(false));

  // then: 같은 조회를 이어 써서 이력 조회를 다시 보내지 않는다.
  await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
  expect(post.mock.calls.filter(([url]) => String(url).includes('/segments/query'))).toHaveLength(1);
  expect(result.current.data?.slots.path).toEqual([]);
});

test('상황판 응답의 차수가 목록과 다르면, 상황판 차수로 경로를 다시 조회한다', async () => {
  // given: 목록을 연 뒤 진행 중 차수가 바뀌어 상황판은 다른 차수를 준다.
  vi.spyOn(apiClient, 'get').mockResolvedValue({ ...metadata, activeOpId: 'op-2', selectedOpIds: ['op-2'] });
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue(emptyPage);

  // when: 목록의 이전 차수로 상황판을 연다.
  renderBoardWithListOp('op-1');

  // then: 상황판이 준 차수로도 이력 조회를 보낸다.
  await waitFor(() =>
    expect(post.mock.calls.some(([url, body]) => String(url).includes('/segments/query')
      && JSON.stringify((body as { opIds: string[] }).opIds) === JSON.stringify(['op-2']))).toBe(true),
  );
});
