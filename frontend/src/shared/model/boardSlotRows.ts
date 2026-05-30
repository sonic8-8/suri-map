import type { AreaBbox, CompletedAreaDraft } from './areaDraft';

export type BoardSlotRowContainer = {
  slots: Partial<Record<string, unknown>>;
};

export type BoardSlotPosition = [number, number];
export type BoardSlotMovementType = 'VEHICLE' | 'FOOT' | 'UNKNOWN';
export type BoardSlotMarkerType = 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';

export function readSlotRows(board: BoardSlotRowContainer, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
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

export function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

export function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : null;
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

export function readAccountId(row: Record<string, unknown>) {
  return readString(row, 'accountId') ?? readString(row, 'account_id');
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

export function readLineStringCoordinates(row: Record<string, unknown>): BoardSlotPosition[] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const coordinates = geometry.coordinates.filter(isPosition);
  return coordinates.length >= 2 ? coordinates : null;
}

export function readPointCoordinates(row: Record<string, unknown>): BoardSlotPosition | null {
  const geometry = row.geometry ?? row.location;
  if (isRecord(geometry) && geometry.type === 'Point' && isPosition(geometry.coordinates)) {
    return geometry.coordinates;
  }

  const coordinates = row.coordinates;
  return isPosition(coordinates) ? coordinates : null;
}

export function readMovementType(row: Record<string, unknown>): BoardSlotMovementType | null {
  const movementType =
    readString(row, 'movementType') ??
    readString(row, 'segmentType') ??
    readString(row, 'pathType') ??
    readString(row, 'mobilityType');

  if (movementType === 'VEHICLE' || movementType === 'CAR') return 'VEHICLE';
  if (movementType === 'FOOT' || movementType === 'WALK') return 'FOOT';
  if (movementType === 'UNKNOWN') return 'UNKNOWN';
  return null;
}

export function readMarkerType(row: Record<string, unknown>): BoardSlotMarkerType {
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

export function isPosition(value: unknown): value is BoardSlotPosition {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    typeof value[0] === 'number' &&
    typeof value[1] === 'number'
  );
}
