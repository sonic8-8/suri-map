import { useQuery } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';

export type BoardSlotName =
  | 'overall_search_area'
  | 'area'
  | 'path'
  | 'police_phone_freshness'
  | 'marker'
  | 'toast'
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
  'toast',
  'package_badge',
  'op_toggle',
  'op_history',
  'handover_memo',
  'handover_status',
  'search_history_summary',
  'incident_terminal',
];

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

export interface IncidentBoardQuery {
  readonly incidentId: string | null | undefined;
  readonly opIds?: readonly string[];
  readonly includeSlots?: readonly BoardSlotName[];
  readonly sinceVersion?: number;
}

export interface IncidentBoardApi {
  fetchIncidentBoard(query: IncidentBoardQuery): Promise<IncidentBoardResponse>;
}

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
    fetchIncidentBoard: (query) =>
      client.get<IncidentBoardResponse>(`/incidents/${requireIncidentId(query.incidentId)}/board`, {
        query: toApiQuery(query),
      }),
  };
}

export const incidentBoardApi = createIncidentBoardApi();

export function useIncidentBoardQuery(query: IncidentBoardQuery, api: IncidentBoardApi = incidentBoardApi) {
  return useQuery({
    queryKey: incidentBoardQueryKeys.detail(query),
    queryFn: () => api.fetchIncidentBoard({ ...query, incidentId: query.incidentId ?? '' }),
    enabled: Boolean(query.incidentId),
    placeholderData: (previousData) =>
      query.incidentId && previousData?.incidentId === query.incidentId ? previousData : undefined,
    retry: false,
  });
}

export function useIncidentBoard(query: IncidentBoardQuery, api: IncidentBoardApi = incidentBoardApi) {
  return useQuery({
    queryKey: incidentBoardQueryKeys.detail(query),
    queryFn: () => api.fetchIncidentBoard({ ...query, incidentId: query.incidentId ?? '' }),
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
