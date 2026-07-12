import { areaColorTokens } from '../../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import {
  applyRouteColorsByAssignee,
  createRouteColorAssigneeKey,
} from '../../../../../shared/model/boardMapFeatures';
import { createBoardMovementPaths } from '../../../../../shared/model/boardMapSlots';
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
