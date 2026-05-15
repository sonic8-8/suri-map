import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { AreaBbox } from '../../../../shared/model/areaDraft';
import type { MovementPath } from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';

export function readSlotRows(board: SituationBoardResponseDto, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value)) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
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

export function readBbox(row: Record<string, unknown>): AreaBbox | undefined {
  const bbox = row.bbox;
  if (!Array.isArray(bbox) || bbox.length < 4) return undefined;
  const [minLon, minLat, maxLon, maxLat] = bbox;
  if (
    typeof minLon !== 'number' ||
    typeof minLat !== 'number' ||
    typeof maxLon !== 'number' ||
    typeof maxLat !== 'number'
  ) {
    return undefined;
  }
  return [minLon, minLat, maxLon, maxLat];
}

export function readPolygonCoordinates(row: Record<string, unknown>): CompletedAreaDraft['coordinates'] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'Polygon' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const firstRing = geometry.coordinates[0];
  if (!Array.isArray(firstRing)) return null;
  const coordinates = firstRing.filter(isPosition);
  return coordinates.length >= 4 ? coordinates : null;
}

export function isPosition(value: unknown): value is [number, number] {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    typeof value[0] === 'number' &&
    typeof value[1] === 'number'
  );
}

export function hasOverallSearchArea(board: SituationBoardResponseDto) {
  return readSlotRows(board, 'overall_search_area').some((row) => {
    const id = readString(row, 'id') ?? readString(row, 'searchAreaId');
    return id !== null && readPolygonCoordinates(row) !== null;
  });
}

export function mergeSearchAreaDrafts(
  apiSearchAreaDrafts: CompletedAreaDraft[],
  savedAreaDrafts: CompletedAreaDraft[],
): CompletedAreaDraft[] {
  if (savedAreaDrafts.length === 0) return apiSearchAreaDrafts;
  if (apiSearchAreaDrafts.length === 0) return savedAreaDrafts;

  const apiAreaIds = new Set(apiSearchAreaDrafts.map((draft) => draft.areaId));
  return [
    ...apiSearchAreaDrafts,
    ...savedAreaDrafts.filter((draft) => !apiAreaIds.has(draft.areaId)),
  ];
}

export function readMovementType(row: Record<string, unknown>): MovementPath['movementType'] {
  const movementType = readString(row, 'movementType');
  return movementType === 'VEHICLE' || movementType === 'FOOT' || movementType === 'UNKNOWN'
    ? movementType
    : 'UNKNOWN';
}
