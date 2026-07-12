import { areaColorTokens } from '../../../../shared/constants/areaColorTokens';
import {
  applyRouteColorsByAssignee,
  createRouteColorAssigneeKey,
} from '../../../../shared/model/boardMapFeatures';
import { createBoardMapMarkers, createBoardMovementPaths } from '../../../../shared/model/boardMapSlots';
import { isRecord, readSlotRows, readString } from '../../../../shared/model/boardSlotRows';
import type { AreaEditBoardResponseDto } from '../../data/getAreaEditBoard';
import type { AreaEditMapMarker, AreaEditMovementPath } from '../components/AreaEditMap';
import type { CompletedAreaDraft } from '../constants/mockAreaEdit';

function readAssignedAccountCount(row: Record<string, unknown>) {
  const assignedAccounts = row.assignedAccounts;
  if (Array.isArray(assignedAccounts)) return assignedAccounts.filter(isRecord).length;

  const assignedAccountIds = row.assignedAccountIds;
  if (Array.isArray(assignedAccountIds)) return assignedAccountIds.length;

  return 0;
}

export function createAssignedAccountCountsByAreaId(board: AreaEditBoardResponseDto | null) {
  const countsByAreaId = new Map<string, number>();
  if (!board) return countsByAreaId;

  for (const row of readSlotRows(board, 'area')) {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!areaId) continue;
    countsByAreaId.set(areaId, readAssignedAccountCount(row));
  }

  return countsByAreaId;
}

function createAreaEditRouteColorsByAssignee(
  board: AreaEditBoardResponseDto,
  completedDrafts: CompletedAreaDraft[],
) {
  const colorTokensByAreaId = new Map(completedDrafts.map((draft) => [draft.areaId, draft.colorToken]));
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByAccountOpId = new Map<string, string>();

  for (const row of readSlotRows(board, 'area')) {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const opId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
    const colorToken = areaId ? colorTokensByAreaId.get(areaId) : null;
    if (!colorToken) continue;

    const assignedAccounts = row.assignedAccounts;
    if (!Array.isArray(assignedAccounts)) continue;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const accountId = readString(account, 'accountId') ?? readString(account, 'account_id');
      if (accountId) {
        routeColorsByAccountId.set(accountId, areaColorTokens[colorToken].lineColor);
        if (opId) {
          routeColorsByAccountOpId.set(
            createRouteColorAssigneeKey(opId, accountId),
            areaColorTokens[colorToken].lineColor,
          );
        }
      }
    });
  }

  return {
    accountId: routeColorsByAccountId,
    accountOpId: routeColorsByAccountOpId,
  };
}

export function createAreaEditMovementPaths(
  board: AreaEditBoardResponseDto | null,
  completedDrafts: CompletedAreaDraft[],
): AreaEditMovementPath[] {
  if (!board) return [];
  const routeColorsByAssignee = createAreaEditRouteColorsByAssignee(board, completedDrafts);
  return applyRouteColorsByAssignee(
    createBoardMovementPaths(board),
    routeColorsByAssignee.accountId,
    {
      accountId: routeColorsByAssignee.accountOpId,
    },
  );
}

export function createAreaEditMapMarkers(board: AreaEditBoardResponseDto | null): AreaEditMapMarker[] {
  if (!board) return [];
  return createBoardMapMarkers(board);
}
