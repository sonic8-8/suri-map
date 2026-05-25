import { useEffect, useMemo, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { getStoredAccessToken } from '../../../../shared/api/client';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import {
  useIncidentBoardQuery,
  incidentBoardQueryKeys,
} from '../../../board/api/incidentBoardApi';
import { openIncidentBoardEventStream } from '../../../board/api/incidentBoardEventStream';
import { mergeWithPreviousCriticalSlots } from '../../../board/model/incidentBoardMerge';
import {
  createIncidentScopedFallbackBoard,
  type SearchAreaAssignedAccount,
  type SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import { toBoardRecentMarkers } from '../../../board/model/markerSlot';
import {
  assignRouteColorsToMovementPaths,
  createLegendItems,
  toMovementPaths,
} from '../slots/path/movementPathBoardMapper';
import {
  filterSituationBoardMarkersForMap,
  filterSituationBoardMovementPathsForMap,
  filterSituationBoardSearchAreaRowsForMap,
} from '../slots/operationalPeriod/opScopedMapRendering';
import { toOperationalPeriods } from '../slots/operationalPeriod/operationalPeriodBoardMapper';
import {
  buildFallbackSearchAreaTree,
  buildSearchAreaTree,
  toAssignmentsByAreaId,
  toSearchAreaDrafts,
  toSearchAreaRows,
} from '../slots/searchArea/searchAreaBoardMapper';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../../../board/model/incidentTerminalSlot';

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

export function shouldSubscribeIncidentBoardEvents(board: SituationBoardResponseDto | null) {
  if (!board) {
    return false;
  }
  return !isIncidentTerminalClosed(toIncidentTerminal(board));
}
