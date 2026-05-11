import { useEffect, useMemo, useState } from 'react';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { getSituationBoard, type SituationBoardResponseDto } from '../../data/getSituationBoard';
import {
  createIncidentScopedFallbackBoard,
  type SearchAreaAssignedAccount,
  type SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import {
  hasOverallSearchArea,
  mergeSearchAreaDrafts,
} from '../utils/boardApiMappers';
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
  const [apiBoard, setApiBoard] = useState<SituationBoardResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const fallbackBoard = useMemo(() => createIncidentScopedFallbackBoard(incidentId), [incidentId]);

  useEffect(() => {
    let isActive = true;
    setIsLoading(true);
    setApiBoard(null);

    void getSituationBoard(incidentId)
      .then((response) => {
        if (!isActive) return;
        setApiBoard(response);
      })
      .catch(() => {
        if (!isActive) return;
        setApiBoard(null);
      })
      .finally(() => {
        if (!isActive) return;
        setIsLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId, refreshVersion]);

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
    isLoading,
    apiBoard,
    isFallback: apiBoard === null,
    isOverallSearchAreaMissing:
      apiBoard !== null &&
      !hasOverallSearchArea(apiBoard) &&
      !savedAreaDrafts.some((draft) => draft.kind === 'overall'),
  };
}
