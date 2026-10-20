import { areaColorTokens, type AreaColorToken } from '../constants/areaColorTokens';
import type { AreaBbox, CompletedAreaDraft } from './areaDraft';
import type {
  BoardMapMarker,
  BoardMovementPath,
  BoardPolicePhoneFreshnessStatus,
  BoardPosition,
} from './boardMapSlots';

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
};

const routeFallbackPalette = Object.values(areaColorTokens).map((token) => token.lineColor);

const defaultAreaColorByLevel: Record<CompletedAreaDraft['kind'], AreaColorToken> = {
  overall: 'AREA_BLUE_01',
  unit: 'AREA_ORANGE_01',
  team: 'AREA_CYAN_01',
};

const defaultDraftLineWidthByKind: Record<CompletedAreaDraft['kind'], number> = {
  overall: 3,
  unit: 2.6,
  team: 2.2,
};

const policePhoneFreshnessColors: Record<BoardPolicePhoneFreshnessStatus, string> = {
  ONLINE: '#22c55e',
  STALE: '#f59e0b',
  LOST: '#ef4444',
  UNKNOWN: '#94a3b8',
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

  const drawOrderedDrafts = [...drafts].sort(
    (leftDraft, rightDraft) => drawPriority[leftDraft.kind] - drawPriority[rightDraft.kind],
  );

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

// 화면별로 유지한다. 전체 배열의 순서는 보존하고 변하지 않은 도형 생성은 생략한다.
// 지도 처리 비용은 도형 수에 비례하므로 같은 경로에서 표시 속성이 같고 끝점이 이어지는 구간을 선 하나로 잇는다.
// 구간 시각 등 원본 값은 경로 데이터에 남고, 경로 재생은 handover-timeline의 좌표 시각을 쓴다.
export function createMovementPathFeatureMapper(options: MovementPathFeatureOptions = {}) {
  let previousOpId: string | null | undefined;
  let cache = new WeakMap<BoardMovementPath, { order: number; segments: BoardMovementPath[]; feature: BoardMapFeature }>();
  return (paths: BoardMovementPath[], activeOpId: string | null): BoardMapFeatureCollection => {
    if (previousOpId !== activeOpId || paths.length === 0) {
      cache = new WeakMap();
      previousOpId = activeOpId;
    }
    const features: BoardMapFeature[] = [];
    let group = 0;
    for (let start = 0; start < paths.length; group += 1) {
      const pathId = paths[start].searchPathId ?? paths[start].id;
      let end = start + 1;
      while (end < paths.length && (paths[end].searchPathId ?? paths[end].id) === pathId) end += 1;
      let run: BoardMovementPath[] = [];
      let runOrder = 0;
      for (let index = start; index < end; index += 1) {
        const path = paths[index];
        if (path.coordinates.length < 2) continue;
        if (run.length > 0 && !continuesRun(run[run.length - 1], path, activeOpId)) {
          features.push(readRunFeature(run, runOrder, activeOpId));
          run = [];
        }
        // 경로 사이에는 정수, 같은 경로의 구간 사이에는 그 안의 소수로 순서를 지정한다.
        // 한 경로에 구간이 추가돼도 다른 경로의 그리기 순서 값은 변하지 않는다.
        if (run.length === 0) runOrder = group + (index - start) / (end - start + 1);
        run.push(path);
      }
      if (run.length > 0) features.push(readRunFeature(run, runOrder, activeOpId));
      start = end;
    }
    return { type: 'FeatureCollection', features };
  };

  function readRunFeature(run: BoardMovementPath[], order: number, activeOpId: string | null) {
    const cached = cache.get(run[0]);
    if (cached?.order === order && cached.segments.length === run.length && cached.segments.every((segment, index) => segment === run[index])) {
      return cached.feature;
    }
    const feature = createMovementPathFeature(run[0], activeOpId, options);
    if (run.length > 1) {
      // 이어지는 구간의 첫 좌표는 앞 구간의 끝 좌표와 같으므로 한 번만 담는다.
      feature.geometry = {
        type: 'LineString',
        coordinates: [...run[0].coordinates, ...run.slice(1).flatMap((segment) => segment.coordinates.slice(1))],
      };
    }
    feature.properties.drawOrder = String(order);
    cache.set(run[0], { order, segments: run, feature });
    return feature;
  }
}

// 도형 속성을 만드는 값(createMovementPathFeature)이 모두 같아야 한 선으로 그려도 표시가 같다.
function continuesRun(previous: BoardMovementPath, next: BoardMovementPath, activeOpId: string | null) {
  const end = previous.coordinates[previous.coordinates.length - 1];
  const [lng, lat] = next.coordinates[0];
  return end[0] === lng
    && end[1] === lat
    && previous.routeColor === next.routeColor
    && previous.movementType === next.movementType
    && previous.freshnessStatus === next.freshnessStatus
    && (previous.opId === activeOpId) === (next.opId === activeOpId)
    && previous.searchPathVersion === next.searchPathVersion;
}

export function createMovementCurrentPositionFeatureCollection(
  movementPaths: BoardMovementPath[],
  activeOperationalPeriodId: string | null,
  options: MovementPathFeatureOptions = {},
): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: Array.from(selectLatestMovementPathsByAssignee(movementPaths).values()).map((path) =>
      createMovementCurrentPositionFeature(path, activeOperationalPeriodId, options),
    ),
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
  scopedRouteColorsByAssignee: ScopedRouteColorMaps = {},
): BoardMovementPath[] {
  return movementPaths.map((path) => ({
    ...path,
    routeColor:
      resolveRouteColor(path, routeColorsByAccountId, scopedRouteColorsByAssignee) ??
      getFallbackRouteColor(path),
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

export function getRouteCoreColor(routeColor: string | null | undefined): string {
  if (!routeColor) return '';

  const normalizedColor = normalizeHexColor(routeColor);
  return normalizedColor ?? routeColor;
}

export function getPolicePhoneFreshnessColor(freshnessStatus: BoardPolicePhoneFreshnessStatus): string {
  return policePhoneFreshnessColors[freshnessStatus];
}

function resolveRouteColor(
  path: BoardMovementPath,
  routeColorsByAccountId: ReadonlyMap<string, string>,
  scopedRouteColorsByAssignee: ScopedRouteColorMaps,
) {
  if (path.routeColor) return path.routeColor;

  const scopedAccountRouteColor =
    path.opId
      ? scopedRouteColorsByAssignee.accountId?.get(createRouteColorAssigneeKey(path.opId, path.accountId))
      : undefined;
  if (scopedAccountRouteColor) return scopedAccountRouteColor;

  const accountRouteColor = routeColorsByAccountId.get(path.accountId);
  if (accountRouteColor) return accountRouteColor;

  return null;
}

export function createRouteColorAssigneeKey(opId: string, assigneeId: string) {
  return `${opId}:${assigneeId}`;
}

function getFallbackRouteColor(path: BoardMovementPath) {
  return routeFallbackPalette[hashString(path.accountId) % routeFallbackPalette.length];
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
    opId: draft.opId ?? '',
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
  // 지도 레이어(색, 필터, 순서), 증분 갱신 ID, 범례 필터, 측정 코드가 읽는 값만 담는다.
  const properties: Record<string, string> = {
    entityId: path.id,
    freshnessStatus: path.freshnessStatus,
    deviceColor: path.routeColor ?? options.fallbackColor ?? '',
    routeCoreColor: getRouteCoreColor(path.routeColor ?? options.fallbackColor),
    movementType: path.movementType,
    isActiveOp: String(path.opId === activeOperationalPeriodId),
  };

  if (path.searchPathId) properties.searchPathId = path.searchPathId;
  if (path.searchPathVersion != null) properties.searchPathVersion = String(path.searchPathVersion);

  return {
    type: 'Feature',
    properties,
    geometry: {
      type: 'LineString',
      coordinates: path.coordinates,
    },
  };
}

function createMovementCurrentPositionFeature(
  path: BoardMovementPath,
  activeOperationalPeriodId: string | null,
  options: MovementPathFeatureOptions,
): BoardMapFeature {
  const currentPosition = path.coordinates[path.coordinates.length - 1];
  const properties: Record<string, string> = {
    slot: 'path',
    geometryType: 'currentPosition',
    entityId: `${path.id}:current-position`,
    pathId: path.id,
    opId: path.opId,
    accountId: path.accountId,
    freshnessStatus: path.freshnessStatus,
    currentPositionColor: getPolicePhoneFreshnessColor(path.freshnessStatus),
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
      type: 'Point',
      coordinates: currentPosition,
    },
  };
}

function selectLatestMovementPathsByAssignee(movementPaths: BoardMovementPath[]) {
  const pathsByAssignee = new Map<string, BoardMovementPath>();

  movementPaths
    .filter((path) => path.coordinates.length > 0)
    .forEach((path) => {
      const assigneeKey = path.accountId;
      const previousPath = pathsByAssignee.get(assigneeKey);
      if (!previousPath || compareMovementPathRecency(path, previousPath) > 0) {
        pathsByAssignee.set(assigneeKey, path);
      }
    });

  return pathsByAssignee;
}

function compareMovementPathRecency(leftPath: BoardMovementPath, rightPath: BoardMovementPath) {
  const leftOpenPriority = leftPath.endedAt ? 0 : 1;
  const rightOpenPriority = rightPath.endedAt ? 0 : 1;
  if (leftOpenPriority !== rightOpenPriority) {
    return leftOpenPriority - rightOpenPriority;
  }

  return readMovementPathTimestamp(leftPath) - readMovementPathTimestamp(rightPath);
}

function readMovementPathTimestamp(path: BoardMovementPath) {
  const timestamp = Date.parse(path.endedAt ?? path.startedAt);
  return Number.isFinite(timestamp) ? timestamp : 0;
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
