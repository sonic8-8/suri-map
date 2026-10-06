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
const runningQueries = new WeakMap<QueryClient, Map<string, { dirty: boolean }>>();

export function refreshSearchPathPages(client: QueryClient, incidentId?: string) {
  for (const query of client
    .getQueryCache()
    .findAll({ queryKey: incidentId ? ['searchPathPages', incidentId] : ['searchPathPages'], type: 'active' })) {
    const state = query.state.data as SearchPathPages | undefined;
    if (state?.restricted) continue;
    const running = runningQueries.get(client)?.get(query.queryHash);
    if (running) running.dirty = true;
    else if (!state?.error)
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
    staleTime: 0,
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
      const runners = runningQueries.get(client) ?? new Map<string, { dirty: boolean }>();
      runningQueries.set(client, runners);
      const runner = { dirty: true };
      runners.set(query.queryHash, runner);
      // 차수 선택 변경 직후에도 공통 경로를 지우지 않고 표시한다.
      client.setQueryData(key, state);
      let preferChanges = false;
      let failures = 0;
      try {
        while (state.hasMoreSegments || state.hasMoreChanges || runner.dirty) {
          signal.throwIfAborted();
          const changePending = state.hasMoreChanges || runner.dirty;
          const kind: PathPageKind =
            changePending && (preferChanges || !state.hasMoreSegments) ? 'changes' : 'segments';
          if (kind === 'changes') runner.dirty = false;
          const request = createSearchPathPageRequest(state, key[2], kind);
          const requestId = crypto.randomUUID();
          readOptions.recordMeasurement('path_page_started', { incidentId, requestId, kind });
          try {
            const value = await apiClient.post<unknown>(
              `/incidents/${encodeURIComponent(incidentId)}/board/search-paths/${kind}/query`,
              request,
              { signal },
            );
            signal.throwIfAborted();
            const page = parseSearchPathPage(value, kind, key[2]);
            state = mergeSearchPathPage(state, page, kind);
            client.setQueryData(key, state);
            failures = 0;
            preferChanges = kind === 'segments';
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
              restricted,
              paths: restricted ? [] : state.paths,
            };
            client.setQueryData(key, state);
            readOptions.recordMeasurement('path_page_failed', { incidentId, requestId, kind });
            if (!readOptions.isRetryable(error)) throw error;
            failures += 1;
            if (kind === 'changes') runner.dirty = true;
            await readOptions.waitForRetry(readOptions.retryDelay(failures, error), signal);
          }
        }
        return state;
      } finally {
        if (runners.get(query.queryHash) === runner) runners.delete(query.queryHash);
      }
    },
  });
}
