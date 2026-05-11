import { areaColorTokens, type AreaColorToken } from '../constants/areaColorTokens';
import type { CompletedAreaDraft } from './areaDraft';
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
      .filter((path) => path.coordinates.length >= 2 && path.routeColor)
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

export function applyRouteColorsByPolicePhone(
  movementPaths: BoardMovementPath[],
  routeColorsByPolicePhoneId: ReadonlyMap<string, string>,
): BoardMovementPath[] {
  return movementPaths.map((path) => ({
    ...path,
    routeColor: path.routeColor ?? (path.policePhoneId ? routeColorsByPolicePhoneId.get(path.policePhoneId) ?? null : null),
  }));
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

  return {
    type: 'Feature',
    properties,
    geometry: {
      type: 'Polygon',
      coordinates: [draft.coordinates],
    },
  };
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
    deviceColor: path.routeColor ?? options.fallbackColor ?? '',
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
