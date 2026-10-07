import { areaColorTokens } from '../../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import {
  applyRouteColorsByAssignee,
  createRouteColorAssigneeKey,
} from '../../../../../shared/model/boardMapFeatures';
import { createBoardMovementPaths, readBoardSlotRows } from '../../../../../shared/model/boardMapSlots';
import {
  resolveRouteColorByGeometry,
  type RouteAreaColorCandidate,
} from '../../../../../shared/model/routeAreaColorMatcher';
import type {
  MovementPath,
  SearchAreaTreeNode,
  SituationBoardFallbackData,
} from '../../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { filterSituationBoardMovementPathsForMap } from '../operationalPeriod/opScopedMapRendering';

export function createMovementPathMapper() {
  let previousContext = '';
  let mappedRows = new WeakMap<Record<string, unknown>, { index: number; paths: MovementPath[] }>();

  return (
    board: SituationBoardResponseDto,
    activeOperationalPeriodId: string | null,
    searchAreaTree: SearchAreaTreeNode,
    searchAreaDrafts: CompletedAreaDraft[],
  ): MovementPath[] => {
    const rows = readBoardSlotRows(board, 'path');
    // 페이지마다 새로 만든 구역 객체도 값이 같으면 재사용한다. 경로·좌표 전체를 직렬화하지 않는다.
    const context = JSON.stringify([
      board.incidentId, board.activeOpId, activeOperationalPeriodId, board.serverTs,
      board.slots.police_phone_freshness, searchAreaTree, searchAreaDrafts,
    ]);
    if (context !== previousContext || rows.length === 0) {
      mappedRows = new WeakMap();
      previousContext = context;
    }

    const entries = rows.map((row, index) => {
      const cached = mappedRows.get(row);
      if (cached?.index === index) return { row, index, paths: cached.paths, reused: true };
      const paths = filterSituationBoardMovementPathsForMap(
        createBoardMovementPaths({ ...board, slots: { ...board.slots, path: [row] } }, index),
        activeOperationalPeriodId,
      );
      return { row, index, paths, reused: false };
    });
    // 색상 기준은 한 번 만들고, 변경된 경로들에만 적용한다.
    const changedPaths = assignRouteColorsToMovementPaths(
      entries.filter(entry => !entry.reused).flatMap(entry => entry.paths), searchAreaTree, searchAreaDrafts,
    );
    let nextChangedPath = 0;
    return entries.flatMap(entry => {
      if (entry.reused) return entry.paths;
      const paths = changedPaths.slice(nextChangedPath, nextChangedPath + entry.paths.length);
      nextChangedPath += entry.paths.length;
      mappedRows.set(entry.row, { index: entry.index, paths });
      return paths;
    });
  };
}

export function toMovementPaths(board: SituationBoardResponseDto): MovementPath[] {
  return createBoardMovementPaths(board);
}

export function assignRouteColorsToMovementPaths(
  movementPaths: MovementPath[],
  searchAreaTree: SearchAreaTreeNode,
  searchAreaDrafts: CompletedAreaDraft[],
) {
  const routeColorsByAssignee = createRouteColorsByAssignee(searchAreaTree, searchAreaDrafts);
  const areaColorCandidates = createRouteAreaColorCandidates(searchAreaTree, searchAreaDrafts);
  const assigneeColoredPaths = applyRouteColorsByAssignee(
    movementPaths,
    routeColorsByAssignee.accountId,
    {
      accountId: routeColorsByAssignee.accountOpId,
    },
  );

  return assigneeColoredPaths.map((path) => ({
    ...path,
    routeColor: resolveRouteColorByGeometry(path.coordinates, areaColorCandidates, path.opId) ?? path.routeColor,
  }));
}

export function createLegendItems(baseLegendItems: SituationBoardFallbackData['legendItems']) {
  return baseLegendItems;
}

function createRouteColorsByAssignee(searchAreaTree: SearchAreaTreeNode, searchAreaDrafts: CompletedAreaDraft[]) {
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByAccountOpId = new Map<string, string>();
  const routeColorPriorityByAccountId = new Map<string, number>();
  const routeColorsByAreaId = new Map(
    searchAreaDrafts.map((draft) => [draft.areaId, areaColorTokens[draft.colorToken].lineColor]),
  );
  const visit = (area: SearchAreaTreeNode, depth = 0) => {
    const routeColor = routeColorsByAreaId.get(area.id) ?? areaColorTokens[area.colorToken].lineColor;
    (area.assignedAccounts ?? []).forEach((account) => {
      const currentAccountPriority = routeColorPriorityByAccountId.get(account.accountId) ?? -1;
      if (depth >= currentAccountPriority) {
        routeColorsByAccountId.set(account.accountId, routeColor);
        routeColorPriorityByAccountId.set(account.accountId, depth);
      }
      if (area.opId) {
        routeColorsByAccountOpId.set(createRouteColorAssigneeKey(area.opId, account.accountId), routeColor);
      }

    });
    (area.children ?? []).forEach((child) => visit(child, depth + 1));
  };

  visit(searchAreaTree);
  return {
    accountId: routeColorsByAccountId,
    accountOpId: routeColorsByAccountOpId,
  };
}

function createRouteAreaColorCandidates(
  searchAreaTree: SearchAreaTreeNode,
  searchAreaDrafts: CompletedAreaDraft[],
): RouteAreaColorCandidate[] {
  const draftsByAreaId = new Map(searchAreaDrafts.map((draft) => [draft.areaId, draft]));
  const candidates: RouteAreaColorCandidate[] = [];
  const visit = (area: SearchAreaTreeNode) => {
    const draft = draftsByAreaId.get(area.id);
    if (draft) {
      candidates.push({
        id: draft.areaId,
        opId: area.opId,
        kind: draft.kind,
        coordinates: draft.coordinates,
        lineColor: areaColorTokens[draft.colorToken].lineColor,
      });
    }
    (area.children ?? []).forEach(visit);
  };

  visit(searchAreaTree);
  return candidates;
}
