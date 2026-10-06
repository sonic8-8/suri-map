import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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

test('경로 조회가 권한 거부되면, 예전 위치 슬롯을 화면에서 제거한다', async () => {
  vi.spyOn(apiClient, 'get').mockResolvedValue({ ...metadata, slots: { marker: { id: 'marker-1' } } });
  vi.spyOn(apiClient, 'post').mockRejectedValue(new ApiHttpError(403, 'incident_access_denied', {}));
  const { result } = renderBoard();
  await waitFor(() => expect(result.current.isLocationRestricted).toBe(true));
  expect(result.current.data?.slots).not.toHaveProperty('marker');
  expect(createBoardMovementPaths(result.current.data ?? null)).toEqual([]);
});
