import { getAreaVisualStyle, type AreaVisualStyle } from '../../../../shared/model/areaColorRegistry';
import {
  createBoardMapMarkers,
  createBoardMovementPaths,
  type BoardMapMarker,
  type BoardMovementPath,
} from '../../../../shared/model/boardMapSlots';
import {
  applyRouteColorsByAssignee,
  createRouteColorAssigneeKey,
  getRouteCoreColor,
} from '../../../../shared/model/boardMapFeatures';
import {
  resolveRouteColorByGeometry,
  type RouteAreaColorCandidate,
} from '../../../../shared/model/routeAreaColorMatcher';
import { isRecord, readSlotRows, readString } from '../../../../shared/model/boardSlotRows';
import { type IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
import type { RecentMarker } from '../../../../shared/model/situationBoardViewModel';

export type Position = [number, number];

type PolygonGeometry = { type: 'Polygon'; coordinates: Position[][] };
type MultiPolygonGeometry = { type: 'MultiPolygon'; coordinates: Position[][][] };
type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
type ComparisonGeometry = PolygonGeometry | MultiPolygonGeometry | LineStringGeometry | PointGeometry;
type ComparisonFeature = {
  type: 'Feature';
  properties: Record<string, string | number>;
  geometry: ComparisonGeometry;
};

export type ComparisonFeatureCollection = {
  type: 'FeatureCollection';
  features: ComparisonFeature[];
};

export type ComparisonFeatureCollections = {
  areas: ComparisonFeatureCollection;
  paths: ComparisonFeatureCollection;
  markers: ComparisonFeatureCollection;
};

const opColorPalette = ['#2563eb', '#f59e0b', '#0f766e', '#7c3aed', '#dc2626', '#0891b2'];

export function createComparisonFeatureCollections(
  board: IncidentBoardResponse | null,
  incidentId: string,
  selectedOpIds: string[],
  focusedOpId: string | null,
): ComparisonFeatureCollections {
  if (!board) {
    return {
      areas: emptyFeatureCollection(),
      paths: emptyFeatureCollection(),
      markers: emptyFeatureCollection(),
    };
  }

  const selectedOpIdSet = new Set(selectedOpIds);
  const areaRows = readSlotRows(board, 'area').filter((row) => rowBelongsToSelectedOp(row, selectedOpIdSet));
  const areaVisualStylesByAreaId = createAreaVisualStylesByAreaId(areaRows);
  const areaColorCandidates = createRouteAreaColorCandidates(areaRows, areaVisualStylesByAreaId);
  const routeColorsByAssignee = createRouteColorsByAssignee(areaRows, areaVisualStylesByAreaId);
  const paths = applyRouteColorsByAssignee(
    createBoardMovementPaths(board),
    routeColorsByAssignee.accountId,
    {
      accountId: routeColorsByAssignee.accountOpId,
    },
  )
    .map((path) => ({
      ...path,
      routeColor: resolveRouteColorByGeometry(path.coordinates, areaColorCandidates, path.opId) ?? path.routeColor,
    }))
    .filter((path) => rowBelongsToSelectedOpId(path.opId, selectedOpIdSet));
  const markers = createBoardMapMarkers(board).filter((marker) => rowBelongsToSelectedOpId(marker.opId, selectedOpIdSet));

  return {
    areas: {
      type: 'FeatureCollection' as const,
      features: areaRows.flatMap((row, index) => createAreaFeature(row, index, incidentId, 'UNIT', areaVisualStylesByAreaId)),
    },
    paths: {
      type: 'FeatureCollection' as const,
      features: paths.map((path) => createPathFeatureFromBoardPath(path, incidentId, focusedOpId, selectedOpIds)),
    },
    markers: {
      type: 'FeatureCollection' as const,
      features: markers.map((marker) => createMarkerFeatureFromBoardMarker(marker, incidentId, focusedOpId, selectedOpIds)),
    },
  };
}

export function createOverallAreaFeatureCollection(
  board: IncidentBoardResponse | null,
  incidentId: string,
): ComparisonFeatureCollection {
  if (!board) {
    return emptyFeatureCollection();
  }

  const overallRows = readSlotRows(board, 'overall_search_area');
  const areaVisualStylesByAreaId = createAreaVisualStylesByAreaId(overallRows);

  return {
    type: 'FeatureCollection' as const,
    features: overallRows.flatMap((row, index) => createAreaFeature(row, index, incidentId, 'OVERALL', areaVisualStylesByAreaId)),
  };
}

export function createComparisonBoardMarkers(
  board: IncidentBoardResponse | null,
  selectedOpIds: string[],
): RecentMarker[] {
  const selectedOpIdSet = new Set(selectedOpIds);
  return createBoardMapMarkers(board)
    .filter((marker) => rowBelongsToSelectedOpId(marker.opId, selectedOpIdSet))
    .map((marker) => ({
      id: marker.id,
      markerType: marker.markerType,
      supportRequestType: marker.supportRequestType,
      title: marker.title ?? marker.memo ?? marker.markerType,
      summary: marker.memo ?? marker.markerType,
      occurredAt: marker.occurredAt,
      timeLabel: formatMarkerTimeLabel(marker.occurredAt),
      opLabel: marker.opId || undefined,
      reporterLabel: marker.reporterLabel ?? undefined,
      sourceLabel: marker.sourceLabel ?? undefined,
      coordinateLabel: `${marker.coordinates[1].toFixed(5)}N / ${marker.coordinates[0].toFixed(5)}E`,
      coordinates: marker.coordinates,
      memo: marker.memo,
      photoCount: marker.photoCount,
      photoThumbnailUrl: marker.photoThumbnailUrl,
    }));
}

export function emptyFeatureCollection(): ComparisonFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}

function createAreaFeature(
  row: Record<string, unknown>,
  index: number,
  incidentId: string,
  defaultAreaLevel = 'UNIT',
  areaVisualStylesByAreaId?: ReadonlyMap<string, AreaVisualStyle>,
): ComparisonFeature[] {
  const geometry = readGeometry(row);
  if (!geometry || geometry.type !== 'Polygon') return [];

  const opId = readRowOpId(row);
  const areaLevel = readString(row, 'areaLevel') ?? defaultAreaLevel;
  const status = readString(row, 'status') ?? '';
  const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId') ?? `area-${index}`;
  const visualStyle = areaVisualStylesByAreaId?.get(areaId) ?? getAreaVisualStyle(areaId);
  const areaKind = areaLevel === 'OVERALL' ? 'overall' : areaLevel === 'TEAM' ? 'team' : 'unit';

  return [
    {
      type: 'Feature',
      properties: {
        slot: 'area',
        entityId: areaId,
        incidentId,
        opId: opId ?? '',
        areaLevel,
        status,
        fillColor: visualStyle.fillColor,
        lineColor: visualStyle.lineColor,
        fillOpacity: Math.max(visualStyle.fillOpacity, 0.18),
        lineOpacity: 0.98,
        lineWidth: areaKind === 'overall' ? 2 : 1,
      },
      geometry,
    },
  ];
}

function createPathFeatureFromBoardPath(
  path: BoardMovementPath,
  incidentId: string,
  focusedOpId: string | null,
  selectedOpIds: string[],
): ComparisonFeature {
  const focused = !path.opId || path.opId === focusedOpId;
  const routeColor = path.routeColor ?? getOpColor(path.opId, selectedOpIds);

  return {
    type: 'Feature',
    properties: {
      slot: 'path',
      entityId: path.id,
      incidentId,
      opId: path.opId,
      focused: String(focused),
      color: routeColor,
      coreColor: getRouteCoreColor(routeColor),
      outerOpacity: focused ? 0.3 : 0.18,
      lineOpacity: focused ? 0.98 : 0.64,
      lineWidth: focused ? 4.6 : 2.8,
    },
    geometry: {
      type: 'LineString',
      coordinates: path.coordinates,
    },
  };
}

function createMarkerFeatureFromBoardMarker(
  marker: BoardMapMarker,
  incidentId: string,
  focusedOpId: string | null,
  selectedOpIds: string[],
): ComparisonFeature {
  const focused = !marker.opId || marker.opId === focusedOpId;

  return {
    type: 'Feature',
    properties: {
      slot: 'marker',
      entityId: marker.id,
      incidentId,
      opId: marker.opId,
      markerType: marker.markerType,
      focused: String(focused),
      color: focused ? getMarkerColor(marker.markerType) : getOpColor(marker.opId, selectedOpIds),
      radius: focused ? 10 : 8,
      opacity: focused ? 0.95 : 0.72,
    },
    geometry: {
      type: 'Point',
      coordinates: marker.coordinates,
    },
  };
}

function readGeometry(row: Record<string, unknown>): ComparisonGeometry | null {
  const geometry = row.geometry;
  if (isRecord(geometry) && isComparisonGeometry(geometry)) return geometry;

  const location = row.location;
  if (isRecord(location) && isComparisonGeometry(location)) return location;

  return null;
}

function isComparisonGeometry(value: Record<string, unknown>): value is ComparisonGeometry {
  if (value.type === 'Polygon' && Array.isArray(value.coordinates)) {
    const outerRing = value.coordinates[0];
    return Array.isArray(outerRing) && outerRing.filter(isPosition).length >= 4;
  }
  if (value.type === 'LineString' && Array.isArray(value.coordinates)) {
    return value.coordinates.filter(isPosition).length >= 2;
  }
  return value.type === 'Point' && isPosition(value.coordinates);
}

function isPosition(value: unknown): value is Position {
  return Array.isArray(value) && value.length >= 2 && typeof value[0] === 'number' && typeof value[1] === 'number';
}

function rowBelongsToSelectedOp(row: Record<string, unknown>, selectedOpIdSet: Set<string>) {
  if (selectedOpIdSet.size === 0) return false;
  const opId = readRowOpId(row);
  return opId !== null && selectedOpIdSet.has(opId);
}

function rowBelongsToSelectedOpId(opId: string, selectedOpIdSet: Set<string>) {
  if (selectedOpIdSet.size === 0) return false;
  return opId !== '' && selectedOpIdSet.has(opId);
}

function readRowOpId(row: Record<string, unknown>) {
  return readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
}

function createAreaVisualStylesByAreaId(areaRows: Record<string, unknown>[]) {
  const areaVisualStylesByAreaId = new Map<string, AreaVisualStyle>();

  areaRows.forEach((row) => {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!areaId || areaVisualStylesByAreaId.has(areaId)) return;
    areaVisualStylesByAreaId.set(areaId, getAreaVisualStyle(areaId));
  });

  return areaVisualStylesByAreaId;
}

function createRouteColorsByAssignee(
  areaRows: Record<string, unknown>[],
  areaVisualStylesByAreaId: ReadonlyMap<string, AreaVisualStyle>,
) {
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByAccountOpId = new Map<string, string>();

  areaRows.forEach((row) => {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const opId = readRowOpId(row);
    const assignedAccounts = row.assignedAccounts;
    if (!areaId || !Array.isArray(assignedAccounts)) return;
    const routeColor = areaVisualStylesByAreaId.get(areaId)?.lineColor ?? getAreaVisualStyle(areaId).lineColor;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const accountId = readAccountId(account);
      if (accountId) {
        routeColorsByAccountId.set(accountId, routeColor);
        if (opId) routeColorsByAccountOpId.set(createRouteColorAssigneeKey(opId, accountId), routeColor);
      }
    });
  });

  return {
    accountId: routeColorsByAccountId,
    accountOpId: routeColorsByAccountOpId,
  };
}

function createRouteAreaColorCandidates(
  areaRows: Record<string, unknown>[],
  areaVisualStylesByAreaId: ReadonlyMap<string, AreaVisualStyle>,
): RouteAreaColorCandidate[] {
  return areaRows.flatMap((row, index): RouteAreaColorCandidate[] => {
    const geometry = readGeometry(row);
    if (!geometry || geometry.type !== 'Polygon') return [];

    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId') ?? `area-${index}`;
    const opId = readRowOpId(row);
    const areaLevel = readString(row, 'areaLevel') ?? 'UNIT';
    const areaKind = areaLevel === 'OVERALL' ? 'overall' : areaLevel === 'TEAM' ? 'team' : 'unit';
    return [
      {
        id: areaId,
        opId,
        kind: areaKind,
        coordinates: geometry.coordinates[0],
        lineColor: areaVisualStylesByAreaId.get(areaId)?.lineColor ?? getAreaVisualStyle(areaId).lineColor,
      },
    ];
  });
}

function getOpColor(opId: string | null, selectedOpIds: string[]) {
  const index = opId ? Math.max(selectedOpIds.indexOf(opId), 0) : 0;
  return opColorPalette[index % opColorPalette.length];
}

function getMarkerColor(markerType: string) {
  const colors: Record<string, string> = {
    CLUE: '#f59e0b',
    PERSON_FOUND: '#dc2626',
    FIELD_CONDITION: '#0ea5e9',
    TERRAIN: '#64748b',
    SUPPORT_REQUEST: '#a855f7',
    NOTE: '#2563eb',
  };
  return colors[markerType] ?? '#0f766e';
}

function readAccountId(row: Record<string, unknown>) {
  return readString(row, 'accountId') ?? readString(row, 'account_id');
}

function formatMarkerTimeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(11, 16) || '-';
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}
