import { useEffect, useMemo, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { useIncidentBoardQuery, incidentBoardQueryKeys } from '../../../board/api/incidentBoardApi';
import { openIncidentBoardEventStream } from '../../../board/api/incidentBoardEventStream';
import {
  createIncidentScopedFallbackBoard,
  type SearchAreaAssignedAccount,
  type SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import { hasOverallSearchArea, mergeSearchAreaDrafts } from '../utils/boardApiMappers';
import { toBoardRecentMarkers } from '../utils/markerBoardMapper';
import {
  assignRouteColorsToMovementPaths,
  createLegendItems,
  toMovementPaths,
} from '../utils/movementPathBoardMapper';
import { toOperationalPeriods } from '../utils/operationalPeriodBoardMapper';
import {
  buildFallbackSearchAreaTree,
  buildSearchAreaTree,
  toAssignmentsByAreaId,
  toSearchAreaDrafts,
  toSearchAreaRows,
} from '../utils/searchAreaBoardMapper';

type SituationBoardDataState = {
  board: SituationBoardFallbackData;
  isLoading: boolean;
  apiBoard: SituationBoardResponseDto | null;
  isFallback: boolean;
  isOverallSearchAreaMissing: boolean;
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
  const apiBoard = (boardQuery.data as unknown as SituationBoardResponseDto) ?? null;

  // 외부 refreshVersion 변경 시 board 재조회 (구역 저장 등)
  useEffect(() => {
    if (prevRefreshVersionRef.current === refreshVersion) return;
    prevRefreshVersionRef.current = refreshVersion;
    void queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.all });
  }, [refreshVersion, queryClient]);

  // SSE: 도메인 이벤트 수신 시 board 재조회
  useEffect(() => {
    if (!incidentId) return;

    let cancelled = false;
    let activeSubscription: { close(): void } | null = null;

    const connect = () => {
      if (cancelled) return;

      const accessToken =
        sessionStorage.getItem('suriMapAccessToken') ??
        (import.meta.env.VITE_API_ACCESS_TOKEN as string | undefined) ??
        undefined;

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
  }, [incidentId, queryClient]);

  const board = useMemo<SituationBoardFallbackData>(() => {
    const apiSearchAreaRows = apiBoard ? toSearchAreaRows(apiBoard) : [];
    const apiSearchAreaDrafts = toSearchAreaDrafts(apiSearchAreaRows);
    const apiAssignmentsByAreaId = apiBoard
      ? toAssignmentsByAreaId(apiBoard)
      : new Map<string, SearchAreaAssignedAccount[]>();
    const apiOperationalPeriods = apiBoard ? toOperationalPeriods(apiBoard, fallbackBoard.operationalPeriods) : [];
    const apiRecentMarkers = apiBoard ? toBoardRecentMarkers(apiBoard) : [];
    const apiMovementPaths = apiBoard ? toMovementPaths(apiBoard) : [];
    const searchAreaDrafts =
      apiBoard !== null
        ? mergeSearchAreaDrafts(apiSearchAreaDrafts, savedAreaDrafts)
        : savedAreaDrafts.length > 0
          ? savedAreaDrafts
          : fallbackBoard.searchAreaDrafts;

    const searchAreaTree =
      apiSearchAreaRows.length > 0
        ? buildSearchAreaTree(fallbackBoard.searchAreaTree, apiSearchAreaRows, searchAreaDrafts)
        : buildFallbackSearchAreaTree(fallbackBoard.searchAreaTree, searchAreaDrafts, apiAssignmentsByAreaId);
    const movementPaths = assignRouteColorsToMovementPaths(
      apiMovementPaths.length > 0 ? apiMovementPaths : fallbackBoard.movementPaths,
      searchAreaTree,
      searchAreaDrafts,
    );

    return {
      ...fallbackBoard,
      operationalPeriods: apiOperationalPeriods.length > 0 ? apiOperationalPeriods : fallbackBoard.operationalPeriods,
      searchAreaTree,
      searchAreaDrafts,
      movementPaths,
      recentMarkers: apiRecentMarkers.length > 0 ? apiRecentMarkers : fallbackBoard.recentMarkers,
      legendItems: createLegendItems(fallbackBoard.legendItems, movementPaths),
    };
  }, [apiBoard, fallbackBoard, savedAreaDrafts]);

  return {
    board,
    isLoading: boardQuery.isLoading,
    apiBoard,
    isFallback: apiBoard === null,
    isOverallSearchAreaMissing:
      apiBoard !== null &&
      !hasOverallSearchArea(apiBoard) &&
      !savedAreaDrafts.some((draft) => draft.kind === 'overall'),
  };
}
