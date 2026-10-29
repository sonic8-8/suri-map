import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { apiClient, ApiHttpError } from '../../../shared/api';
import {
  createSearchPathPages,
  createSearchPathPageRequest,
  mergeSearchPathPage,
  mergeCachedSearchPaths,
  parseSearchPathPage,
  type PathPageKind,
  type SearchPathPages,
} from '../model/searchPathPages';

type SearchPathPageReadOptions = {
  isRetryable: (error: unknown) => boolean;
  retryDelay: (failures: number, error: unknown) => number;
  waitForRetry: (delay: number, signal: AbortSignal) => Promise<void>;
  recordMeasurement: (stage: string, details: Record<string, unknown>) => void;
};

export const searchPathPagesKey = (incidentId: string, opIds: readonly string[]) =>
  ['searchPathPages', incidentId, [...new Set(opIds)].sort()] as const;

// 데이터는 Query cache에만 둔다. 이 표에는 진행 중 요청을 합칠 신호만 보관한다.
type PathPageRunner = { dirty: boolean; recordMeasurement: SearchPathPageReadOptions['recordMeasurement'] };
const runningQueries = new WeakMap<QueryClient, Map<string, PathPageRunner>>();

export function refreshSearchPathPages(client: QueryClient, incidentId?: string) {
  for (const query of client
    .getQueryCache()
    .findAll({ queryKey: incidentId ? ['searchPathPages', incidentId] : ['searchPathPages'], type: 'active' })) {
    const state = query.state.data as SearchPathPages | undefined;
    if (state?.restricted) continue;
    const running = runningQueries.get(client)?.get(query.queryHash);
    if (running) {
      running.dirty = true;
      running.recordMeasurement('path_refresh_requested', {
        incidentId: query.queryKey[1],
        queryScope: query.queryHash,
        dirty: true,
      });
    } else if (!state?.error)
      void client.refetchQueries({ queryKey: query.queryKey, exact: true }, { cancelRefetch: false });
  }
}

export function restrictSearchPathPages(client: QueryClient, incidentId: string) {
  const filter = { queryKey: ['searchPathPages', incidentId] };
  void client.cancelQueries(filter);
  client.setQueriesData<SearchPathPages>(filter, () => ({
    ...createSearchPathPages(),
    paths: [],
    restricted: true,
    hasMoreSegments: false,
    hasMoreChanges: false,
  }));
}

export function useSearchPathPages(
  incidentId: string,
  opIds: readonly string[],
  enabled: boolean,
  readOptions: SearchPathPageReadOptions,
) {
  const client = useQueryClient();
  const key = searchPathPagesKey(incidentId, opIds);
  return useQuery({
    queryKey: key,
    enabled: enabled && Boolean(incidentId),
    networkMode: 'always',
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: true,
    // 캐시된 범위로 돌아올 때도 다른 화면에서 진행한 공통 경로를 반영하고 확인한다.
    // 0이면 먼저 읽은 Date.now()(저장 시각)보다 나중에 읽은 Date.now()가 작아질 때 방금 받은 데이터로 판정되므로, Date.now() 값과 관계없이 항상 오래된 데이터로 둔다.
    staleTime: -Infinity,
    queryFn: async ({ signal }) => {
      const query = client.getQueryCache().find({ queryKey: key, exact: true });
      if (!query) throw new Error('missing_search_path_query');
      let state = client.getQueryData<SearchPathPages>(key) ?? createSearchPathPages();
      for (const candidate of client.getQueryCache().findAll({ queryKey: ['searchPathPages', incidentId] })) {
        if (candidate.queryHash === query.queryHash) continue;
        const cached = candidate.state.data as SearchPathPages | undefined;
        if (!cached) continue;
        if (cached.restricted) {
          state = { ...state, paths: [], restricted: true };
          break;
        }
        state = mergeCachedSearchPaths(
          state,
          cached.paths.filter((path) => opIds.includes(path.opId)),
        );
      }
      if (
        state.restricted ||
        state.error === 'invalid_search_path_query' ||
        state.error === 'invalid_search_path_response'
      )
        return state;
      const runners = runningQueries.get(client) ?? new Map<string, PathPageRunner>();
      runningQueries.set(client, runners);
      const runner = { dirty: true, recordMeasurement: readOptions.recordMeasurement };
      runners.set(query.queryHash, runner);
      // 차수 선택 변경 직후에도 공통 경로를 지우지 않고 표시한다.
      state = client.setQueryData<SearchPathPages>(key, state) ?? state;
      // 최초 이력 조회로 기준 버전을 확보한 뒤 변경분에 최대 두 번 연속 차례를 준다.
      let consecutiveChanges = 2;
      const failures: Record<PathPageKind, number> = { segments: 0, changes: 0 };
      let failedKind: PathPageKind | null = null;
      try {
        while (state.hasMoreSegments || state.hasMoreChanges || runner.dirty) {
          signal.throwIfAborted();
          const changePending = state.hasMoreChanges || runner.dirty;
          let kind: PathPageKind =
            changePending && (consecutiveChanges < 2 || !state.hasMoreSegments) ? 'changes' : 'segments';
          // 재시도 대기를 마쳤다면 다른 조회에도 차례를 주되, 변경 추적 기준은 먼저 확보한다.
          if (failedKind === 'changes' && state.hasMoreSegments) kind = 'segments';
          if (failedKind === 'segments' && changePending && state.paths.some((path) => path.baselineVersion !== null))
            kind = 'changes';
          const dirtyAtSelection = runner.dirty;
          const failedKindAtSelection = failedKind;
          failedKind = null;
          if (kind === 'changes') runner.dirty = false;
          const request = createSearchPathPageRequest(state, key[2], kind);
          const requestId = crypto.randomUUID();
          readOptions.recordMeasurement('path_page_started', {
            incidentId,
            requestId,
            kind,
            queryScope: query.queryHash,
            dirty: dirtyAtSelection,
            hasMoreSegments: state.hasMoreSegments,
            hasMoreChanges: state.hasMoreChanges,
            consecutiveChanges,
            failedKind: failedKindAtSelection,
          });
          try {
            const value = await apiClient.post<unknown>(
              `/incidents/${encodeURIComponent(incidentId)}/board/search-paths/${kind}/query`,
              request,
              { signal },
            );
            signal.throwIfAborted();
            // API client가 본문 JSON을 읽은 직후다. 아래 경로 파싱·병합 시간과 구분한다.
            readOptions.recordMeasurement('path_page_response_received', { incidentId, requestId, kind });
            const page = parseSearchPathPage(value, kind, key[2]);
            state = mergeSearchPathPage(state, page, kind);
            // 캐시가 재사용한 객체로 이어가야 다음 페이지에서 변경 없는 경로의 재비교를 피한다.
            state = client.setQueryData<SearchPathPages>(key, state) ?? state;
            failures[kind] = 0;
            consecutiveChanges = kind === 'changes' ? consecutiveChanges + 1 : 0;
            readOptions.recordMeasurement('path_page_completed', {
              incidentId,
              requestId,
              kind,
              hasMore: page.hasMore,
              paths: page.paths.map((path) => ({
                id: path.id,
                version: path.version,
                segmentCount: path.segments.length,
              })),
            });
          } catch (error) {
            signal.throwIfAborted();
            const restricted =
              error instanceof ApiHttpError && ([401, 403].includes(error.status) || error.code === 'incident_closed');
            state = {
              ...state,
              error: error instanceof Error ? error.message : 'path_query_failed',
              pageErrors: { ...state.pageErrors, [kind]: error instanceof Error ? error.message : 'path_query_failed' },
              restricted,
              paths: restricted ? [] : state.paths,
            };
            state = client.setQueryData<SearchPathPages>(key, state) ?? state;
            readOptions.recordMeasurement('path_page_failed', { incidentId, requestId, kind });
            if (!readOptions.isRetryable(error)) throw error;
            failures[kind] += 1;
            failedKind = kind;
            if (kind === 'changes') runner.dirty = true;
            await readOptions.waitForRetry(readOptions.retryDelay(failures[kind], error), signal);
          }
        }
        return state;
      } finally {
        if (runners.get(query.queryHash) === runner) runners.delete(query.queryHash);
      }
    },
  });
}
