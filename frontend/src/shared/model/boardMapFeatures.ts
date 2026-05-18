import { areaColorTokens, type AreaColorToken } from '../constants/areaColorTokens';
import type { AreaBbox, CompletedAreaDraft } from './areaDraft';
import type { BoardMapMarker, BoardMovementPath, BoardPosition } from './boardMapSlots';

export type BoardMapMarkerType = BoardMapMarker['markerType'];
export type BoardMapPolygonGeometry = { type: 'Polygon'; coordinates: BoardPosition[][] };
export type BoardMapLineStringGeometry = { type: 'LineString'; coordinates: BoardPosition[] };
export type BoardMapPointGeometry = { type: 'Point'; coordinates: BoardPosition };
export type BoardMapGeometry = BoardMapPolygonGeometry | BoardMapLineStringGeometry | BoardMapPointGeometry;
export type BoardMapFeature = {
  type: 'Feature';
  properties: Record<string, string>;
  geometry: BoardMapGeometry;
};
export type BoardMapFeatureCollection = {
  type: 'FeatureCollection';
  features: BoardMapFeature[];
};

type DraftLineWidthByKind = Partial<Record<CompletedAreaDraft['kind'], number>>;

type SearchAreaDraftFeatureOptions = {
  incidentId?: string;
  status?: string;
  includeSlot?: boolean;
  minFillOpacity?: number;
  lineWidthByKind?: DraftLineWidthByKind;
};

type MovementPathFeatureOptions = {
  fallbackColor?: string;
  includeLabel?: boolean;
};

type ScopedRouteColorMaps = {
  accountId?: ReadonlyMap<string, string>;
  policePhoneId?: ReadonlyMap<string, string>;
};

const routeFallbackPalette = Object.values(areaColorTokens).map((token) => token.lineColor);

const defaultAreaColorByLevel: Record<CompletedAreaDraft['kind'], AreaColorToken> = {
  overall: 'areaColor001',
  unit: 'areaColor002',
  team: 'areaColor005',
};

const defaultDraftLineWidthByKind: Record<CompletedAreaDraft['kind'], number> = {
  overall: 3,
  unit: 2.6,
  team: 2.2,
};

export function createEmptyBoardMapFeatureCollection(): BoardMapFeatureCollection {
  return { type: 'FeatureCollection', features: [] };
}

export function createSearchAreaDraftFeatureCollection(
  drafts: CompletedAreaDraft[],
  options: SearchAreaDraftFeatureOptions = {},
): BoardMapFeatureCollection {
  const drawPriority: Record<CompletedAreaDraft['kind'], number> = {
    overall: 0,
    unit: 1,
    team: 2,
  };

  const drawOrderedDrafts = [...drafts].sort((leftDraft, rightDraft) => drawPriority[leftDraft.kind] - drawPriority[rightDraft.kind]);

  return {
    type: 'FeatureCollection',
    features: drawOrderedDrafts.map((draft) => createSearchAreaDraftFeature(draft, options)),
  };
}

export function createMovementPathFeatureCollection(
  movementPaths: BoardMovementPath[],
  activeOperationalPeriodId: string | null,
  options: MovementPathFeatureOptions = {},
): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: movementPaths
      .filter((path) => path.coordinates.length >= 2)
      .map((path) => createMovementPathFeature(path, activeOperationalPeriodId, options)),
  };
}

export function createMarkerFeatureCollection(markers: BoardMapMarker[]): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: markers.map((marker) => ({
      type: 'Feature',
      properties: {
        entityId: marker.id,
        markerType: marker.markerType,
        markerGlyph: markerTypeGlyph(marker.markerType),
        color: markerTypeColor(marker.markerType),
      },
      geometry: {
        type: 'Point',
        coordinates: marker.coordinates,
      },
    })),
  };
}

export function applyRouteColorsByAssignee(
  movementPaths: BoardMovementPath[],
  routeColorsByAccountId: ReadonlyMap<string, string>,
  routeColorsByPolicePhoneId: ReadonlyMap<string, string>,
  scopedRouteColorsByAssignee: ScopedRouteColorMaps = {},
): BoardMovementPath[] {
  return movementPaths.map((path) => ({
    ...path,
    routeColor:
      resolveRouteColor(path, routeColorsByAccountId, routeColorsByPolicePhoneId, scopedRouteColorsByAssignee) ??
      getFallbackRouteColor(path),
  }));
}

export function applyRouteColorsByPolicePhone(
  movementPaths: BoardMovementPath[],
  routeColorsByPolicePhoneId: ReadonlyMap<string, string>,
): BoardMovementPath[] {
  return applyRouteColorsByAssignee(movementPaths, new Map(), routeColorsByPolicePhoneId);
}

export function markerTypeColor(markerType: BoardMapMarkerType) {
  switch (markerType) {
    case 'CLUE':
      return '#ffb020';
    case 'PERSON_FOUND':
      return '#d63a3a';
    case 'FIELD_CONDITION':
      return '#64748b';
    case 'SUPPORT_REQUEST':
      return '#a855f7';
    case 'NOTE':
      return '#3b82f6';
    default:
      return '#334155';
  }
}

export function markerTypeGlyph(markerType: BoardMapMarkerType) {
  switch (markerType) {
    case 'CLUE':
      return '?';
    case 'PERSON_FOUND':
      return 'P';
    case 'FIELD_CONDITION':
      return '!';
    case 'SUPPORT_REQUEST':
      return '+';
    case 'NOTE':
      return 'N';
    default:
      return '.';
  }
}

export function getRouteCoreColor(routeColor: string | null | undefined): string {
  if (!routeColor) return '';

  const normalizedColor = normalizeHexColor(routeColor);
  return normalizedColor ?? routeColor;
}

function resolveRouteColor(
  path: BoardMovementPath,
  routeColorsByAccountId: ReadonlyMap<string, string>,
  routeColorsByPolicePhoneId: ReadonlyMap<string, string>,
  scopedRouteColorsByAssignee: ScopedRouteColorMaps,
) {
  if (path.routeColor) return path.routeColor;

  const scopedAccountRouteColor =
    path.opId && path.accountId
      ? scopedRouteColorsByAssignee.accountId?.get(createRouteColorAssigneeKey(path.opId, path.accountId))
      : undefined;
  if (scopedAccountRouteColor) return scopedAccountRouteColor;

  const scopedPolicePhoneRouteColor =
    path.opId && path.policePhoneId
      ? scopedRouteColorsByAssignee.policePhoneId?.get(createRouteColorAssigneeKey(path.opId, path.policePhoneId))
      : undefined;
  if (scopedPolicePhoneRouteColor) return scopedPolicePhoneRouteColor;

  const accountRouteColor = path.accountId ? routeColorsByAccountId.get(path.accountId) : undefined;
  if (accountRouteColor) return accountRouteColor;

  return path.policePhoneId ? routeColorsByPolicePhoneId.get(path.policePhoneId) ?? null : null;
}

export function createRouteColorAssigneeKey(opId: string, assigneeId: string) {
  return `${opId}:${assigneeId}`;
}

function getFallbackRouteColor(path: BoardMovementPath) {
  const routeKey = path.policePhoneId ?? path.accountId ?? path.id;
  return routeFallbackPalette[hashString(routeKey) % routeFallbackPalette.length];
}

function hashString(value: string) {
  return Array.from(value).reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0, 17);
}

function createSearchAreaDraftFeature(
  draft: CompletedAreaDraft,
  options: SearchAreaDraftFeatureOptions,
): BoardMapFeature {
  const visualStyle = areaColorTokens[draft.colorToken] ?? areaColorTokens[defaultAreaColorByLevel[draft.kind]];
  const lineWidthByKind = { ...defaultDraftLineWidthByKind, ...options.lineWidthByKind };
  const properties: Record<string, string> = {
    entityId: draft.areaId,
    areaLevel: draft.kind.toUpperCase(),
    status: options.status ?? 'ACTIVE',
    version: '1',
    fillColor: visualStyle.fillColor,
    lineColor: visualStyle.lineColor,
    fillOpacity: String(Math.max(visualStyle.fillOpacity, options.minFillOpacity ?? 0.18)),
    lineWidth: String(lineWidthByKind[draft.kind]),
    lineOpacity: '0.98',
  };

  if (options.includeSlot) {
    properties.slot = draft.kind === 'overall' ? 'overall_search_area' : 'area';
  }
  if (options.incidentId) {
    properties.incidentId = options.incidentId;
  }
  if (draft.bbox) {
    properties.bbox = stringifyBbox(draft.bbox);
  }

  return {
    type: 'Feature',
    properties,
    geometry: {
      type: 'Polygon',
      coordinates: [draft.coordinates],
    },
  };
}

function stringifyBbox(bbox: AreaBbox): string {
  return JSON.stringify(bbox);
}

function createMovementPathFeature(
  path: BoardMovementPath,
  activeOperationalPeriodId: string | null,
  options: MovementPathFeatureOptions,
): BoardMapFeature {
  const properties: Record<string, string> = {
    slot: 'path',
    entityId: path.id,
    opId: path.opId,
    policePhoneId: path.policePhoneId ?? '',
    accountId: path.accountId ?? '',
    freshnessStatus: path.freshnessStatus,
    deviceColor: path.routeColor ?? options.fallbackColor ?? '',
    routeCoreColor: getRouteCoreColor(path.routeColor ?? options.fallbackColor),
    movementType: path.movementType,
    isActiveOp: String(path.opId === activeOperationalPeriodId),
    startedAt: path.startedAt,
    endedAt: path.endedAt ?? '',
  };

  if (options.includeLabel) {
    properties.label = path.label;
  }

  return {
    type: 'Feature',
    properties,
    geometry: {
      type: 'LineString',
      coordinates: path.coordinates,
    },
  };
}

function normalizeHexColor(color: string): string | null {
  const trimmedColor = color.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(trimmedColor)) {
    return trimmedColor;
  }

  if (/^#[0-9a-fA-F]{3}$/.test(trimmedColor)) {
    const red = trimmedColor[1];
    const green = trimmedColor[2];
    const blue = trimmedColor[3];
    return `#${red}${red}${green}${green}${blue}${blue}`;
  }

  return null;
}
