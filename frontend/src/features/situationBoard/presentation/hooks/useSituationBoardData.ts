import { useEffect, useMemo, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { refreshSituationBoards } from '../../../../app/board/refreshSituationBoards';
import { usePagedIncidentBoardQuery } from '../../../../app/board/usePagedIncidentBoardQuery';
import { useIncidentBoardEvents } from '../../../../app/board/useIncidentBoardEvents';
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
  pathLoading: boolean;
};

export function useSituationBoardData(
  incidentId: string,
  savedAreaDrafts: CompletedAreaDraft[],
  refreshVersion = 0,
): SituationBoardDataState {
  const queryClient = useQueryClient();
  const prevRefreshVersionRef = useRef(refreshVersion);

  const fallbackBoard = useMemo(() => createIncidentScopedFallbackBoard(incidentId), [incidentId]);

  const boardQuery = usePagedIncidentBoardQuery({ incidentId });
  const rawApiBoard = (boardQuery.data as unknown as SituationBoardResponseDto) ?? null;
  const currentApiBoard = useMemo<SituationBoardResponseDto | null>(() => {
    return rawApiBoard && rawApiBoard.incidentId === incidentId ? rawApiBoard : null;
  }, [incidentId, rawApiBoard]);
  const stableApiBoardRef = useRef<SituationBoardResponseDto | null>(null);
  const { apiBoard, hasBackfilledCriticalSlots } = useMemo(() => {
    const merged = boardQuery.isLocationRestricted ? currentApiBoard : mergeWithPreviousCriticalSlots(currentApiBoard, stableApiBoardRef.current, ['path']);
    if (merged) {
      stableApiBoardRef.current = merged;
    }
    return {
      apiBoard: merged,
      hasBackfilledCriticalSlots: Boolean(currentApiBoard && merged && merged !== currentApiBoard),
    };
  }, [currentApiBoard, boardQuery.isLocationRestricted]);
  const hasApiBoard = apiBoard !== null;
  const syncStatus = boardQuery.pathSyncStatus ?? createBoardSyncStatus({
    hasApiBoard,
    hasBackfilledCriticalSlots,
    isError: boardQuery.isError,
    isFetching: boardQuery.isFetching,
    lastSuccessAt: boardQuery.dataUpdatedAt,
  });
  const shouldSubscribeEvents = !boardQuery.isLocationRestricted && shouldSubscribeIncidentBoardEvents(apiBoard);

  // 외부 refreshVersion 변경 시 board 재조회 (구역 저장 등)
  useEffect(() => {
    if (prevRefreshVersionRef.current === refreshVersion) return;
    prevRefreshVersionRef.current = refreshVersion;
    void refreshSituationBoards(queryClient);
  }, [refreshVersion, queryClient]);

  useIncidentBoardEvents(incidentId, hasApiBoard && shouldSubscribeEvents);

  useEffect(() => {
    stableApiBoardRef.current = null;
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
      filterSituationBoardMovementPathsForMap(apiMovementPaths, activeOperationalPeriodId),
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
    pathLoading: boardQuery.pathLoading,
    isLoading: boardQuery.isLoading,
    isInitialLoading: boardQuery.isLoading && apiBoard === null && !boardQuery.isError,
    isInitialLoadError: boardQuery.isError && apiBoard === null,
    isInitialReconnecting: boardQuery.isFetching && apiBoard === null,
    syncStatus,
    apiBoard,
    isFallback: apiBoard === null,
    isOverallSearchAreaMissing: apiBoard !== null && board.searchAreaDrafts.length === 0,
    retryInitialLoad: () => {
      void boardQuery.refetch({ cancelRefetch: false });
    },
  };
}

function createBoardSyncStatus({
  hasApiBoard,
  hasBackfilledCriticalSlots,
  isError,
  isFetching,
  lastSuccessAt,
}: {
  hasApiBoard: boolean;
  hasBackfilledCriticalSlots: boolean;
  isError: boolean;
  isFetching: boolean;
  lastSuccessAt: number;
}): SituationBoardDataState['syncStatus'] {
  if (!hasApiBoard) {
    return null;
  }

  if (isError) {
    return { label: `동기화 실패 · 최신 상태 미확인 · 마지막 조회 성공 ${new Date(lastSuccessAt).toLocaleTimeString('ko-KR')}`, tone: 'error' };
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
