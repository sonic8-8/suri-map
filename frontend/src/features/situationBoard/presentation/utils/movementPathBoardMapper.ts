import { areaColorTokens } from '../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import {
  applyRouteColorsByAssignee,
  createRouteColorAssigneeKey,
} from '../../../../shared/model/boardMapFeatures';
import { createBoardMovementPaths } from '../../../../shared/model/boardMapSlots';
import type {
  MovementPath,
  SearchAreaTreeNode,
  SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';

export function toMovementPaths(board: SituationBoardResponseDto): MovementPath[] {
  return createBoardMovementPaths(board);
}

export function assignRouteColorsToMovementPaths(
  movementPaths: MovementPath[],
  searchAreaTree: SearchAreaTreeNode,
  searchAreaDrafts: CompletedAreaDraft[],
) {
  const routeColorsByAssignee = createRouteColorsByAssignee(searchAreaTree, searchAreaDrafts);
  return applyRouteColorsByAssignee(
    movementPaths,
    routeColorsByAssignee.accountId,
    routeColorsByAssignee.policePhoneId,
    {
      accountId: routeColorsByAssignee.accountOpId,
      policePhoneId: routeColorsByAssignee.policePhoneOpId,
    },
  );
}

export function createLegendItems(
  baseLegendItems: SituationBoardFallbackData['legendItems'],
  movementPaths: MovementPath[],
) {
  const deviceRouteItems = new Map<string, SituationBoardFallbackData['legendItems'][number]>();

  movementPaths.forEach((path) => {
    if (!path.routeColor) return;
    const deviceKey = path.policePhoneId ?? path.id;
    if (deviceRouteItems.has(deviceKey)) return;

    deviceRouteItems.set(deviceKey, {
      label: path.policePhoneId ? `${path.policePhoneId} 경로` : `${path.label} 경로`,
      className: 'legend-swatch device-route',
      color: path.routeColor,
      lineStyle: path.movementType === 'FOOT' ? 'dashed' : 'solid',
    });
  });

  return [...baseLegendItems, ...deviceRouteItems.values()];
}

function createRouteColorsByAssignee(searchAreaTree: SearchAreaTreeNode, searchAreaDrafts: CompletedAreaDraft[]) {
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByPolicePhoneId = new Map<string, string>();
  const routeColorsByAccountOpId = new Map<string, string>();
  const routeColorsByPolicePhoneOpId = new Map<string, string>();
  const routeColorPriorityByAccountId = new Map<string, number>();
  const routeColorPriorityByPolicePhoneId = new Map<string, number>();
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

      if (account.policePhoneId) {
        const currentPhonePriority = routeColorPriorityByPolicePhoneId.get(account.policePhoneId) ?? -1;
        if (depth >= currentPhonePriority) {
          routeColorsByPolicePhoneId.set(account.policePhoneId, routeColor);
          routeColorPriorityByPolicePhoneId.set(account.policePhoneId, depth);
        }
        if (area.opId) {
          routeColorsByPolicePhoneOpId.set(createRouteColorAssigneeKey(area.opId, account.policePhoneId), routeColor);
        }
      }
    });
    (area.children ?? []).forEach((child) => visit(child, depth + 1));
  };

  visit(searchAreaTree);
  return {
    accountId: routeColorsByAccountId,
    policePhoneId: routeColorsByPolicePhoneId,
    accountOpId: routeColorsByAccountOpId,
    policePhoneOpId: routeColorsByPolicePhoneOpId,
  };
}
