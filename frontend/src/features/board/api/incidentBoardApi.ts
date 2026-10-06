import { useQuery, type Query, type QueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';
import { isBoardMeasurementEnabled, recordBoardMeasurement } from '../model/boardMeasurement';
import { isRetryableBoardRead, boardReadRetryDelay, waitForBoardReadRetry } from '../model/boardReadRecovery';

export type BoardSlotName =
  | 'overall_search_area'
  | 'area'
  | 'path'
  | 'police_phone_freshness'
  | 'marker'
  | 'marker_notification'
  | 'package_badge'
  | 'op_toggle'
  | 'op_history'
  | 'handover_memo'
  | 'handover_status'
  | 'search_history_summary'
  | 'incident_terminal';

export interface BoardSlotRow {
  readonly slot: BoardSlotName;
  readonly id: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly slotSources: readonly string[];
  readonly sourceVersions: Record<string, number>;
  readonly sourceHashes: Record<string, string>;
}

const boardSlotRegistry: readonly BoardSlotName[] = [
  'overall_search_area',
  'area',
  'path',
  'police_phone_freshness',
  'marker',
  'marker_notification',
  'package_badge',
  'op_toggle',
  'op_history',
  'handover_memo',
  'handover_status',
  'search_history_summary',
  'incident_terminal',
];

export const boardSlotsWithoutPaths = boardSlotRegistry.filter((slot) => slot !== 'path');

export interface BoardSourceRowCursor {
  readonly id: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
}

export type IncidentBoardSlotPayload = BoardSourceRowCursor & Record<string, unknown>;

export type IncidentBoardSlotValue = IncidentBoardSlotPayload | readonly IncidentBoardSlotPayload[] | null | undefined;

export interface IncidentBoardResponse {
  readonly incidentId: string;
  readonly boardResponseVersion: number;
  readonly serverTs: string;
  readonly activeOpId: string | null;
  readonly selectedOpIds: readonly string[];
  readonly geometryHash: string;
  readonly slots: Partial<Record<BoardSlotName, IncidentBoardSlotValue>>;
  readonly slotSources: Partial<Record<BoardSlotName, readonly BoardSourceRowCursor[]>>;
  readonly sourceVersions: Partial<Record<BoardSlotName, readonly BoardSourceRowCursor[]>>;
  readonly sourceHashes: Partial<Record<BoardSlotName, readonly BoardSourceRowCursor[]>>;
}

export type SituationBoardResponseDto = {
  incidentId: string;
  boardResponseVersion: number;
  serverTs: string;
  activeOpId: string | null;
  selectedOpIds: string[];
  slots: Record<string, unknown>;
  sourceVersions: Record<string, number>;
  geometryHash: string | null;
  sourceHashes: Record<string, string>;
  slotSources: Record<string, unknown[]>;
};

export interface IncidentBoardQuery {
  readonly incidentId: string | null | undefined;
  readonly opIds?: readonly string[];
  readonly includeSlots?: readonly BoardSlotName[];
  readonly sinceVersion?: number;
}

export interface IncidentBoardApi {
  fetchIncidentBoard(query: IncidentBoardQuery, signal?: AbortSignal): Promise<IncidentBoardResponse>;
}

type IncidentBoardQueryOptions = {
  refetchInterval?: number | false;
  recoverReads?: boolean;
};

export type IncidentBoardSlotRow = IncidentBoardSlotPayload & {
  readonly slot: BoardSlotName;
  readonly sourceId: string;
  readonly sourceResponseId: string;
};

export interface IncidentBoardViewModel {
  readonly response: IncidentBoardResponse;
  readonly incidentId: string;
  readonly boardResponseVersion: number;
  readonly serverTs: string;
  readonly activeOpId: string | null;
  readonly selectedOpIds: readonly string[];
  readonly geometryHash: string;
  readonly rowsBySlot: Record<BoardSlotName, readonly IncidentBoardSlotRow[]>;
  readonly cursorsBySlot: Record<BoardSlotName, readonly BoardSourceRowCursor[]>;
  readonly hostRows: readonly BoardSlotRow[];
}

export const incidentBoardQueryKeys = {
  all: ['incidentBoard'] as const,
  detail: (query: IncidentBoardQuery) =>
    [
      ...incidentBoardQueryKeys.all,
      'detail',
      query.incidentId ?? '',
      incidentBoardQueryKeyParams(query),
    ] as const,
};

export function createIncidentBoardApi(client: ApiClient = apiClient): IncidentBoardApi {
  return {
    fetchIncidentBoard: async (query, signal) => {
      const path = `/incidents/${requireIncidentId(query.incidentId)}/board`;
      const requestId = isBoardMeasurementEnabled() ? crypto.randomUUID() : null;
      if (requestId) recordBoardMeasurement('board_read_started', { requestId, incidentId: query.incidentId });
      try {
        const response = await client.get<IncidentBoardResponse>(path, { query: toApiQuery(query), signal });
        if (requestId) {
          recordBoardMeasurement('board_read_completed', {
            requestId, incidentId: response.incidentId, boardResponseVersion: response.boardResponseVersion,
            rows: (['path', 'marker'] as const).flatMap((slot) => normalizeSlotRows(slot, response.slots[slot])
              .map((row) => ({ slot, id: row.id, version: row.version, latestEventId: row.latestEventId }))),
          });
        }
        return response;
      } catch (error) {
        if (requestId) recordBoardMeasurement('board_read_failed', { requestId, incidentId: query.incidentId });
        throw error;
      }
    },
  };
}

export const incidentBoardApi = createIncidentBoardApi();

const pendingBoardRefreshes = new WeakMap<Query, { completion: Promise<void>; followup?: Promise<void> }>();

export function refreshIncidentBoards(queryClient: QueryClient, query?: IncidentBoardQuery): Promise<void> {
  // 사건 단위 갱신은 includeSlots·선택 차수가 다른 실제 소비 화면도 포함한다.
  const queryKey = query ? [...incidentBoardQueryKeys.all, 'detail', query.incidentId ?? ''] : incidentBoardQueryKeys.all;
  // 사용하지 않는 조회는 오래된 상태로만 표시하고, 열린 화면의 조회만 실행한다.
  void queryClient.invalidateQueries({ queryKey, refetchType: 'none' });
  const completions = queryClient.getQueryCache().findAll({ queryKey, type: 'active' }).map((boardQuery) =>
    // 오류는 Query 상태로 화면에 표시한다. 실패 직후 후속 조회를 반복하지 않는다.
    refreshIncidentBoardQuery(queryClient, boardQuery).catch(() => undefined),
  );
  return Promise.all(completions).then(() => undefined);
}

export function useIncidentBoardQuery(
  query: IncidentBoardQuery,
  api: IncidentBoardApi = incidentBoardApi,
  options: IncidentBoardQueryOptions = {},
) {
  return useQuery({
    queryKey: incidentBoardQueryKeys.detail(query),
    queryFn: async ({ signal, client, queryKey }) => {
      const failures = client.getQueryState(queryKey)?.fetchFailureCount ?? 0;
      if (options.recoverReads && failures > 0) await waitForBoardReadRetry(0, signal);
      return api.fetchIncidentBoard({ ...query, incidentId: query.incidentId ?? '' }, signal);
    },
    enabled: Boolean(query.incidentId),
    placeholderData: (previousData) =>
      query.incidentId && previousData?.incidentId === query.incidentId ? previousData : undefined,
    retry: options.recoverReads ? (_failureCount, error) => isRetryableBoardRead(error) : false,
    retryDelay: (failureCount, error) => boardReadRetryDelay(failureCount + 1, error),
    networkMode: options.recoverReads ? 'always' : 'online',
    refetchInterval: options.refetchInterval,
  });
}

export function useIncidentBoard(query: IncidentBoardQuery, api: IncidentBoardApi = incidentBoardApi) {
  return useQuery({
    queryKey: incidentBoardQueryKeys.detail(query),
    queryFn: ({ signal }) => api.fetchIncidentBoard({ ...query, incidentId: query.incidentId ?? '' }, signal),
    enabled: Boolean(query.incidentId),
    placeholderData: (previousData) =>
      query.incidentId && previousData?.incidentId === query.incidentId ? previousData : undefined,
    retry: false,
    select: mapIncidentBoardResponse,
  });
}

export function mapIncidentBoardResponse(response: IncidentBoardResponse): IncidentBoardViewModel {
  const rowsBySlot = {} as Record<BoardSlotName, readonly IncidentBoardSlotRow[]>;
  const cursorsBySlot = {} as Record<BoardSlotName, readonly BoardSourceRowCursor[]>;
  const hostRows: BoardSlotRow[] = [];

  for (const slot of boardSlots()) {
    const rows = normalizeSlotRows(slot, response.slots[slot]);
    const cursors = normalizeCursors(response.slotSources[slot]);
    rowsBySlot[slot] = rows;
    cursorsBySlot[slot] = cursors;
    hostRows.push(toHostRow(slot, rows, cursors));
  }

  return {
    response,
    incidentId: response.incidentId,
    boardResponseVersion: response.boardResponseVersion,
    serverTs: response.serverTs,
    activeOpId: response.activeOpId,
    selectedOpIds: response.selectedOpIds,
    geometryHash: response.geometryHash,
    rowsBySlot,
    cursorsBySlot,
    hostRows,
  };
}

function refreshIncidentBoardQuery(queryClient: QueryClient, boardQuery: Query): Promise<void> {
  let pending = pendingBoardRefreshes.get(boardQuery);
  if (!pending) {
    const alreadyFetching = boardQuery.state.fetchStatus !== 'idle';
    const completion = queryClient.refetchQueries(
      { queryKey: boardQuery.queryKey, exact: true, type: 'active' },
      { cancelRefetch: false, throwOnError: true },
    ).then(async () => {
      // 오프라인 보류는 성공이 아니며, 취소를 이전 데이터 복원 성공으로 취급하지 않는다.
      await boardQuery.promise;
    });
    pending = { completion: completion.finally(() => pendingBoardRefreshes.delete(boardQuery)) };
    pendingBoardRefreshes.set(boardQuery, pending);
    if (!alreadyFetching) return pending.completion;
  }

  // 현재 요청 중 받은 여러 변경은 후속 요청 하나로 합친다.
  // 호출자는 자기 변경을 반영한 조회까지만 기다린다. 이후 변경이 저장 완료를 막지 않는다.
  pending.followup ??= pending.completion.then(() => {
    if (!boardQuery.isActive() || queryClient.getQueryCache().get(boardQuery.queryHash) !== boardQuery) return;
    return refreshIncidentBoardQuery(queryClient, boardQuery);
  });
  return pending.followup;
}

function toApiQuery(query: IncidentBoardQuery): ApiQuery {
  return {
    opIds: query.opIds,
    includeSlots: query.includeSlots,
    sinceVersion: query.sinceVersion,
  };
}

function requireIncidentId(incidentId: string | null | undefined): string {
  if (!incidentId) {
    throw new Error('incidentId is required');
  }
  return encodeURIComponent(incidentId);
}

function sortedValues<TValue extends string>(values: readonly TValue[] | undefined): readonly TValue[] {
  return [...(values ?? [])].sort();
}

function incidentBoardQueryKeyParams(query: IncidentBoardQuery) {
  return {
    includeSlots: sortedValues(query.includeSlots),
    opIds: sortedValues(query.opIds),
    sinceVersion: query.sinceVersion ?? null,
  };
}

function boardSlots(): readonly BoardSlotName[] {
  return boardSlotRegistry;
}

function normalizeSlotRows(slot: BoardSlotName, value: IncidentBoardSlotValue): readonly IncidentBoardSlotRow[] {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return values.filter(isBoardSlotPayload).map((row) => ({
    ...row,
    slot,
    sourceId: row.id,
    sourceResponseId: row.id,
  }));
}

function normalizeCursors(cursors: readonly BoardSourceRowCursor[] | undefined): readonly BoardSourceRowCursor[] {
  return (cursors ?? []).filter(isBoardSlotPayload);
}

function toHostRow(
  slot: BoardSlotName,
  rows: readonly IncidentBoardSlotRow[],
  cursors: readonly BoardSourceRowCursor[],
): BoardSlotRow {
  const primary = rows[0] ?? cursors[0] ?? emptyCursor(slot);
  return {
    slot,
    id: primary.id,
    status: primary.status,
    version: primary.version,
    sequence: primary.sequence,
    sourceSpec: primary.sourceSpec,
    sourceHash: primary.sourceHash,
    latestEventId: primary.latestEventId,
    slotSources: cursors.map((cursor) => cursor.id),
    sourceVersions: Object.fromEntries(cursors.map((cursor) => [cursor.id, cursor.version])),
    sourceHashes: Object.fromEntries(cursors.map((cursor) => [cursor.id, cursor.sourceHash])),
  };
}

function emptyCursor(slot: BoardSlotName): BoardSourceRowCursor {
  return {
    id: `${slot}:empty`,
    status: 'EMPTY',
    version: 0,
    sequence: 0,
    sourceSpec: 'S3-2',
    sourceHash: '',
    latestEventId: '',
  };
}

function isBoardSlotPayload(value: unknown): value is IncidentBoardSlotPayload {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    typeof value.id === 'string' &&
    'status' in value &&
    typeof value.status === 'string' &&
    'version' in value &&
    typeof value.version === 'number' &&
    'sequence' in value &&
    typeof value.sequence === 'number' &&
    'sourceSpec' in value &&
    typeof value.sourceSpec === 'string' &&
    'sourceHash' in value &&
    typeof value.sourceHash === 'string' &&
    'latestEventId' in value &&
    typeof value.latestEventId === 'string'
  );
}
