import { areaColorTokens } from '../../../../shared/constants/areaColorTokens';
import { applyRouteColorsByAssignee } from '../../../../shared/model/boardMapFeatures';
import { createBoardMapMarkers, createBoardMovementPaths } from '../../../../shared/model/boardMapSlots';
import type { AreaEditBoardResponseDto } from '../../data/getAreaEditBoard';
import type { AreaEditMapMarker, AreaEditMovementPath } from '../components/AreaEditMap';
import type { CompletedAreaDraft } from '../constants/mockAreaEdit';

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function readSlotRows(board: AreaEditBoardResponseDto, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

export function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

export function readPolicePhoneId(row: Record<string, unknown>) {
  return (
    readString(row, 'policePhoneId') ??
    readString(row, 'police_phone_id') ??
    readString(row, 'phoneId') ??
    readString(row, 'deviceId') ??
    readString(row, 'device_id')
  );
}

export function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' ? value : null;
}

export function readAssignedAccountCount(row: Record<string, unknown>) {
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

function isPosition(value: unknown): value is [number, number] {
  return Array.isArray(value) && value.length >= 2 && typeof value[0] === 'number' && typeof value[1] === 'number';
}

export function readLineStringCoordinates(row: Record<string, unknown>): [number, number][] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const coordinates = geometry.coordinates.filter(isPosition);
  return coordinates.length >= 2 ? coordinates : null;
}

export function readPointCoordinates(row: Record<string, unknown>): [number, number] | null {
  const geometry = row.geometry;
  if (isRecord(geometry) && geometry.type === 'Point' && isPosition(geometry.coordinates)) {
    return geometry.coordinates;
  }

  const coordinates = row.coordinates;
  return isPosition(coordinates) ? coordinates : null;
}

export function readMovementType(row: Record<string, unknown>): AreaEditMovementPath['movementType'] {
  const movementType = readString(row, 'movementType');
  return movementType === 'VEHICLE' || movementType === 'FOOT' || movementType === 'UNKNOWN'
    ? movementType
    : 'UNKNOWN';
}

export function readMarkerType(row: Record<string, unknown>): AreaEditMapMarker['markerType'] {
  const markerType = readString(row, 'markerType') ?? readString(row, 'type');
  if (
    markerType === 'CLUE' ||
    markerType === 'PERSON_FOUND' ||
    markerType === 'FIELD_CONDITION' ||
    markerType === 'SUPPORT_REQUEST' ||
    markerType === 'NOTE'
  ) {
    return markerType;
  }

  return 'UNKNOWN';
}

function createAreaEditRouteColorsByAssignee(
  board: AreaEditBoardResponseDto,
  completedDrafts: CompletedAreaDraft[],
) {
  const colorTokensByAreaId = new Map(completedDrafts.map((draft) => [draft.areaId, draft.colorToken]));
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByPolicePhoneId = new Map<string, string>();

  for (const row of readSlotRows(board, 'area')) {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const colorToken = areaId ? colorTokensByAreaId.get(areaId) : null;
    if (!colorToken) continue;

    const assignedAccounts = row.assignedAccounts;
    if (!Array.isArray(assignedAccounts)) continue;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const accountId = readString(account, 'accountId') ?? readString(account, 'account_id');
      const policePhoneId = readPolicePhoneId(account);
      if (accountId) routeColorsByAccountId.set(accountId, areaColorTokens[colorToken].lineColor);
      if (policePhoneId) routeColorsByPolicePhoneId.set(policePhoneId, areaColorTokens[colorToken].lineColor);
    });
  }

  return { accountId: routeColorsByAccountId, policePhoneId: routeColorsByPolicePhoneId };
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
    routeColorsByAssignee.policePhoneId,
  );
}

export function createAreaEditMapMarkers(board: AreaEditBoardResponseDto | null): AreaEditMapMarker[] {
  if (!board) return [];
  return createBoardMapMarkers(board);
}
