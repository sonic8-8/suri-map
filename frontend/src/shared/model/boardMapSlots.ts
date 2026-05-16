export type BoardResponseLike = {
  incidentId: string;
  serverTs: string;
  activeOpId: string | null;
  slots: Record<string, unknown>;
};

export type BoardPosition = [number, number];

export type BoardMovementPath = {
  id: string;
  policePhoneId: string | null;
  accountId: string | null;
  routeColor: string | null;
  opId: string;
  label: string;
  movementType: 'VEHICLE' | 'FOOT' | 'UNKNOWN';
  coordinates: BoardPosition[];
  startedAt: string;
  endedAt: string | null;
};

export type BoardMapMarker = {
  id: string;
  markerType: 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';
  supportRequestType: 'DRONE' | 'POLICE_DOG' | 'OTHER' | null;
  coordinates: BoardPosition;
  title: string | null;
  memo: string | null;
  occurredAt: string;
  opId: string;
  reporterLabel: string | null;
  sourceLabel: string | null;
  photoCount: number;
};

export function readBoardSlotRows(board: BoardResponseLike, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

export function createBoardMovementPaths(board: BoardResponseLike | null): BoardMovementPath[] {
  if (!board) return [];

  const accountIdsByPolicePhoneId = createAccountIdsByPolicePhoneId(board);

  return readBoardSlotRows(board, 'path').flatMap((row, pathIndex) => {
    const segments = row.segments;
    if (Array.isArray(segments)) {
      return segments.filter(isRecord).flatMap((segment, segmentIndex) => {
        const coordinates = readLineStringCoordinates(segment);
        if (!coordinates) return [];

        const rowId = readString(row, 'id') ?? readString(row, 'pathId') ?? 'path';
        const policePhoneId = readPolicePhoneId(segment) ?? readPolicePhoneId(row);
        const accountId =
          readAccountId(segment) ?? readAccountId(row) ?? readAccountIdByPolicePhoneId(policePhoneId, accountIdsByPolicePhoneId);
        return [
          {
            id: readString(segment, 'id') ?? readString(segment, 'segmentId') ?? `${rowId}:segment-${segmentIndex + 1}`,
            policePhoneId,
            accountId,
            routeColor: null,
            opId: readRowOpId(segment) ?? readRowOpId(row) ?? board.activeOpId ?? '',
            label: readString(segment, 'label') ?? readString(row, 'label') ?? `Path ${pathIndex + 1}`,
            movementType: readMovementType(segment) ?? readMovementType(row) ?? 'UNKNOWN',
            coordinates,
            startedAt: readString(segment, 'startedAt') ?? readString(row, 'startedAt') ?? board.serverTs,
            endedAt: readString(segment, 'endedAt') ?? readString(row, 'endedAt'),
          },
        ];
      });
    }

    const coordinates = readLineStringCoordinates(row);
    if (!coordinates) return [];

    return [
      {
        id: readString(row, 'id') ?? readString(row, 'pathId') ?? `${board.incidentId}:path-${pathIndex + 1}`,
        policePhoneId: readPolicePhoneId(row),
        accountId:
          readAccountId(row) ??
          readAccountIdByPolicePhoneId(readPolicePhoneId(row), accountIdsByPolicePhoneId),
        routeColor: null,
        opId: readRowOpId(row) ?? board.activeOpId ?? '',
        label: readString(row, 'label') ?? `Path ${pathIndex + 1}`,
        movementType: readMovementType(row) ?? 'UNKNOWN',
        coordinates,
        startedAt: readString(row, 'startedAt') ?? board.serverTs,
        endedAt: readString(row, 'endedAt'),
      },
    ];
  });
}

function createAccountIdsByPolicePhoneId(board: BoardResponseLike) {
  const accountIdsByPolicePhoneId = new Map<string, string>();

  readBoardSlotRows(board, 'police_phone_freshness').forEach((row) => {
    const policePhoneId = readPolicePhoneId(row);
    const accountId = readAccountId(row);
    if (policePhoneId && accountId) {
      accountIdsByPolicePhoneId.set(policePhoneId, accountId);
    }
  });

  return accountIdsByPolicePhoneId;
}

function readAccountIdByPolicePhoneId(
  policePhoneId: string | null,
  accountIdsByPolicePhoneId: ReadonlyMap<string, string>,
) {
  return policePhoneId ? accountIdsByPolicePhoneId.get(policePhoneId) ?? null : null;
}

export function createBoardMapMarkers(board: BoardResponseLike | null): BoardMapMarker[] {
  if (!board) return [];

  return readBoardSlotRows(board, 'marker').flatMap((row, index) => {
    const coordinates = readPointCoordinates(row);
    if (!coordinates) return [];

    const markerType = readMarkerType(row);
    const supportRequestType = readSupportRequestType(row);
    const occurredAt =
      readString(row, 'occurredAt') ??
      readString(row, 'createdAt') ??
      readString(row, 'updatedAt') ??
      board.serverTs;

    return [
      {
        id: readString(row, 'id') ?? readString(row, 'markerId') ?? `${board.incidentId}:marker-${index + 1}`,
        markerType,
        supportRequestType,
        coordinates,
        title: readString(row, 'title'),
        memo: readString(row, 'memo') ?? readString(row, 'content') ?? readString(row, 'description'),
        occurredAt,
        opId: readRowOpId(row) ?? board.activeOpId ?? '',
        reporterLabel:
          readString(row, 'reporter') ??
          readString(row, 'reportedBy') ??
          readString(row, 'createdByAccountDisplayName') ??
          readString(row, 'createdByAccountId') ??
          readString(row, 'policePhoneId'),
        sourceLabel: readString(row, 'source'),
        photoCount: readNumber(row, 'photoCount') ?? readPhotoCount(row),
      },
    ];
  });
}

export function readBoardRowOpId(row: Record<string, unknown>) {
  return readRowOpId(row);
}

export function isBoardRecord(value: unknown): value is Record<string, unknown> {
  return isRecord(value);
}

export function readBoardString(row: Record<string, unknown>, key: string) {
  return readString(row, key);
}

function readLineStringCoordinates(row: Record<string, unknown>): BoardPosition[] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const coordinates = geometry.coordinates.filter(isPosition);
  return coordinates.length >= 2 ? coordinates : null;
}

function readPointCoordinates(row: Record<string, unknown>): BoardPosition | null {
  const geometry = row.geometry ?? row.location;
  if (isRecord(geometry) && geometry.type === 'Point' && isPosition(geometry.coordinates)) {
    return geometry.coordinates;
  }

  const coordinates = row.coordinates;
  return isPosition(coordinates) ? coordinates : null;
}

function readMovementType(row: Record<string, unknown>): BoardMovementPath['movementType'] | null {
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

function readMarkerType(row: Record<string, unknown>): BoardMapMarker['markerType'] {
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

function readSupportRequestType(row: Record<string, unknown>): BoardMapMarker['supportRequestType'] {
  const supportRequestType = readString(row, 'supportRequestType') ?? readString(row, 'support_request_type');
  if (supportRequestType === 'DRONE' || supportRequestType === 'POLICE_DOG' || supportRequestType === 'OTHER') {
    return supportRequestType;
  }

  return null;
}

function readRowOpId(row: Record<string, unknown>) {
  return readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
}

function readPolicePhoneId(row: Record<string, unknown>) {
  return (
    readString(row, 'policePhoneId') ??
    readString(row, 'police_phone_id') ??
    readString(row, 'phoneId') ??
    readString(row, 'deviceId') ??
    readString(row, 'device_id')
  );
}

function readAccountId(row: Record<string, unknown>) {
  return readString(row, 'accountId') ?? readString(row, 'account_id');
}

function readPhotoCount(row: Record<string, unknown>) {
  const photos = row.photos;
  return Array.isArray(photos) ? photos.length : 0;
}

function isPosition(value: unknown): value is BoardPosition {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    typeof value[0] === 'number' &&
    typeof value[1] === 'number'
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' ? value : null;
}
