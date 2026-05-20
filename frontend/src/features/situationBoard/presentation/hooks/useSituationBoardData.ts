import { useEffect, useMemo, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { getStoredAccessToken } from '../../../../shared/api/client';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import {
  useIncidentBoardQuery,
  incidentBoardQueryKeys,
  type BoardSlotName,
} from '../../../board/api/incidentBoardApi';
import { openIncidentBoardEventStream } from '../../../board/api/incidentBoardEventStream';
import {
  createIncidentScopedFallbackBoard,
  type SearchAreaAssignedAccount,
  type SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import { toBoardRecentMarkers } from '../utils/markerBoardMapper';
import {
  assignRouteColorsToMovementPaths,
  createLegendItems,
  toMovementPaths,
} from '../utils/movementPathBoardMapper';
import {
  filterSituationBoardMarkersForMap,
  filterSituationBoardMovementPathsForMap,
  filterSituationBoardSearchAreaRowsForMap,
} from '../utils/opScopedMapRendering';
import { toOperationalPeriods } from '../utils/operationalPeriodBoardMapper';
import {
  buildFallbackSearchAreaTree,
  buildSearchAreaTree,
  toAssignmentsByAreaId,
  toSearchAreaDrafts,
  toSearchAreaRows,
} from '../utils/searchAreaBoardMapper';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../utils/incidentTerminalBoardMapper';

type SituationBoardDataState = {
  board: SituationBoardFallbackData;
  isLoading: boolean;
  isInitialLoading: boolean;
  isInitialLoadError: boolean;
  isInitialReconnecting: boolean;
  syncStatus: { label: string; tone: 'syncing' | 'stale' | 'error' } | null;
  apiBoard: SituationBoardResponseDto | null;
  isFallback: boolean;
  isOverallSearchAreaMissing: boolean;
  retryInitialLoad: () => void;
};

export function useSituationBoardData(
  incidentId: string,
  savedAreaDrafts: CompletedAreaDraft[],
  refreshVersion = 0,
): SituationBoardDataState {
  const queryClient = useQueryClient();
  const lastEventIdRef = useRef<string | null>(null);
  const prevRefreshVersionRef = useRef(refreshVersion);

  const fallbackBoard = useMemo(() => createIncidentScopedFallbackBoard(incidentId), [incidentId]);

  const boardQuery = useIncidentBoardQuery({ incidentId });
  const rawApiBoard = (boardQuery.data as unknown as SituationBoardResponseDto) ?? null;
  const currentApiBoard = useMemo<SituationBoardResponseDto | null>(() => {
    return rawApiBoard && rawApiBoard.incidentId === incidentId ? rawApiBoard : null;
  }, [incidentId, rawApiBoard]);
  const stableApiBoardRef = useRef<SituationBoardResponseDto | null>(null);
  const { apiBoard, hasBackfilledCriticalSlots } = useMemo(() => {
    const merged = mergeWithPreviousCriticalSlots(currentApiBoard, stableApiBoardRef.current);
    if (merged) {
      stableApiBoardRef.current = merged;
    }
    return {
      apiBoard: merged,
      hasBackfilledCriticalSlots: Boolean(currentApiBoard && merged && merged !== currentApiBoard),
    };
  }, [currentApiBoard]);
  const hasApiBoard = apiBoard !== null;
  const syncStatus = createBoardSyncStatus({
    hasApiBoard,
    hasBackfilledCriticalSlots,
    isError: boardQuery.isError,
    isFetching: boardQuery.isFetching,
  });
  const shouldSubscribeEvents = shouldSubscribeIncidentBoardEvents(apiBoard);

  // 외부 refreshVersion 변경 시 board 재조회 (구역 저장 등)
  useEffect(() => {
    if (prevRefreshVersionRef.current === refreshVersion) return;
    prevRefreshVersionRef.current = refreshVersion;
    void queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.all });
  }, [refreshVersion, queryClient]);

  // SSE: 도메인 이벤트 수신 시 board 재조회
  useEffect(() => {
    if (!incidentId || !hasApiBoard || !shouldSubscribeEvents) return;

    let cancelled = false;
    let activeSubscription: { close(): void } | null = null;

    const connect = () => {
      if (cancelled) return;

      const accessToken = getStoredAccessToken();

      const queryKey = incidentBoardQueryKeys.detail({ incidentId });

      const subscription = openIncidentBoardEventStream({
        incidentId,
        accessToken,
        lastEventId: lastEventIdRef.current,
        onEvent: (_event, meta) => {
          lastEventIdRef.current = meta.lastEventId;
          void queryClient.invalidateQueries({ queryKey });
        },
        onRefetchRequired: () => {
          lastEventIdRef.current = null;
          void queryClient.invalidateQueries({ queryKey });
        },
      });

      activeSubscription = subscription;

      // 연결 종료 시 재연결 (3초 후)
      void subscription.closed.then(() => {
        if (!cancelled) {
          setTimeout(connect, 3_000);
        }
      });
    };

    connect();

    return () => {
      cancelled = true;
      activeSubscription?.close();
    };
  }, [incidentId, queryClient, hasApiBoard, shouldSubscribeEvents]);

  useEffect(() => {
    stableApiBoardRef.current = null;
    lastEventIdRef.current = null;
  }, [incidentId]);

  const board = useMemo<SituationBoardFallbackData>(() => {
    const apiSearchAreaRows = apiBoard ? toSearchAreaRows(apiBoard) : [];
    const apiOperationalPeriods = apiBoard ? toOperationalPeriods(apiBoard, fallbackBoard.operationalPeriods) : [];
    const activeOperationalPeriodId =
      apiBoard?.activeOpId ??
      apiOperationalPeriods.find((operationalPeriod) => operationalPeriod.state === 'current')?.id ??
      fallbackBoard.operationalPeriods.find((operationalPeriod) => operationalPeriod.state === 'current')?.id ??
      null;
    const mapSearchAreaRows =
      apiBoard !== null
        ? filterSituationBoardSearchAreaRowsForMap(apiSearchAreaRows, activeOperationalPeriodId)
        : apiSearchAreaRows;
    const apiSearchAreaDrafts = toSearchAreaDrafts(mapSearchAreaRows);
    const apiAssignmentsByAreaId = apiBoard
      ? toAssignmentsByAreaId(apiBoard)
      : new Map<string, SearchAreaAssignedAccount[]>();
    const apiRecentMarkers = apiBoard ? toBoardRecentMarkers(apiBoard) : [];
    const apiMovementPaths = apiBoard ? toMovementPaths(apiBoard) : [];
    const searchAreaDrafts =
      apiBoard !== null
        ? apiSearchAreaDrafts
        : savedAreaDrafts.length > 0
          ? savedAreaDrafts
          : fallbackBoard.searchAreaDrafts;

    const searchAreaTree =
      mapSearchAreaRows.length > 0
        ? buildSearchAreaTree(fallbackBoard.searchAreaTree, mapSearchAreaRows, searchAreaDrafts)
        : buildFallbackSearchAreaTree(fallbackBoard.searchAreaTree, searchAreaDrafts, apiAssignmentsByAreaId);
    const movementPaths = assignRouteColorsToMovementPaths(
      apiMovementPaths.length > 0
        ? filterSituationBoardMovementPathsForMap(apiMovementPaths, activeOperationalPeriodId)
        : fallbackBoard.movementPaths,
      searchAreaTree,
      searchAreaDrafts,
    );
    const recentMarkers =
      apiBoard !== null
        ? filterSituationBoardMarkersForMap(apiRecentMarkers, apiBoard, activeOperationalPeriodId)
        : apiRecentMarkers;

    return {
      ...fallbackBoard,
      operationalPeriods: apiOperationalPeriods.length > 0 ? apiOperationalPeriods : fallbackBoard.operationalPeriods,
      searchAreaTree,
      searchAreaDrafts,
      movementPaths,
      recentMarkers,
      legendItems: createLegendItems(fallbackBoard.legendItems),
    };
  }, [apiBoard, fallbackBoard, savedAreaDrafts]);

  return {
    board,
    isLoading: boardQuery.isLoading,
    isInitialLoading: boardQuery.isLoading && apiBoard === null && !boardQuery.isError,
    isInitialLoadError: boardQuery.isError && apiBoard === null,
    isInitialReconnecting: boardQuery.isFetching && apiBoard === null,
    syncStatus,
    apiBoard,
    isFallback: apiBoard === null,
    isOverallSearchAreaMissing: apiBoard !== null && board.searchAreaDrafts.length === 0,
    retryInitialLoad: () => {
      void boardQuery.refetch();
    },
  };
}

function createBoardSyncStatus({
  hasApiBoard,
  hasBackfilledCriticalSlots,
  isError,
  isFetching,
}: {
  hasApiBoard: boolean;
  hasBackfilledCriticalSlots: boolean;
  isError: boolean;
  isFetching: boolean;
}): SituationBoardDataState['syncStatus'] {
  if (!hasApiBoard) {
    return null;
  }

  if (isError) {
    return { label: '동기화 실패 · 이전 데이터 표시', tone: 'error' };
  }

  if (hasBackfilledCriticalSlots) {
    return { label: '일부 슬롯 지연 · 이전 데이터 보존', tone: 'stale' };
  }

  if (isFetching) {
    return { label: '동기화 중', tone: 'syncing' };
  }

  return null;
}

const CRITICAL_BOARD_SLOTS: readonly BoardSlotName[] = [
  'overall_search_area',
  'area',
  'path',
  'marker',
  'police_phone_freshness',
  'op_toggle',
  'op_history',
  'handover_memo',
  'handover_status',
  'search_history_summary',
  'package_badge',
  'incident_terminal',
];

export function mergeWithPreviousCriticalSlots(
  current: SituationBoardResponseDto | null,
  previous: SituationBoardResponseDto | null,
) {
  if (!current || !previous || current.incidentId !== previous.incidentId) {
    return current;
  }

  const slots = { ...current.slots };
  const slotSources = { ...current.slotSources };
  const sourceVersions = { ...current.sourceVersions };
  const sourceHashes = { ...current.sourceHashes };
  let changed = false;

  CRITICAL_BOARD_SLOTS.forEach((slot) => {
    if (shouldKeepPreviousSlot(slots[slot], previous.slots[slot])) {
      slots[slot] = previous.slots[slot];
      changed = true;
    }

    if (shouldKeepPreviousSlot(slotSources[slot], previous.slotSources[slot])) {
      slotSources[slot] = previous.slotSources[slot];
      changed = true;
    }

    if (isMissingSlot(sourceVersions[slot]) && !isMissingSlot(previous.sourceVersions[slot])) {
      sourceVersions[slot] = previous.sourceVersions[slot];
      changed = true;
    }

    if (isMissingSlot(sourceHashes[slot]) && !isMissingSlot(previous.sourceHashes[slot])) {
      sourceHashes[slot] = previous.sourceHashes[slot];
      changed = true;
    }
  });

  return changed
    ? {
        ...current,
        slots,
        slotSources,
        sourceVersions,
        sourceHashes,
      }
    : current;
}

export function shouldSubscribeIncidentBoardEvents(board: SituationBoardResponseDto | null) {
  if (!board) {
    return false;
  }
  return !isIncidentTerminalClosed(toIncidentTerminal(board));
}

function isMissingSlot(value: unknown) {
  return value === null || value === undefined;
}

function shouldKeepPreviousSlot(currentValue: unknown, previousValue: unknown) {
  if (isMissingSlot(currentValue)) {
    return !isMissingSlot(previousValue);
  }

  if (isEmptySlotValue(currentValue)) {
    return !isMissingSlot(previousValue) && !isEmptySlotValue(previousValue);
  }

  return false;
}

function isEmptySlotValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.length === 0;
  }

  return isRecord(value) && Object.keys(value).length === 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
