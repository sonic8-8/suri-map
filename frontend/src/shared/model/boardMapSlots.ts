import {
  isRecord,
  readAccountId,
  readLineStringCoordinates,
  readMarkerType,
  readMovementType,
  readNumber,
  readPointCoordinates,
  readPolicePhoneId,
  readSlotRows,
  readString,
} from './boardSlotRows';

export type BoardResponseLike = {
  incidentId: string;
  serverTs: string;
  activeOpId: string | null;
  slots: Record<string, unknown>;
};

export type BoardPosition = [number, number];
export type BoardPolicePhoneFreshnessStatus = 'ONLINE' | 'STALE' | 'LOST' | 'UNKNOWN';

export type BoardMovementPath = {
  id: string;
  policePhoneId: string | null;
  accountId: string | null;
  freshnessStatus: BoardPolicePhoneFreshnessStatus;
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
  source: 'APP' | 'WEB' | 'MOCK_SEED' | 'SYSTEM' | 'UNKNOWN';
  version: number | null;
  coordinates: BoardPosition;
  title: string | null;
  memo: string | null;
  occurredAt: string;
  opId: string;
  reporterLabel: string | null;
  sourceLabel: string | null;
  photoCount: number;
  photoThumbnailUrl: string | null;
};

export function readBoardSlotRows(board: BoardResponseLike, slot: string): Record<string, unknown>[] {
  return readSlotRows(board, slot);
}

export function createBoardMovementPaths(board: BoardResponseLike | null): BoardMovementPath[] {
  if (!board) return [];

  const accountIdsByPolicePhoneId = createAccountIdsByPolicePhoneId(board);
  const freshnessStatusByPolicePhoneId = createFreshnessStatusByPolicePhoneId(board);

  return readBoardSlotRows(board, 'path').flatMap((row, pathIndex) => {
    const segments = row.segments;
    if (Array.isArray(segments)) {
      const segmentRows = segments.filter(isRecord);
      return segmentRows.flatMap((segment, segmentIndex) => {
        const coordinates = readConnectedSegmentCoordinates(segmentRows, segmentIndex);
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
            freshnessStatus: readFreshnessStatusByPolicePhoneId(policePhoneId, freshnessStatusByPolicePhoneId),
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
        freshnessStatus: readFreshnessStatusByPolicePhoneId(readPolicePhoneId(row), freshnessStatusByPolicePhoneId),
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

function readConnectedSegmentCoordinates(
  segments: Record<string, unknown>[],
  segmentIndex: number,
): BoardPosition[] | null {
  const coordinates = readLineStringCoordinates(segments[segmentIndex]);
  if (!coordinates) return null;
  if (segmentIndex === 0) return coordinates;

  const previousCoordinates = readLineStringCoordinates(segments[segmentIndex - 1]);
  const previousLastCoordinate = previousCoordinates?.at(-1);
  const currentFirstCoordinate = coordinates[0];
  if (!previousLastCoordinate || !currentFirstCoordinate || isSamePosition(previousLastCoordinate, currentFirstCoordinate)) {
    return coordinates;
  }

  return [previousLastCoordinate, ...coordinates];
}

function isSamePosition(left: BoardPosition, right: BoardPosition) {
  return left[0] === right[0] && left[1] === right[1];
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

function createFreshnessStatusByPolicePhoneId(board: BoardResponseLike) {
  const freshnessStatusByPolicePhoneId = new Map<string, BoardPolicePhoneFreshnessStatus>();

  readBoardSlotRows(board, 'police_phone_freshness').forEach((row) => {
    const policePhoneId = readPolicePhoneId(row);
    const freshnessStatus = readPolicePhoneFreshnessStatus(row);
    if (policePhoneId && freshnessStatus) {
      freshnessStatusByPolicePhoneId.set(policePhoneId, freshnessStatus);
    }
  });

  return freshnessStatusByPolicePhoneId;
}

function readAccountIdByPolicePhoneId(
  policePhoneId: string | null,
  accountIdsByPolicePhoneId: ReadonlyMap<string, string>,
) {
  return policePhoneId ? accountIdsByPolicePhoneId.get(policePhoneId) ?? null : null;
}

function readFreshnessStatusByPolicePhoneId(
  policePhoneId: string | null,
  freshnessStatusByPolicePhoneId: ReadonlyMap<string, BoardPolicePhoneFreshnessStatus>,
) {
  return policePhoneId ? freshnessStatusByPolicePhoneId.get(policePhoneId) ?? 'UNKNOWN' : 'UNKNOWN';
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
        source: readMarkerSource(row),
        version: readNumber(row, 'version'),
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
        photoThumbnailUrl: readPhotoThumbnailUrl(row),
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

function readSupportRequestType(row: Record<string, unknown>): BoardMapMarker['supportRequestType'] {
  const supportRequestType = readString(row, 'supportRequestType') ?? readString(row, 'support_request_type');
  if (supportRequestType === 'DRONE' || supportRequestType === 'POLICE_DOG' || supportRequestType === 'OTHER') {
    return supportRequestType;
  }

  return null;
}

function readMarkerSource(row: Record<string, unknown>): BoardMapMarker['source'] {
  const source = readString(row, 'source') ?? readString(row, 'markerSource') ?? readString(row, 'marker_source');
  if (source === 'APP' || source === 'WEB' || source === 'MOCK_SEED' || source === 'SYSTEM') {
    return source;
  }

  return 'UNKNOWN';
}

function readRowOpId(row: Record<string, unknown>) {
  return readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
}

function readPolicePhoneFreshnessStatus(row: Record<string, unknown>): BoardPolicePhoneFreshnessStatus | null {
  const freshnessStatus =
    readString(row, 'freshnessStatus') ??
    readString(row, 'status') ??
    readString(row, 'freshness');

  switch (freshnessStatus?.toUpperCase()) {
    case 'ONLINE':
    case 'NORMAL':
      return 'ONLINE';
    case 'STALE':
      return 'STALE';
    case 'LOST':
      return 'LOST';
    default:
      return null;
  }
}

function readPhotoCount(row: Record<string, unknown>) {
  const photoSummary = row.photoSummary;
  if (Array.isArray(photoSummary)) return photoSummary.length;

  const photos = row.photos;
  return Array.isArray(photos) ? photos.length : 0;
}

function readPhotoThumbnailUrl(row: Record<string, unknown>) {
  return (
    readDisplayUrl(row) ??
    readDisplayUrlFromCollection(row.photoSummary) ??
    readDisplayUrlFromCollection(row.photos)
  );
}

function readDisplayUrlFromCollection(value: unknown) {
  if (!Array.isArray(value)) return null;

  for (const item of value) {
    if (!isRecord(item)) continue;
    const displayUrl = readDisplayUrl(item);
    if (displayUrl) return displayUrl;
  }

  return null;
}

function readDisplayUrl(row: Record<string, unknown>) {
  return (
    readString(row, 'photoThumbnailUrl') ??
    readString(row, 'thumbnailUrl') ??
    readString(row, 'photoUrl') ??
    readString(row, 'imageUrl') ??
    readString(row, 'contentUrl') ??
    readString(row, 'url')
  );
}
