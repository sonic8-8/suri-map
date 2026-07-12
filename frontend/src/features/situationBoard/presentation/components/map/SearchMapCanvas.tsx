import { useQueryClient } from '@tanstack/react-query';
import { Image as ImageIcon } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useCallback } from 'react';
import { createPortal } from 'react-dom';
import maplibregl, {
  type FilterSpecification,
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';
import { ApiHttpError, createIdempotencyKey } from '../../../../../shared/api/client';
import { getVWorldApiKey } from '../../../../../shared/config';
import { getMarkerLegendColor } from '../../../../../shared/constants/markerLegendColors';
import { incidentBoardQueryKeys } from '../../../../board/api/incidentBoardApi';
import {
  useCreateMarkerMutation,
  useUpdateMarkerMutation,
  type UpdateMarkerRequest,
} from '../../../../marker/api/markerCommandApi';
import {
  useCreateManualSearchPathMutation,
  type ManualSearchPathPointInput,
} from '../../../../path/api/searchPathApi';
import {
  handoverApi,
  handoverQueryKeys,
  type HandoverMemoTargetType,
} from '../../../../operationalPeriod/api/handoverApi';
import {
  AreaEditMapCanvas,
  type AreaEditMapCanvasProps,
} from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import {
  HandoverComparisonMap,
  type HandoverComparisonMapSharedProps,
} from '../../../../handover/presentation/components/HandoverComparisonMap';
import {
  applySearchAreaStatuses,
  EMPTY_OPERATIONAL_FEATURE_COLLECTION,
  createOperationalFeatureCollectionBoundsSignature,
  getAssignedSearchAreaBounds,
  getSearchAreaBoundsById,
  resolveInitialMapView,
  toBounds,
  type Position,
} from './searchMapCanvasData';
import {
  createVWorldBaseStyle,
  V_WORLD_BASE_LAYER_ID,
  V_WORLD_BASE_OPACITY,
  V_WORLD_MAX_ZOOM,
} from '../../../../../shared/map/vworldBaseMap';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import {
  createMovementCurrentPositionFeatureCollection,
  createMovementPathFeatureCollection,
  createSearchAreaDraftFeatureCollection,
  type BoardMapFeatureCollection,
} from '../../../../../shared/model/boardMapFeatures';
import type {
  MovementPath,
  OperationalPeriod,
  PolicePhoneLegendFilterId,
  RecentMarker,
  SearchAreaLegendFilterId,
} from '../../constants/mockSituationBoard';
import {
  clearMarkerElements,
  getNearestMarkerIdAtPoint,
  getRenderedMarkerIdAtPoint,
  removeMarkerPopup,
  raiseMarkerLayer,
  syncMarkerElements,
  syncMarkerElementsWhenAvailable,
  syncMarkerPopups,
  type MarkerInstance,
  type MarkerInteractionHandlers,
} from './boardMarkerLayer';
import { MarkerGlyph, markerTypeGlyphName, type MarkerGlyphName } from '../../../../../shared/ui/markerGlyph/MarkerGlyph';
import { SearchAreaInspectorCard } from './SearchAreaInspectorCard';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { RouteEditorPanel, type ManualRouteMarkerType } from './RouteEditorPanel';
import { addRouteEditorLayers, syncRouteEditorDraft } from './routeEditorLayer';
import {
  filterMovementPathsByPolicePhoneLegendFilters,
  filterSearchAreasByLegendFilters,
} from './searchMapLayerFilters';
import styles from './SearchMapCanvas.module.css';

const DEFAULT_GWANGJU_CENTER: [number, number] = [126.8325, 35.1547];
const GWANGJU_BBOX: [number, number, number, number] = [126.647507, 35.052595, 127.017482, 35.256837];
const DEFAULT_FIT_PADDING = 44;
const FOCUSED_SEARCH_AREA_FIT_PADDING = 72;
const FOCUSED_SEARCH_AREA_FIT_MAX_ZOOM = 16;
const MARKER_SELECTED_POPUP_OFFSET_PX = 60;
const MANUAL_ROUTE_SAMPLE_INTERVAL_MS = 5_000;
const MANUAL_ROUTE_NATURAL_STEP_M = 7;
const MANUAL_ROUTE_MAX_POINTS = 120;
const OVERALL_SEARCH_AREA_SOURCE_ID = 'operational-overall_search_area';
const SEARCH_AREA_FILL_LAYER_ID = 'operational-overall_search_area-fill';
const SEARCH_AREA_COMPLETED_HATCH_PATTERN_ID = 'operational-completed-search-area-hatch';
const SEARCH_AREA_COMPLETED_HATCH_LAYER_ID = 'operational-overall_search_area-completed-hatch';
const SEARCH_AREA_LINE_LAYER_IDS = {
  overall: 'operational-overall_search_area-line-overall',
  unit: 'operational-overall_search_area-line-unit',
  team: 'operational-overall_search_area-line-team',
} as const;
const SEARCH_AREA_LINE_LAYER_ORDER = [
  SEARCH_AREA_LINE_LAYER_IDS.overall,
  SEARCH_AREA_LINE_LAYER_IDS.unit,
  SEARCH_AREA_LINE_LAYER_IDS.team,
] as const;
const SEARCH_AREA_RENDER_LAYER_IDS = [
  SEARCH_AREA_FILL_LAYER_ID,
  SEARCH_AREA_COMPLETED_HATCH_LAYER_ID,
  ...SEARCH_AREA_LINE_LAYER_ORDER,
];
const SEARCH_AREA_LINE_LAYER_STYLES: Array<{
  id: (typeof SEARCH_AREA_LINE_LAYER_ORDER)[number];
  areaLevel: SearchAreaLevel;
  lineWidth: number;
  lineOpacity: number;
  lineGapWidth?: number;
  lineDasharray?: [number, number];
}> = [
  {
    id: SEARCH_AREA_LINE_LAYER_IDS.overall,
    areaLevel: 'OVERALL',
    lineWidth: 2,
    lineOpacity: 0.92,
    lineDasharray: [2, 1.2],
  },
  {
    id: SEARCH_AREA_LINE_LAYER_IDS.unit,
    areaLevel: 'UNIT',
    lineWidth: 1,
    lineGapWidth: 3,
    lineOpacity: 0.98,
  },
  {
    id: SEARCH_AREA_LINE_LAYER_IDS.team,
    areaLevel: 'TEAM',
    lineWidth: 1,
    lineOpacity: 0.98,
  },
];
const MOVEMENT_PATH_SOURCE_ID = 'operational-movement-path';
const MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID = 'operational-movement-path-compare-highlight';
const MOVEMENT_PATH_COMPARE_LAYER_ID = 'operational-movement-path-compare';
const MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID = 'operational-movement-path-vehicle-glow';
const MOVEMENT_PATH_VEHICLE_LAYER_ID = 'operational-movement-path-vehicle';
const MOVEMENT_PATH_FOOT_GLOW_LAYER_ID = 'operational-movement-path-foot-glow';
const MOVEMENT_PATH_FOOT_LAYER_ID = 'operational-movement-path-foot';
const MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID = 'operational-movement-path-unknown-glow';
const MOVEMENT_PATH_UNKNOWN_LAYER_ID = 'operational-movement-path-unknown';
const MOVEMENT_CURRENT_POSITION_SOURCE_ID = 'operational-movement-current-position';
const MOVEMENT_CURRENT_POSITION_VEHICLE_GLOW_LAYER_ID = 'operational-movement-current-position-vehicle-glow';
const MOVEMENT_CURRENT_POSITION_VEHICLE_LAYER_ID = 'operational-movement-current-position-vehicle';
const MOVEMENT_CURRENT_POSITION_VEHICLE_STATUS_LAYER_ID = 'operational-movement-current-position-vehicle-status';
const MOVEMENT_CURRENT_POSITION_FOOT_GLOW_LAYER_ID = 'operational-movement-current-position-foot-glow';
const MOVEMENT_CURRENT_POSITION_FOOT_LAYER_ID = 'operational-movement-current-position-foot';
const MOVEMENT_CURRENT_POSITION_FOOT_STATUS_LAYER_ID = 'operational-movement-current-position-foot-status';
const MOVEMENT_CURRENT_POSITION_UNKNOWN_GLOW_LAYER_ID = 'operational-movement-current-position-unknown-glow';
const MOVEMENT_CURRENT_POSITION_UNKNOWN_LAYER_ID = 'operational-movement-current-position-unknown';
const MOVEMENT_CURRENT_POSITION_UNKNOWN_STATUS_LAYER_ID = 'operational-movement-current-position-unknown-status';
const INITIAL_MAP_FALLBACK_ZOOM = 12;
const DEFAULT_MARKER_POPUP_COLOR = '#64748b';

type OperationalFeatureCollection = BoardMapFeatureCollection;
type SearchAreaLevel = 'OVERALL' | 'UNIT' | 'TEAM';
type MapMemoSubmitStatus = 'idle' | 'editing' | 'saving' | 'saved' | 'error';
type MapMemoTarget = {
  targetType: HandoverMemoTargetType;
  targetId: string;
  opId: string;
};
type MapMemoSubmitOptions = {
  closeComposerOnSuccess?: boolean;
};
type InitialMapResolution =
  | { state: 'overall-ready'; bounds: LngLatBoundsLike; overallSearchArea: OperationalFeatureCollection }
  | { state: 'fallback'; bounds: LngLatBoundsLike | null };

export type InitialMapState = InitialMapResolution['state'];

export function canCorrectReferenceMarker(marker: Pick<RecentMarker, 'source' | 'version'> | null | undefined) {
  return (
    Boolean(marker) &&
    (marker?.source === 'MOCK_SEED' || marker?.source === 'SYSTEM') &&
    typeof marker?.version === 'number' &&
    Number.isFinite(marker.version)
  );
}

export function createReferenceMarkerCorrectionRequest(
  marker: Pick<RecentMarker, 'version'>,
  coordinates: [number, number],
): UpdateMarkerRequest {
  return {
    version: marker.version ?? 0,
    location: {
      type: 'Point',
      coordinates,
    },
  };
}

function createReferenceMarkerCorrectionIdempotencyKey(markerId: string) {
  const suffix =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `web-reference-marker-correction:${markerId}:${suffix}`;
}

function createUuid() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const randomValue = (Math.random() * 16) | 0;
    const value = character === 'x' ? randomValue : (randomValue & 0x3) | 0x8;
    return value.toString(16);
  });
}

function toDatetimeLocalValue(date: Date) {
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

function parseDatetimeLocalValue(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function createManualSearchPathPoints(
  coordinates: Position[],
  startedAt: Date,
): ManualSearchPathPointInput[] {
  return coordinates.map(([lon, lat], index) => {
    const coordinate: Position = [roundManualRouteCoordinate(lon), roundManualRouteCoordinate(lat)];
    const previousCoordinate = coordinates[index - 1]
      ? normalizeManualRouteCoordinate(coordinates[index - 1])
      : null;
    return {
      pointId: createUuid(),
      lon: coordinate[0],
      lat: coordinate[1],
      speedMps: previousCoordinate ? Math.min(distanceMeters(previousCoordinate, coordinate) / 5, 2.4) : 0,
      horizontalAccuracyM: 5,
      clientTs: new Date(startedAt.getTime() + MANUAL_ROUTE_SAMPLE_INTERVAL_MS * index).toISOString(),
    };
  });
}

export function interpolateManualRouteCoordinates(anchors: Position[]): Position[] {
  if (anchors.length <= 1) {
    return anchors;
  }

  const segmentDistances = anchors.slice(1).map((anchor, index) => distanceMeters(anchors[index], anchor));
  const totalDistance = segmentDistances.reduce((sum, distance) => sum + distance, 0);
  const targetStepMeters = Math.max(MANUAL_ROUTE_NATURAL_STEP_M, totalDistance / (MANUAL_ROUTE_MAX_POINTS - 1));
  const coordinates: Position[] = [anchors[0]];

  anchors.slice(1).forEach((end, index) => {
    const start = anchors[index];
    const distance = segmentDistances[index] ?? 0;
    const steps = Math.max(1, Math.ceil(distance / targetStepMeters));
    for (let step = 1; step <= steps; step += 1) {
      const ratio = step / steps;
      coordinates.push([
        start[0] + (end[0] - start[0]) * ratio,
        start[1] + (end[1] - start[1]) * ratio,
      ]);
    }
  });

  if (coordinates.length <= MANUAL_ROUTE_MAX_POINTS) {
    return coordinates;
  }

  return downsampleRouteCoordinates(coordinates, MANUAL_ROUTE_MAX_POINTS);
}

function calculateManualRouteEndedAt(startedAt: Date, pointCount: number) {
  return new Date(startedAt.getTime() + Math.max(pointCount, 1) * MANUAL_ROUTE_SAMPLE_INTERVAL_MS);
}

function normalizeManualRouteCoordinate([lon, lat]: Position): Position {
  return [roundManualRouteCoordinate(lon), roundManualRouteCoordinate(lat)];
}

function roundManualRouteCoordinate(value: number) {
  return Number(value.toFixed(6));
}

function distanceMeters(start: Position, end: Position) {
  const earthRadiusM = 6_371_000;
  const startLat = toRadians(start[1]);
  const endLat = toRadians(end[1]);
  const deltaLat = toRadians(end[1] - start[1]);
  const deltaLon = toRadians(end[0] - start[0]);
  const haversine =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLon / 2) ** 2;
  return 2 * earthRadiusM * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
}

function toRadians(value: number) {
  return (value * Math.PI) / 180;
}

function downsampleRouteCoordinates(coordinates: Position[], maxPoints: number): Position[] {
  const lastIndex = coordinates.length - 1;
  return Array.from({ length: maxPoints }, (_, index) => {
    if (index === 0) return coordinates[0];
    if (index === maxPoints - 1) return coordinates[lastIndex];
    return coordinates[Math.round((lastIndex * index) / (maxPoints - 1))];
  });
}

function createMarkerPopupStyle(
  marker: RecentMarker,
  point: { x: number; y: number },
): CSSProperties & { '--marker-color': string } {
  return {
    '--marker-color': getMarkerLegendColor(marker.markerType, marker.supportRequestType) || DEFAULT_MARKER_POPUP_COLOR,
    left: `${point.x}px`,
    top: `${point.y}px`,
    transform: `translate(-50%, calc(-100% - ${MARKER_SELECTED_POPUP_OFFSET_PX}px))`,
  };
}

function getMarkerPopupGlyphName(marker: RecentMarker): MarkerGlyphName {
  if (marker.markerType === 'SUPPORT_REQUEST') {
    if (marker.supportRequestType === 'DRONE') return 'drone';
    if (marker.supportRequestType === 'POLICE_DOG') return 'dog';
    return 'handHelping';
  }

  return markerTypeGlyphName(marker.markerType);
}

function getMarkerPopupAriaLabel(marker: RecentMarker) {
  return `${marker.markerTypeLabel ?? marker.summary ?? '마커'} 정보`;
}

function getMarkerPopupContent(marker: RecentMarker) {
  const content = marker.memo?.trim();
  return content || null;
}

function getMarkerPopupOpLabel(marker: RecentMarker) {
  return marker.opLabel?.trim() || 'OP 확인 전';
}

function getMarkerPopupAuthorLabel(marker: RecentMarker) {
  return marker.reporterLabel?.trim() || '작성자 확인 전';
}

function getMarkerPopupDateTimeLabel(marker: RecentMarker) {
  const occurredAt = marker.occurredAt?.trim();
  if (!occurredAt) {
    return marker.timeLabel || '시간 확인 전';
  }

  const date = new Date(occurredAt);
  if (Number.isNaN(date.getTime())) {
    return occurredAt;
  }

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function getMarkerPopupTypeLabel(marker: RecentMarker) {
  return marker.markerTypeLabel?.trim() || marker.summary?.trim() || '마커';
}

function hasMarkerPopupPhoto(marker: RecentMarker) {
  return Boolean(marker.photoThumbnailUrl) || (typeof marker.photoCount === 'number' && marker.photoCount > 0);
}

function getMarkerPopupPhotoCountLabel(marker: RecentMarker) {
  return typeof marker.photoCount === 'number' && marker.photoCount > 0 ? `사진 ${marker.photoCount}장` : '사진 포함';
}

function createMapMemoTargetKey(targetType: HandoverMemoTargetType, targetId: string) {
  return `${targetType}:${targetId}`;
}

function findSearchAreaNode(area: SearchAreaTreeNode, targetId: string): SearchAreaTreeNode | null {
  if (area.id === targetId) {
    return area;
  }

  for (const childArea of area.children ?? []) {
    const foundArea = findSearchAreaNode(childArea, targetId);
    if (foundArea) {
      return foundArea;
    }
  }

  return null;
}

function resolveSearchAreaMemoOpId(
  searchAreaTree: SearchAreaTreeNode,
  searchAreaId: string | null,
  activeOperationalPeriodId: string | null,
) {
  if (!searchAreaId) {
    return null;
  }

  const searchArea = findSearchAreaNode(searchAreaTree, searchAreaId);
  return searchArea?.opId?.trim() || activeOperationalPeriodId;
}

export function resolveSearchAreaPolicePhoneId(
  searchArea: SearchAreaTreeNode | null,
  targetOpId: string | null = null,
): string | null {
  if (!searchArea) {
    return null;
  }

  const searchAreaOpId = searchArea.opId?.trim();
  if (targetOpId && searchAreaOpId && searchAreaOpId !== targetOpId) {
    return null;
  }

  const directPhoneId = (searchArea.assignedAccounts ?? [])
    .map((account) => account.policePhoneId?.trim())
    .find((policePhoneId): policePhoneId is string => Boolean(policePhoneId));
  if (directPhoneId) {
    return directPhoneId;
  }

  for (const childArea of searchArea.children ?? []) {
    const childPhoneId = resolveSearchAreaPolicePhoneId(childArea, targetOpId);
    if (childPhoneId) {
      return childPhoneId;
    }
  }

  return null;
}

function resolveMarkerMemoOpId(marker: RecentMarker | null, activeOperationalPeriodId: string | null) {
  return marker?.opId?.trim() || activeOperationalPeriodId;
}

export type LayerVisibility = {
  vehiclePath: boolean;
  footPath: boolean;
  searchArea: boolean;
  marker: boolean;
};

function addGeoJsonSource(map: maplibregl.Map, sourceId: string, data: string | OperationalFeatureCollection) {
  if (map.getSource(sourceId)) {
    return;
  }
  map.addSource(sourceId, {
    type: 'geojson',
    data,
  });
}

function setOperationalGeoJsonSourceData(map: maplibregl.Map, sourceId: string, data: OperationalFeatureCollection) {
  const source = map.getSource(sourceId);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(data);
}

export function syncOperationalGeoJsonSourceDataWhenAvailable(
  map: maplibregl.Map,
  sourceId: string,
  data: OperationalFeatureCollection,
) {
  if (map.getSource(sourceId)) {
    setOperationalGeoJsonSourceData(map, sourceId, data);
    return undefined;
  }

  const syncWhenLoaded = () => {
    setOperationalGeoJsonSourceData(map, sourceId, data);
  };
  map.once('load', syncWhenLoaded);
  return () => {
    map.off('load', syncWhenLoaded);
  };
}

function syncSearchAreaSourceData(
  map: maplibregl.Map,
  searchAreas: OperationalFeatureCollection,
  isVisible: boolean,
) {
  setOperationalGeoJsonSourceData(
    map,
    OVERALL_SEARCH_AREA_SOURCE_ID,
    isVisible ? searchAreas : EMPTY_OPERATIONAL_FEATURE_COLLECTION,
  );
}

function syncSearchAreaSourceDataWhenAvailable(
  map: maplibregl.Map,
  searchAreas: OperationalFeatureCollection,
  isVisible: boolean,
) {
  if (map.getSource(OVERALL_SEARCH_AREA_SOURCE_ID)) {
    syncSearchAreaSourceData(map, searchAreas, isVisible);
    return;
  }

  if (!map.loaded()) {
    map.once('load', () => syncSearchAreaSourceData(map, searchAreas, isVisible));
  }
}

function hasSearchAreaLayers(map: maplibregl.Map) {
  return SEARCH_AREA_RENDER_LAYER_IDS.every((layerId) => Boolean(map.getLayer(layerId)));
}

function addCompletedSearchAreaHatchPattern(map: maplibregl.Map) {
  if (map.hasImage(SEARCH_AREA_COMPLETED_HATCH_PATTERN_ID)) {
    return;
  }

  const canvas = document.createElement('canvas');
  canvas.width = 16;
  canvas.height = 16;
  const context = canvas.getContext('2d');
  if (!context) {
    return;
  }

  context.clearRect(0, 0, canvas.width, canvas.height);
  context.strokeStyle = 'rgba(15, 23, 42, 0.46)';
  context.lineWidth = 1;
  context.beginPath();
  context.moveTo(-4, 16);
  context.lineTo(16, -4);
  context.moveTo(0, 20);
  context.lineTo(20, 0);
  context.stroke();

  map.addImage(SEARCH_AREA_COMPLETED_HATCH_PATTERN_ID, context.getImageData(0, 0, canvas.width, canvas.height));
}

function addLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) {
    return;
  }
  map.addLayer(layer);
}

function applyBaseRasterOpacity(map: maplibregl.Map) {
  if (!map.getLayer(V_WORLD_BASE_LAYER_ID)) {
    return;
  }
  map.setPaintProperty(V_WORLD_BASE_LAYER_ID, 'raster-opacity', V_WORLD_BASE_OPACITY);
}

function syncBaseMapOpacity(map: maplibregl.Map) {
  applyBaseRasterOpacity(map);
}

function addSearchAreaLayers(map: maplibregl.Map, overallSearchArea: OperationalFeatureCollection) {
  addGeoJsonSource(map, OVERALL_SEARCH_AREA_SOURCE_ID, overallSearchArea);
  addCompletedSearchAreaHatchPattern(map);

  addLayer(map, {
    id: SEARCH_AREA_FILL_LAYER_ID,
    type: 'fill',
    source: OVERALL_SEARCH_AREA_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': 0.12,
    },
  });

  addLayer(map, {
    id: SEARCH_AREA_COMPLETED_HATCH_LAYER_ID,
    type: 'fill',
    source: OVERALL_SEARCH_AREA_SOURCE_ID,
    filter: ['==', ['get', 'status'], 'COMPLETED'],
    paint: {
      'fill-pattern': SEARCH_AREA_COMPLETED_HATCH_PATTERN_ID,
      'fill-opacity': 0.45,
    },
  });

  SEARCH_AREA_LINE_LAYER_STYLES.forEach((style) => {
    addLayer(map, {
      id: style.id,
      type: 'line',
      source: OVERALL_SEARCH_AREA_SOURCE_ID,
      filter: ['==', ['get', 'areaLevel'], style.areaLevel],
      paint: {
        'line-color': ['get', 'lineColor'],
        'line-width': style.lineWidth,
        'line-opacity': style.lineOpacity,
        ...(style.lineGapWidth ? { 'line-gap-width': style.lineGapWidth } : {}),
        ...(style.lineDasharray ? { 'line-dasharray': style.lineDasharray } : {}),
      },
    });
  });
}

function addMovementPathLayers(map: maplibregl.Map, movementPaths: OperationalFeatureCollection) {
  addGeoJsonSource(map, MOVEMENT_PATH_SOURCE_ID, movementPaths);

  addLayer(map, {
    id: MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: ['all', ['has', 'deviceColor'], ['!=', ['get', 'deviceColor'], ''], ['!=', ['get', 'isActiveOp'], 'true']],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'deviceColor'],
      'line-width': 5.8,
      'line-opacity': 0.18,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_COMPARE_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: ['all', ['has', 'deviceColor'], ['!=', ['get', 'deviceColor'], ''], ['!=', ['get', 'isActiveOp'], 'true']],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'routeCoreColor'],
      'line-width': 2.7,
      'line-opacity': 0.64,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'VEHICLE'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'deviceColor'],
      'line-width': 8.8,
      'line-opacity': 0.32,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_VEHICLE_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'VEHICLE'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'routeCoreColor'],
      'line-width': 4.6,
      'line-opacity': 0.98,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_FOOT_GLOW_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'FOOT'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'deviceColor'],
      'line-width': 7.8,
      'line-opacity': 0.3,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_FOOT_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'FOOT'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'routeCoreColor'],
      'line-width': 3.8,
      'line-opacity': 0.98,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'UNKNOWN'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'deviceColor'],
      'line-width': 7,
      'line-opacity': 0.24,
    },
  });
  addLayer(map, {
    id: MOVEMENT_PATH_UNKNOWN_LAYER_ID,
    type: 'line',
    source: MOVEMENT_PATH_SOURCE_ID,
    filter: [
      'all',
      ['has', 'deviceColor'],
      ['!=', ['get', 'deviceColor'], ''],
      ['==', ['get', 'isActiveOp'], 'true'],
      ['==', ['get', 'movementType'], 'UNKNOWN'],
    ],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'routeCoreColor'],
      'line-width': 3.4,
      'line-opacity': 0.86,
    },
  });
}

function addMovementCurrentPositionLayers(map: maplibregl.Map, currentPositions: OperationalFeatureCollection) {
  addGeoJsonSource(map, MOVEMENT_CURRENT_POSITION_SOURCE_ID, currentPositions);

  addMovementCurrentPositionLayerGroup(map, {
    movementType: 'VEHICLE',
    glowLayerId: MOVEMENT_CURRENT_POSITION_VEHICLE_GLOW_LAYER_ID,
    coreLayerId: MOVEMENT_CURRENT_POSITION_VEHICLE_LAYER_ID,
    statusLayerId: MOVEMENT_CURRENT_POSITION_VEHICLE_STATUS_LAYER_ID,
  });
  addMovementCurrentPositionLayerGroup(map, {
    movementType: 'FOOT',
    glowLayerId: MOVEMENT_CURRENT_POSITION_FOOT_GLOW_LAYER_ID,
    coreLayerId: MOVEMENT_CURRENT_POSITION_FOOT_LAYER_ID,
    statusLayerId: MOVEMENT_CURRENT_POSITION_FOOT_STATUS_LAYER_ID,
  });
  addMovementCurrentPositionLayerGroup(map, {
    movementType: 'UNKNOWN',
    glowLayerId: MOVEMENT_CURRENT_POSITION_UNKNOWN_GLOW_LAYER_ID,
    coreLayerId: MOVEMENT_CURRENT_POSITION_UNKNOWN_LAYER_ID,
    statusLayerId: MOVEMENT_CURRENT_POSITION_UNKNOWN_STATUS_LAYER_ID,
  });
}

function addMovementCurrentPositionLayerGroup(
  map: maplibregl.Map,
  {
    movementType,
    glowLayerId,
    coreLayerId,
    statusLayerId,
  }: { movementType: MovementPath['movementType']; glowLayerId: string; coreLayerId: string; statusLayerId: string },
) {
  const currentPositionFilter = [
    'all',
    ['has', 'deviceColor'],
    ['!=', ['get', 'deviceColor'], ''],
    ['==', ['get', 'movementType'], movementType],
  ] as FilterSpecification;
  addLayer(map, {
    id: glowLayerId,
    type: 'circle',
    source: MOVEMENT_CURRENT_POSITION_SOURCE_ID,
    filter: currentPositionFilter,
    paint: {
      'circle-color': ['get', 'deviceColor'],
      'circle-radius': 9,
      'circle-opacity': 0.24,
      'circle-stroke-color': ['get', 'deviceColor'],
      'circle-stroke-width': 3,
      'circle-stroke-opacity': 0.28,
    },
  });

  addLayer(map, {
    id: coreLayerId,
    type: 'circle',
    source: MOVEMENT_CURRENT_POSITION_SOURCE_ID,
    filter: currentPositionFilter,
    paint: {
      'circle-color': ['get', 'routeCoreColor'],
      'circle-radius': 5.6,
      'circle-opacity': 0.98,
      'circle-stroke-color': 'rgba(15, 23, 42, 0.28)',
      'circle-stroke-width': 1,
      'circle-stroke-opacity': 0.72,
    },
  });

  addLayer(map, {
    id: statusLayerId,
    type: 'circle',
    source: MOVEMENT_CURRENT_POSITION_SOURCE_ID,
    filter: currentPositionFilter,
    paint: {
      'circle-color': ['get', 'currentPositionColor'],
      'circle-radius': 2.5,
      'circle-opacity': 0.98,
      'circle-stroke-color': 'rgba(15, 23, 42, 0.48)',
      'circle-stroke-width': 0.7,
    },
  });
}

function raiseMovementPathLayers(map: maplibregl.Map) {
  [
    MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID,
    MOVEMENT_PATH_COMPARE_LAYER_ID,
    MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID,
    MOVEMENT_PATH_FOOT_GLOW_LAYER_ID,
    MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID,
    MOVEMENT_PATH_VEHICLE_LAYER_ID,
    MOVEMENT_PATH_FOOT_LAYER_ID,
    MOVEMENT_PATH_UNKNOWN_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_VEHICLE_GLOW_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_FOOT_GLOW_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_GLOW_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_VEHICLE_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_FOOT_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_VEHICLE_STATUS_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_FOOT_STATUS_LAYER_ID,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_STATUS_LAYER_ID,
  ].forEach((layerId) => {
    if (map.getLayer(layerId)) {
      map.moveLayer(layerId);
    }
  });
}

function syncSelectedSearchArea(map: maplibregl.Map, selectedSearchAreaId: string | null) {
  if (!hasSearchAreaLayers(map)) {
    return;
  }

  const selectedFilter = selectedSearchAreaId ? ['==', ['get', 'entityId'], selectedSearchAreaId] : false;
  map.setPaintProperty(SEARCH_AREA_FILL_LAYER_ID, 'fill-opacity', ['case', selectedFilter, 0.28, 0.12]);
  SEARCH_AREA_LINE_LAYER_STYLES.forEach((style) => {
    map.setPaintProperty(style.id, 'line-opacity', ['case', selectedFilter, 1, style.lineOpacity]);
  });
}

function setLayerVisibility(map: maplibregl.Map, layerId: string, isVisible: boolean) {
  if (!map.getLayer(layerId)) {
    return;
  }

  map.setLayoutProperty(layerId, 'visibility', isVisible ? 'visible' : 'none');
}

function syncLayerVisibility(map: maplibregl.Map, layerVisibility: LayerVisibility) {
  setLayerVisibility(map, SEARCH_AREA_FILL_LAYER_ID, layerVisibility.searchArea);
  setLayerVisibility(map, SEARCH_AREA_COMPLETED_HATCH_LAYER_ID, layerVisibility.searchArea);
  SEARCH_AREA_LINE_LAYER_ORDER.forEach((layerId) => setLayerVisibility(map, layerId, layerVisibility.searchArea));
  setLayerVisibility(
    map,
    MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID,
    layerVisibility.vehiclePath || layerVisibility.footPath,
  );
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_GLOW_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_COMPARE_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_VEHICLE_GLOW_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_VEHICLE_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_VEHICLE_STATUS_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_FOOT_GLOW_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_FOOT_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_CURRENT_POSITION_FOOT_STATUS_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(
    map,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_GLOW_LAYER_ID,
    layerVisibility.vehiclePath || layerVisibility.footPath,
  );
  setLayerVisibility(
    map,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_LAYER_ID,
    layerVisibility.vehiclePath || layerVisibility.footPath,
  );
  setLayerVisibility(
    map,
    MOVEMENT_CURRENT_POSITION_UNKNOWN_STATUS_LAYER_ID,
    layerVisibility.vehiclePath || layerVisibility.footPath,
  );
}

type SearchMapCanvasProps = {
  activeOperationalPeriodId: string | null;
  incidentId: string;
  layerVisibility: LayerVisibility;
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  operationalPeriods: OperationalPeriod[];
  focusedMarkerId: string | null;
  focusedMarkerSequence: number;
  focusedSearchAreaId: string | null;
  focusedSearchAreaSequence: number;
  visibleMarkerIds: string[];
  savedAreaDrafts: CompletedAreaDraft[];
  selectedPolicePhoneLegendFilters: PolicePhoneLegendFilterId[];
  selectedSearchAreaLegendFilters: SearchAreaLegendFilterId[];
  onInitialBoundsReady?: (bounds: LngLatBoundsLike | null) => void;
  onInitialMapStateReady?: (state: InitialMapResolution['state'] | null) => void;
  onMapReady?: (map: maplibregl.Map | null) => void;
  areaEditMapProps?: AreaEditMapCanvasProps | null;
  handoverMapProps?: HandoverComparisonMapSharedProps | null;
  searchAreaTree: SearchAreaTreeNode;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
  onClearSelectedSearchArea: () => void;
  onOpenSearchAreaSplit: () => void;
  onOpenSearchAreaAssign: () => void;
};

export function SearchMapCanvas({
  activeOperationalPeriodId,
  incidentId,
  layerVisibility,
  movementPaths,
  recentMarkers,
  operationalPeriods,
  focusedMarkerId,
  focusedMarkerSequence,
  focusedSearchAreaId,
  focusedSearchAreaSequence,
  visibleMarkerIds,
  savedAreaDrafts,
  selectedPolicePhoneLegendFilters,
  selectedSearchAreaLegendFilters,
  onInitialBoundsReady,
  onInitialMapStateReady,
  onMapReady,
  areaEditMapProps,
  handoverMapProps,
  searchAreaTree,
  selectedSearchAreaId,
  onSelectSearchArea,
  onClearSelectedSearchArea,
  onOpenSearchAreaSplit,
  onOpenSearchAreaAssign,
}: SearchMapCanvasProps) {
  const queryClient = useQueryClient();
  const updateMarkerMutation = useUpdateMarkerMutation();
  const createMarkerMutation = useCreateMarkerMutation();
  const createManualSearchPathMutation = useCreateManualSearchPathMutation();
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const isRouteEditorEnabledRef = useRef(false);
  const selectedSearchAreaIdRef = useRef(selectedSearchAreaId);
  const areaEditMapPropsRef = useRef(areaEditMapProps);
  const layerVisibilityRef = useRef(layerVisibility);
  const recentMarkersRef = useRef(recentMarkers);
  const visibleMarkerIdsRef = useRef(visibleMarkerIds);
  const markerInstancesRef = useRef<Map<string, MarkerInstance>>(new Map());
  const hoverMarkerPopupRef = useRef<maplibregl.Popup | null>(null);
  const selectedMarkerPopupRef = useRef<maplibregl.Popup | null>(null);
  const hoveredMarkerIdRef = useRef<string | null>(null);
  const selectedMarkerIdRef = useRef<string | null>(null);
  const onSelectSearchAreaRef = useRef(onSelectSearchArea);
  const [isRouteEditorEnabled, setIsRouteEditorEnabled] = useState(false);
  const [routeEditorCoordinates, setRouteEditorCoordinates] = useState<Position[]>([]);
  const [routeEditorPolicePhoneId, setRouteEditorPolicePhoneId] = useState('');
  const [routeEditorStartedAtLocal, setRouteEditorStartedAtLocal] = useState(() =>
    toDatetimeLocalValue(new Date(Date.now() - 5 * 60_000)),
  );
  const [routeEditorEndedAtLocal, setRouteEditorEndedAtLocal] = useState(() => toDatetimeLocalValue(new Date()));
  const [routeEditorMarkerType, setRouteEditorMarkerType] = useState<ManualRouteMarkerType>('CLUE');
  const [routeEditorMarkerMemo, setRouteEditorMarkerMemo] = useState('');
  const [routeEditorStatus, setRouteEditorStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const [routeEditorErrorMessage, setRouteEditorErrorMessage] = useState('');
  const routeEditorGeneratedCoordinates = useMemo(
    () => interpolateManualRouteCoordinates(routeEditorCoordinates),
    [routeEditorCoordinates],
  );
  const routeEditorTargetOpId = useMemo(
    () => resolveSearchAreaMemoOpId(searchAreaTree, selectedSearchAreaId, activeOperationalPeriodId),
    [activeOperationalPeriodId, searchAreaTree, selectedSearchAreaId],
  );
  const [mapInstance, setMapInstance] = useState<maplibregl.Map | null>(null);
  const [mapViewportVersion, setMapViewportVersion] = useState(0);
  const [hoveredMarkerId, setHoveredMarkerId] = useState<string | null>(null);
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);
  const [referenceMarkerCorrectionState, setReferenceMarkerCorrectionState] = useState<{
    markerId: string | null;
    status: 'idle' | 'editing' | 'saving' | 'saved' | 'error';
  }>({ markerId: null, status: 'idle' });
  const [mapMemoTarget, setMapMemoTarget] = useState<MapMemoTarget | null>(null);
  const [mapMemoContent, setMapMemoContent] = useState('');
  const [mapMemoSubmitStatus, setMapMemoSubmitStatus] = useState<MapMemoSubmitStatus>('idle');
  const [mapMemoErrorMessage, setMapMemoErrorMessage] = useState('');
  const [markerMemoOverrides, setMarkerMemoOverrides] = useState<Map<string, string>>(() => new Map());
  const [searchAreaPopupLngLat, setSearchAreaPopupLngLat] = useState<maplibregl.LngLatLike | null>(null);
  const searchAreaPopupOverlayRef = useRef<HTMLDivElement | null>(null);
  const searchAreaPopupSearchAreaIdRef = useRef<string | null>(null);
  const assignedSearchAreas = useMemo(
    () =>
      applySearchAreaStatuses(
        createSearchAreaDraftFeatureCollection(savedAreaDrafts, { incidentId, includeSlot: true }),
        searchAreaTree,
      ),
    [incidentId, savedAreaDrafts, searchAreaTree],
  );
  const visibleAssignedSearchAreas = useMemo(
    () => filterSearchAreasByLegendFilters(assignedSearchAreas, selectedSearchAreaLegendFilters),
    [assignedSearchAreas, selectedSearchAreaLegendFilters],
  );
  const selectedMarker = useMemo(
    () =>
      selectedMarkerId
        ? (recentMarkers.find((marker) => marker.id === selectedMarkerId && marker.coordinates) ?? null)
        : null,
    [recentMarkers, selectedMarkerId],
  );
  const selectedMarkerPopupContent = selectedMarker
    ? (markerMemoOverrides.get(selectedMarker.id) ?? getMarkerPopupContent(selectedMarker))
    : null;
  const canCorrectSelectedReferenceMarker = canCorrectReferenceMarker(selectedMarker);
  const selectedMarkerCorrectionStatus =
    referenceMarkerCorrectionState.markerId === selectedMarker?.id ? referenceMarkerCorrectionState.status : 'idle';
  const getViewportPoint = useCallback(
    (coordinates: maplibregl.LngLatLike) => {
      const map = mapInstance;
      const mapContainer = mapContainerRef.current;
      if (!map || !mapContainer) {
        return null;
      }

      const mapContainerRect = mapContainer.getBoundingClientRect();
      const projectedPoint = map.project(coordinates);
      return {
        x: mapContainerRect.left + projectedPoint.x,
        y: mapContainerRect.top + projectedPoint.y,
      };
    },
    [mapInstance, mapViewportVersion],
  );
  const selectedMarkerPoint = useMemo(
    () => (selectedMarker?.coordinates ? getViewportPoint(selectedMarker.coordinates) : null),
    [getViewportPoint, selectedMarker],
  );
  const searchAreaPopupPoint = useMemo(
    () => (searchAreaPopupLngLat ? getViewportPoint(searchAreaPopupLngLat) : null),
    [getViewportPoint, searchAreaPopupLngLat],
  );
  const movementPathFeatures = useMemo(
    () =>
      createMovementPathFeatureCollection(movementPaths, activeOperationalPeriodId, {
        includeLabel: true,
      }),
    [activeOperationalPeriodId, movementPaths],
  );
  const movementCurrentPositionFeatures = useMemo(
    () =>
      createMovementCurrentPositionFeatureCollection(movementPaths, activeOperationalPeriodId, {
        includeLabel: true,
      }),
    [activeOperationalPeriodId, movementPaths],
  );
  const visibleMovementPathFeatures = useMemo(
    () => filterMovementPathsByPolicePhoneLegendFilters(movementPathFeatures, selectedPolicePhoneLegendFilters),
    [movementPathFeatures, selectedPolicePhoneLegendFilters],
  );
  const visibleMovementCurrentPositionFeatures = useMemo(
    () =>
      filterMovementPathsByPolicePhoneLegendFilters(
        movementCurrentPositionFeatures,
        selectedPolicePhoneLegendFilters,
      ),
    [movementCurrentPositionFeatures, selectedPolicePhoneLegendFilters],
  );
  const assignedSearchAreasBoundsSignature = useMemo(
    () => createOperationalFeatureCollectionBoundsSignature(assignedSearchAreas),
    [assignedSearchAreas],
  );
  const assignedSearchAreasRef = useRef(visibleAssignedSearchAreas);
  const movementPathFeaturesRef = useRef(visibleMovementPathFeatures);
  const movementCurrentPositionFeaturesRef = useRef(visibleMovementCurrentPositionFeatures);
  const fittedSearchAreasBoundsSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    assignedSearchAreasRef.current = visibleAssignedSearchAreas;
  }, [visibleAssignedSearchAreas]);

  useEffect(() => {
    movementPathFeaturesRef.current = visibleMovementPathFeatures;
  }, [visibleMovementPathFeatures]);

  useEffect(() => {
    movementCurrentPositionFeaturesRef.current = visibleMovementCurrentPositionFeatures;
  }, [visibleMovementCurrentPositionFeatures]);

  useEffect(() => {
    selectedSearchAreaIdRef.current = selectedSearchAreaId;
  }, [selectedSearchAreaId]);

  useEffect(() => {
    visibleMarkerIdsRef.current = visibleMarkerIds;
  }, [visibleMarkerIds]);

  useEffect(() => {
    hoveredMarkerIdRef.current = hoveredMarkerId;
  }, [hoveredMarkerId]);

  useEffect(() => {
    selectedMarkerIdRef.current = selectedMarkerId;
  }, [selectedMarkerId]);

  useEffect(() => {
    const map = mapInstance;
    if (!map) {
      return;
    }

    const syncViewportVersion = () => {
      setMapViewportVersion((current) => current + 1);
    };

    syncViewportVersion();
    map.on('move', syncViewportVersion);
    map.on('resize', syncViewportVersion);
    window.addEventListener('scroll', syncViewportVersion, true);
    window.addEventListener('resize', syncViewportVersion);

    return () => {
      map.off('move', syncViewportVersion);
      map.off('resize', syncViewportVersion);
      window.removeEventListener('scroll', syncViewportVersion, true);
      window.removeEventListener('resize', syncViewportVersion);
    };
  }, [mapInstance]);

  useEffect(() => {
    if (selectedSearchAreaId !== null) {
      if (searchAreaPopupSearchAreaIdRef.current === selectedSearchAreaId) {
        return;
      }
    }

    searchAreaPopupSearchAreaIdRef.current = null;
    setSearchAreaPopupLngLat(null);
  }, [selectedSearchAreaId]);

  useEffect(() => {
    areaEditMapPropsRef.current = areaEditMapProps;
  }, [areaEditMapProps]);

  const removeSearchAreaPopup = useCallback(() => {
    searchAreaPopupOverlayRef.current = null;
    searchAreaPopupSearchAreaIdRef.current = null;
    setSearchAreaPopupLngLat(null);
  }, []);

  const closeSearchAreaPopup = useCallback(() => {
    searchAreaPopupSearchAreaIdRef.current = null;
    setSearchAreaPopupLngLat(null);
    removeSearchAreaPopup();
    onClearSelectedSearchArea();
  }, [onClearSelectedSearchArea, removeSearchAreaPopup]);

  const handleHoverMarker = useCallback((markerId: string) => {
    setHoveredMarkerId(markerId);
  }, []);

  const handleLeaveMarker = useCallback(() => {
    setHoveredMarkerId(null);
  }, []);

  const handleSelectMarker = useCallback(
    (markerId: string) => {
      closeSearchAreaPopup();
      setSelectedMarkerId(markerId);
      setHoveredMarkerId(null);
    },
    [closeSearchAreaPopup],
  );

  const handleCloseSelectedMarker = useCallback(() => {
    setSelectedMarkerId(null);
  }, []);

  const handleStartSelectedReferenceMarkerCorrection = useCallback(() => {
    const marker = selectedMarker;
    if (!marker || !canCorrectReferenceMarker(marker)) {
      return;
    }
    setReferenceMarkerCorrectionState({ markerId: marker.id, status: 'editing' });
  }, [selectedMarker]);

  const handleCancelSelectedReferenceMarkerCorrection = useCallback(() => {
    setReferenceMarkerCorrectionState({ markerId: selectedMarker?.id ?? null, status: 'idle' });
  }, [selectedMarker?.id]);

  const handleCorrectSelectedReferenceMarker = useCallback(async () => {
    const marker = selectedMarker;
    const map = mapRef.current;
    if (!marker || !canCorrectReferenceMarker(marker) || !map) {
      return;
    }

    const center = map.getCenter();
    const coordinates: [number, number] = [center.lng, center.lat];
    setReferenceMarkerCorrectionState({ markerId: marker.id, status: 'saving' });
    try {
      await updateMarkerMutation.mutateAsync({
        markerId: marker.id,
        request: createReferenceMarkerCorrectionRequest(marker, coordinates),
        idempotencyKey: createReferenceMarkerCorrectionIdempotencyKey(marker.id),
      });
      await queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId }) });
      setReferenceMarkerCorrectionState({ markerId: marker.id, status: 'saved' });
    } catch {
      setReferenceMarkerCorrectionState({ markerId: marker.id, status: 'error' });
    }
  }, [incidentId, queryClient, selectedMarker, updateMarkerMutation]);

  const handleCloseSearchAreaPopup = useCallback(() => {
    closeSearchAreaPopup();
  }, [closeSearchAreaPopup]);

  const handleOpenSearchAreaSplit = useCallback(() => {
    closeSearchAreaPopup();
    onOpenSearchAreaSplit();
  }, [closeSearchAreaPopup, onOpenSearchAreaSplit]);

  const handleOpenSearchAreaAssign = useCallback(() => {
    closeSearchAreaPopup();
    onOpenSearchAreaAssign();
  }, [closeSearchAreaPopup, onOpenSearchAreaAssign]);

  const resetRouteEditorTimes = useCallback(() => {
    const now = new Date();
    setRouteEditorStartedAtLocal(toDatetimeLocalValue(new Date(now.getTime() - 5 * 60_000)));
    setRouteEditorEndedAtLocal(toDatetimeLocalValue(now));
  }, []);

  const handleOpenRouteEditor = useCallback(() => {
    if (!selectedSearchAreaId || areaEditMapPropsRef.current) {
      return;
    }
    const searchArea = findSearchAreaNode(searchAreaTree, selectedSearchAreaId);
    removeSearchAreaPopup();
    setSelectedMarkerId(null);
    setRouteEditorCoordinates([]);
    setRouteEditorPolicePhoneId(
      resolveSearchAreaPolicePhoneId(searchArea, routeEditorTargetOpId) ?? '',
    );
    setRouteEditorStatus('idle');
    setRouteEditorErrorMessage('');
    resetRouteEditorTimes();
    setIsRouteEditorEnabled(true);
  }, [
    removeSearchAreaPopup,
    resetRouteEditorTimes,
    routeEditorTargetOpId,
    searchAreaTree,
    selectedSearchAreaId,
  ]);

  const handleCloseRouteEditor = useCallback(() => {
    setIsRouteEditorEnabled(false);
    setRouteEditorCoordinates([]);
    setRouteEditorStatus('idle');
    setRouteEditorErrorMessage('');
  }, []);

  const handleUndoRouteEditorPoint = useCallback(() => {
    setRouteEditorCoordinates((currentCoordinates) => currentCoordinates.slice(0, -1));
    setRouteEditorStatus('idle');
    setRouteEditorErrorMessage('');
  }, []);

  const handleClearRouteEditor = useCallback(() => {
    setRouteEditorCoordinates([]);
    setRouteEditorStatus('idle');
    setRouteEditorErrorMessage('');
  }, []);

  const getRouteEditorWriteContext = useCallback(() => {
    const opId = routeEditorTargetOpId;
    const policePhoneId = routeEditorPolicePhoneId.trim();
    const startedAt = parseDatetimeLocalValue(routeEditorStartedAtLocal);
    const endedAt = parseDatetimeLocalValue(routeEditorEndedAtLocal);
    if (!selectedSearchAreaId) {
      return { error: '수색구역을 먼저 선택하세요.' as const };
    }
    if (!opId) {
      return { error: '활성 OP를 확인할 수 없습니다.' as const };
    }
    if (!policePhoneId) {
      return { error: 'PolicePhone ID를 입력하세요.' as const };
    }
    if (!startedAt || !endedAt || startedAt.getTime() >= endedAt.getTime()) {
      return { error: '시작/종료 시각을 확인하세요.' as const };
    }
    return { opId, policePhoneId, startedAt, endedAt };
  }, [
    routeEditorEndedAtLocal,
    routeEditorPolicePhoneId,
    routeEditorStartedAtLocal,
    routeEditorTargetOpId,
    selectedSearchAreaId,
  ]);

  const handleSaveRouteEditorPath = useCallback(async () => {
    if (routeEditorGeneratedCoordinates.length < 2 || routeEditorStatus === 'saving') {
      return;
    }
    const context = getRouteEditorWriteContext();
    if ('error' in context) {
      setRouteEditorStatus('error');
      setRouteEditorErrorMessage(context.error ?? '저장 조건을 확인하세요.');
      return;
    }

    const searchPathId = createUuid();
    const routeEndedAt = calculateManualRouteEndedAt(context.startedAt, routeEditorGeneratedCoordinates.length);
    setRouteEditorStatus('saving');
    setRouteEditorErrorMessage('');
    try {
      await createManualSearchPathMutation.mutateAsync({
        request: {
          incidentId,
          opId: context.opId,
          policePhoneId: context.policePhoneId,
          searchPathId,
          startedAt: context.startedAt.toISOString(),
          endedAt: routeEndedAt.toISOString(),
          points: createManualSearchPathPoints(routeEditorGeneratedCoordinates, context.startedAt),
        },
        idempotencyKey: createIdempotencyKey('web-manual-path'),
      });
      await queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId }) });
      setRouteEditorEndedAtLocal(toDatetimeLocalValue(routeEndedAt));
      setRouteEditorStatus('saved');
    } catch (error) {
      console.error('Failed to save manual search path', error);
      setRouteEditorStatus('error');
      if (error instanceof ApiHttpError && error.code === 'police_phone_required') {
        setRouteEditorErrorMessage('선택 구역의 폴리폰 ID를 찾을 수 없습니다.');
      } else if (error instanceof ApiHttpError && error.code === 'police_phone_not_assigned') {
        setRouteEditorErrorMessage('선택 구역의 폴리폰이 현재 OP에 근무 배정되어 있지 않습니다.');
      } else if (error instanceof ApiHttpError && error.code === 'invalid_geometry') {
        setRouteEditorErrorMessage('경로 좌표가 서버 검증 조건을 통과하지 못했습니다.');
      } else if (error instanceof ApiHttpError && error.code === 'write_conflict') {
        setRouteEditorErrorMessage('서버가 동일 요청을 처리 중입니다. 잠시 후 다시 시도하세요.');
      } else if (error instanceof ApiHttpError) {
        setRouteEditorErrorMessage(`경로 저장에 실패했습니다. (${error.code})`);
      } else {
        setRouteEditorErrorMessage('경로 저장에 실패했습니다.');
      }
    }
  }, [
    createManualSearchPathMutation,
    getRouteEditorWriteContext,
    incidentId,
    queryClient,
    routeEditorGeneratedCoordinates,
    routeEditorStatus,
  ]);

  const handleCreateRouteEditorMarker = useCallback(async () => {
    const coordinate = routeEditorCoordinates.at(-1);
    if (!coordinate || routeEditorStatus === 'saving') {
      return;
    }
    const context = getRouteEditorWriteContext();
    if ('error' in context) {
      setRouteEditorStatus('error');
      setRouteEditorErrorMessage(context.error ?? '저장 조건을 확인하세요.');
      return;
    }

    setRouteEditorStatus('saving');
    setRouteEditorErrorMessage('');
    try {
      await createMarkerMutation.mutateAsync({
        request: {
          id: createUuid(),
          incidentId,
          opId: context.opId,
          type: routeEditorMarkerType,
          location: {
            type: 'Point',
            coordinates: [coordinate[0], coordinate[1]],
          },
          clientTs: context.endedAt.toISOString(),
          memo: routeEditorMarkerMemo.trim() || null,
          photos: [],
        },
        idempotencyKey: createIdempotencyKey('web-manual-marker'),
        policePhoneId: context.policePhoneId,
      });
      await queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId }) });
      setRouteEditorStatus('saved');
    } catch {
      setRouteEditorStatus('error');
      setRouteEditorErrorMessage('마커 저장에 실패했습니다.');
    }
  }, [
    createMarkerMutation,
    getRouteEditorWriteContext,
    incidentId,
    queryClient,
    routeEditorCoordinates,
    routeEditorMarkerMemo,
    routeEditorMarkerType,
    routeEditorStatus,
  ]);

  useEffect(() => {
    if (isRouteEditorEnabled && !selectedSearchAreaId) {
      handleCloseRouteEditor();
    }
  }, [handleCloseRouteEditor, isRouteEditorEnabled, selectedSearchAreaId]);

  const openMapMemoComposer = useCallback((target: MapMemoTarget) => {
    setMapMemoTarget(target);
    setMapMemoContent('');
    setMapMemoErrorMessage('');
    setMapMemoSubmitStatus('editing');
  }, []);

  const handleOpenSearchAreaMemoComposer = useCallback(() => {
    if (!selectedSearchAreaId) {
      return;
    }

    const opId = resolveSearchAreaMemoOpId(searchAreaTree, selectedSearchAreaId, activeOperationalPeriodId);
    if (!opId) {
      return;
    }

    openMapMemoComposer({
      targetType: 'SEARCH_AREA',
      targetId: selectedSearchAreaId,
      opId,
    });
  }, [activeOperationalPeriodId, openMapMemoComposer, searchAreaTree, selectedSearchAreaId]);

  const handleOpenMarkerMemoComposer = useCallback(() => {
    if (!selectedMarker) {
      return;
    }

    const opId = resolveMarkerMemoOpId(selectedMarker, activeOperationalPeriodId);
    if (!opId) {
      return;
    }

    openMapMemoComposer({
      targetType: 'MARKER',
      targetId: selectedMarker.id,
      opId,
    });
  }, [activeOperationalPeriodId, openMapMemoComposer, selectedMarker]);

  const handleCloseMapMemoComposer = useCallback(() => {
    setMapMemoTarget(null);
    setMapMemoContent('');
    setMapMemoErrorMessage('');
    setMapMemoSubmitStatus('idle');
  }, []);

  const handleSubmitMapMemo = useCallback(async (options: MapMemoSubmitOptions = {}) => {
    const trimmedContent = mapMemoContent.trim();
    if (!mapMemoTarget || !trimmedContent || mapMemoSubmitStatus === 'saving') {
      return;
    }

    const submittedTarget = mapMemoTarget;
    setMapMemoSubmitStatus('saving');
    setMapMemoErrorMessage('');
    try {
      await handoverApi.createHandoverMemo(
        {
          incidentId,
          opId: submittedTarget.opId,
          memoTargetType: submittedTarget.targetType,
          memoTargetId: submittedTarget.targetId,
          content: trimmedContent,
          clientTs: new Date().toISOString(),
        },
        createIdempotencyKey('handover-memo'),
      );
      if (submittedTarget.targetType === 'MARKER') {
        setMarkerMemoOverrides((currentOverrides) => {
          const nextOverrides = new Map(currentOverrides);
          nextOverrides.set(submittedTarget.targetId, trimmedContent);
          return nextOverrides;
        });
      }
      if (options.closeComposerOnSuccess) {
        handleCloseMapMemoComposer();
        void Promise.all([
          queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId }) }),
          queryClient.invalidateQueries({ queryKey: handoverQueryKeys.all }),
        ]);
        return;
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.detail({ incidentId }) }),
        queryClient.invalidateQueries({ queryKey: handoverQueryKeys.all }),
      ]);
      setMapMemoContent('');
      setMapMemoSubmitStatus('saved');
    } catch {
      setMapMemoSubmitStatus('error');
      setMapMemoErrorMessage('메모 저장에 실패했습니다.');
    }
  }, [handleCloseMapMemoComposer, incidentId, mapMemoContent, mapMemoSubmitStatus, mapMemoTarget, queryClient]);

  const markerInteractionHandlers = useMemo<MarkerInteractionHandlers>(
    () => ({
      onSelectMarker: handleSelectMarker,
      onCloseSelectedMarker: handleCloseSelectedMarker,
    }),
    [handleCloseSelectedMarker, handleSelectMarker],
  );

  useEffect(() => {
    if (!searchAreaPopupLngLat) {
      return;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }

      if (searchAreaPopupOverlayRef.current?.contains(target)) {
        return;
      }

      onClearSelectedSearchArea();
      closeSearchAreaPopup();
    };

    document.addEventListener('pointerdown', handlePointerDown, true);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown, true);
    };
  }, [closeSearchAreaPopup, searchAreaPopupLngLat]);

  useEffect(() => {
    if (!mapInstance || !searchAreaPopupLngLat || !selectedSearchAreaId || areaEditMapProps) {
      removeSearchAreaPopup();
      return;
    }
  }, [
    areaEditMapProps,
    handleCloseSearchAreaPopup,
    handleOpenSearchAreaAssign,
    handleOpenSearchAreaSplit,
    mapInstance,
    removeSearchAreaPopup,
    searchAreaTree,
    selectedSearchAreaId,
    operationalPeriods,
  ]);

  const overlayPortalTarget = typeof document === 'undefined' ? null : document.body;

  useEffect(() => {
    layerVisibilityRef.current = layerVisibility;
    const map = mapRef.current;
    if (!map) {
      return;
    }

    return syncMarkerElementsWhenAvailable(
      map,
      recentMarkersRef.current,
      visibleMarkerIds,
      markerInstancesRef,
      layerVisibility.marker,
      markerInteractionHandlers,
      hoveredMarkerId,
      selectedMarkerId,
    );
  }, [hoveredMarkerId, layerVisibility, markerInteractionHandlers, selectedMarkerId, visibleMarkerIds]);

  useEffect(() => {
    recentMarkersRef.current = recentMarkers;
    const map = mapRef.current;
    if (!map) {
      return;
    }

    return syncMarkerElementsWhenAvailable(
      map,
      recentMarkers,
      visibleMarkerIds,
      markerInstancesRef,
      layerVisibilityRef.current.marker,
      markerInteractionHandlers,
      hoveredMarkerId,
      selectedMarkerId,
    );
  }, [hoveredMarkerId, markerInteractionHandlers, recentMarkers, selectedMarkerId, visibleMarkerIds]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded() || !layerVisibility.marker) {
      removeMarkerPopup(hoverMarkerPopupRef);
      removeMarkerPopup(selectedMarkerPopupRef);
      return;
    }

    syncMarkerPopups(
      map,
      recentMarkers,
      hoveredMarkerId,
      selectedMarkerId,
      { hover: hoverMarkerPopupRef, selected: selectedMarkerPopupRef },
      markerInteractionHandlers,
    );
  }, [hoveredMarkerId, layerVisibility.marker, markerInteractionHandlers, recentMarkers, selectedMarkerId]);

  useEffect(() => {
    const visibleMarkerIdSet = new Set(visibleMarkerIds);
    setHoveredMarkerId((currentMarkerId) =>
      currentMarkerId && visibleMarkerIdSet.has(currentMarkerId) ? currentMarkerId : null,
    );
    setSelectedMarkerId((currentMarkerId) =>
      currentMarkerId && visibleMarkerIdSet.has(currentMarkerId) ? currentMarkerId : null,
    );
  }, [visibleMarkerIds]);

  useEffect(() => {
    if (!focusedMarkerId) {
      return;
    }

    const marker = recentMarkers.find((currentMarker) => currentMarker.id === focusedMarkerId);
    const map = mapRef.current;
    if (!map || !marker?.coordinates) {
      return;
    }

    setSelectedMarkerId(marker.id);
    setHoveredMarkerId(null);
    map.easeTo({
      center: marker.coordinates,
      duration: 520,
      essential: true,
    });
  }, [focusedMarkerId, focusedMarkerSequence, recentMarkers]);

  useEffect(() => {
    if (!focusedSearchAreaId) {
      return;
    }

    const map = mapRef.current;
    if (!map) {
      return;
    }

    const focusSearchArea = () => {
      const bounds = getSearchAreaBoundsById(assignedSearchAreasRef.current, focusedSearchAreaId);
      if (!bounds) {
        return;
      }

      map.fitBounds(bounds, {
        padding: FOCUSED_SEARCH_AREA_FIT_PADDING,
        duration: 520,
        maxZoom: FOCUSED_SEARCH_AREA_FIT_MAX_ZOOM,
      });
    };

    if (map.loaded()) {
      focusSearchArea();
      return;
    }

    map.once('load', focusSearchArea);
    return () => {
      map.off('load', focusSearchArea);
    };
  }, [focusedSearchAreaId, focusedSearchAreaSequence]);

  useEffect(() => {
    onSelectSearchAreaRef.current = onSelectSearchArea;
  }, [onSelectSearchArea]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    return syncOperationalGeoJsonSourceDataWhenAvailable(map, MOVEMENT_PATH_SOURCE_ID, visibleMovementPathFeatures);
  }, [visibleMovementPathFeatures]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    return syncOperationalGeoJsonSourceDataWhenAvailable(
      map,
      MOVEMENT_CURRENT_POSITION_SOURCE_ID,
      visibleMovementCurrentPositionFeatures,
    );
  }, [visibleMovementCurrentPositionFeatures]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    syncSearchAreaSourceDataWhenAvailable(map, visibleAssignedSearchAreas, layerVisibilityRef.current.searchArea);
    if (hasSearchAreaLayers(map)) {
      syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
    }
    const assignedSearchAreaBounds = getAssignedSearchAreaBounds(assignedSearchAreas);
    if (!assignedSearchAreaBounds) {
      onInitialBoundsReady?.(null);
      onInitialMapStateReady?.(null);
      return;
    }

    onInitialBoundsReady?.(assignedSearchAreaBounds);
    onInitialMapStateReady?.('overall-ready');
    if (fittedSearchAreasBoundsSignatureRef.current !== assignedSearchAreasBoundsSignature) {
      fittedSearchAreasBoundsSignatureRef.current = assignedSearchAreasBoundsSignature;
      map.fitBounds(assignedSearchAreaBounds, { padding: DEFAULT_FIT_PADDING, duration: 420, maxZoom: 15 });
    }
  }, [
    assignedSearchAreas,
    assignedSearchAreasBoundsSignature,
    onInitialBoundsReady,
    onInitialMapStateReady,
    visibleAssignedSearchAreas,
  ]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncSelectedSearchArea(map, selectedSearchAreaId);
  }, [selectedSearchAreaId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    syncSearchAreaSourceDataWhenAvailable(map, assignedSearchAreasRef.current, layerVisibility.searchArea);
    if (hasSearchAreaLayers(map)) {
      syncLayerVisibility(map, layerVisibility);
      syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
    }
  }, [layerVisibility]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }
    syncBaseMapOpacity(map);
  });

  useEffect(() => {
    if (!isRouteEditorEnabled) {
      const map = mapRef.current;
      if (map?.loaded()) {
        syncRouteEditorDraft(map, []);
      }
      return;
    }

    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    addRouteEditorLayers(map);
    syncRouteEditorDraft(map, routeEditorGeneratedCoordinates);
  }, [isRouteEditorEnabled, routeEditorGeneratedCoordinates]);

  useEffect(() => {
    isRouteEditorEnabledRef.current = isRouteEditorEnabled;
  }, [isRouteEditorEnabled]);

  useEffect(() => {
    if (!mapContainerRef.current) {
      return;
    }

    const vWorldApiKey = getVWorldApiKey();

    let map: maplibregl.Map;
    try {
      map = new maplibregl.Map({
        container: mapContainerRef.current,
        style: createVWorldBaseStyle(vWorldApiKey),
        center: DEFAULT_GWANGJU_CENTER,
        zoom: INITIAL_MAP_FALLBACK_ZOOM,
        maxZoom: V_WORLD_MAX_ZOOM,
        attributionControl: false,
      });
    } catch (error) {
      console.error('Failed to initialize search map', error);
      return;
    }

    mapRef.current = map;
    setMapInstance(map);
    onMapReady?.(map);

    const handleMapClick = (event: maplibregl.MapMouseEvent) => {
      if (!isRouteEditorEnabledRef.current) {
        if (areaEditMapPropsRef.current) {
          closeSearchAreaPopup();
          return;
        }

        const clickedMarkerId = layerVisibilityRef.current.marker
          ? (getRenderedMarkerIdAtPoint(map, event.point) ??
            getNearestMarkerIdAtPoint(map, event.point, recentMarkersRef.current, visibleMarkerIdsRef.current))
          : null;
        if (clickedMarkerId) {
          closeSearchAreaPopup();
          setSelectedMarkerId(clickedMarkerId);
          setHoveredMarkerId(null);
          return;
        }

        setSelectedMarkerId(null);

        if (!hasSearchAreaLayers(map)) {
          onClearSelectedSearchArea();
          closeSearchAreaPopup();
          return;
        }

        if (!layerVisibilityRef.current.searchArea) {
          onClearSelectedSearchArea();
          closeSearchAreaPopup();
          return;
        }

        const features = map.queryRenderedFeatures(event.point, { layers: SEARCH_AREA_RENDER_LAYER_IDS });
        const searchAreaId = features.find((feature) => typeof feature.properties?.entityId === 'string')?.properties
          ?.entityId;
        if (typeof searchAreaId === 'string') {
          searchAreaPopupSearchAreaIdRef.current = searchAreaId;
          onSelectSearchAreaRef.current(searchAreaId);
          setSearchAreaPopupLngLat(event.lngLat);
          return;
        }
        onClearSelectedSearchArea();
        closeSearchAreaPopup();
        return;
      }

      const routeEditorCoordinate: Position = [event.lngLat.lng, event.lngLat.lat];
      setRouteEditorCoordinates((currentCoordinates) => [...currentCoordinates, routeEditorCoordinate].slice(-120));
      setRouteEditorStatus('idle');
      setRouteEditorErrorMessage('');
    };

    map.on('click', handleMapClick);

    map.once('load', () => {
      if (mapRef.current !== map) return;
      try {
        syncBaseMapOpacity(map);
        const currentAssignedSearchAreas = assignedSearchAreasRef.current;
        const currentMovementPathFeatures = movementPathFeaturesRef.current;
        const currentMovementCurrentPositionFeatures = movementCurrentPositionFeaturesRef.current;
        addSearchAreaLayers(map, currentAssignedSearchAreas);
        addMovementPathLayers(map, currentMovementPathFeatures);
        addMovementCurrentPositionLayers(map, currentMovementCurrentPositionFeatures);
        syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
        syncLayerVisibility(map, layerVisibilityRef.current);
        syncMarkerElements(
          map,
          recentMarkersRef.current,
          visibleMarkerIdsRef.current,
          markerInstancesRef,
          layerVisibilityRef.current.marker,
          markerInteractionHandlers,
          hoveredMarkerIdRef.current,
          selectedMarkerIdRef.current,
        );
      } catch (error) {
        console.error('Failed to initialize search map layers', error);
        return;
      }

      void Promise.resolve()
        .then(() => {
          if (mapRef.current !== map) return;
          raiseMovementPathLayers(map);
          raiseMarkerLayer(map);
          if (isRouteEditorEnabledRef.current) {
            addRouteEditorLayers(map);
          }

          const fallbackBounds = toBounds(GWANGJU_BBOX);
          const initialMapResolution = resolveInitialMapView(fallbackBounds, assignedSearchAreasRef.current);

          onInitialBoundsReady?.(initialMapResolution.bounds);
          onInitialMapStateReady?.(initialMapResolution.state);
          if (initialMapResolution.bounds) {
            fittedSearchAreasBoundsSignatureRef.current = createOperationalFeatureCollectionBoundsSignature(
              assignedSearchAreasRef.current,
            );
            map.fitBounds(initialMapResolution.bounds, { padding: DEFAULT_FIT_PADDING, duration: 0, maxZoom: 15 });
          } else {
            map.setCenter(DEFAULT_GWANGJU_CENTER);
            map.setZoom(INITIAL_MAP_FALLBACK_ZOOM);
          }
        })
        .catch((error: unknown) => {
          console.error(error);
        });
    });

    return () => {
      map.off('click', handleMapClick);
      clearMarkerElements(markerInstancesRef);
      removeMarkerPopup(hoverMarkerPopupRef);
      removeMarkerPopup(selectedMarkerPopupRef);
      mapRef.current = null;
      setMapInstance(null);
      onInitialBoundsReady?.(null);
      onInitialMapStateReady?.(null);
      onMapReady?.(null);
      try {
        map.remove();
      } catch (error) {
        console.warn('Failed to remove search map', error);
      }
    };
  }, [onInitialBoundsReady, onInitialMapStateReady, onMapReady]);

  const selectedSearchAreaMemoOpId = resolveSearchAreaMemoOpId(
    searchAreaTree,
    selectedSearchAreaId,
    activeOperationalPeriodId,
  );
  const routeEditorSearchArea = selectedSearchAreaId ? findSearchAreaNode(searchAreaTree, selectedSearchAreaId) : null;
  const routeEditorAreaLabel = routeEditorSearchArea?.name?.trim() || routeEditorSearchArea?.id || '선택 구역';
  const selectedMarkerMemoOpId = resolveMarkerMemoOpId(selectedMarker, activeOperationalPeriodId);
  const selectedSearchAreaMemoKey = selectedSearchAreaId
    ? createMapMemoTargetKey('SEARCH_AREA', selectedSearchAreaId)
    : null;
  const selectedMarkerMemoKey = selectedMarker ? createMapMemoTargetKey('MARKER', selectedMarker.id) : null;
  const activeMapMemoKey = mapMemoTarget
    ? createMapMemoTargetKey(mapMemoTarget.targetType, mapMemoTarget.targetId)
    : null;
  const isSearchAreaMemoComposerOpen = Boolean(
    selectedSearchAreaMemoKey && selectedSearchAreaMemoKey === activeMapMemoKey,
  );
  const isMarkerMemoComposerOpen = Boolean(selectedMarkerMemoKey && selectedMarkerMemoKey === activeMapMemoKey);
  const isMapMemoSaving = mapMemoSubmitStatus === 'saving';

  const renderMapMemoComposer = (targetLabel: string, options: MapMemoSubmitOptions = {}) => (
    <div className={styles.mapMemoComposer}>
      <label className={styles.mapMemoField}>
        <span>{targetLabel} 메모</span>
        <textarea
          value={mapMemoContent}
          maxLength={1000}
          placeholder={`${targetLabel}에 남길 인수인계 메모를 입력하세요.`}
          disabled={isMapMemoSaving}
          onChange={(event) => {
            setMapMemoContent(event.target.value);
            if (mapMemoSubmitStatus === 'saved' || mapMemoSubmitStatus === 'error') {
              setMapMemoSubmitStatus('editing');
              setMapMemoErrorMessage('');
            }
          }}
        />
      </label>
      <div className={styles.mapMemoFooter}>
        <span className={styles.mapMemoCount}>{mapMemoContent.trim().length}/1000</span>
        <button
          type="button"
          className={styles.mapMemoSaveButton}
          disabled={!mapMemoContent.trim() || isMapMemoSaving}
          onClick={() => {
            void handleSubmitMapMemo(options);
          }}
        >
          {isMapMemoSaving ? '저장 중' : '저장'}
        </button>
        <button
          type="button"
          className={styles.mapMemoCancelButton}
          disabled={isMapMemoSaving}
          onClick={handleCloseMapMemoComposer}
        >
          취소
        </button>
      </div>
      {mapMemoSubmitStatus === 'saved' ? <span className={styles.mapMemoStatus}>메모 저장 완료</span> : null}
      {mapMemoErrorMessage ? <span className={styles.mapMemoError}>{mapMemoErrorMessage}</span> : null}
    </div>
  );

  return (
    <>
      {overlayPortalTarget && (selectedMarkerPoint || searchAreaPopupPoint)
        ? createPortal(
            <div className={styles.overlayPortalLayer}>
              {searchAreaPopupPoint && selectedSearchAreaId && !areaEditMapProps ? (
                <div
                  ref={searchAreaPopupOverlayRef}
                  className={styles.searchAreaMapPopup}
                  style={{
                    left: `${searchAreaPopupPoint.x}px`,
                    top: `${searchAreaPopupPoint.y}px`,
                    transform: 'translate(-50%, calc(-100% - 12px))',
                  }}
                  onClick={(event) => event.stopPropagation()}
                  onPointerDown={(event) => event.stopPropagation()}
                >
                  <SearchAreaInspectorCard
                    variant="mapPopup"
                    searchAreaTree={searchAreaTree}
                    selectedSearchAreaId={selectedSearchAreaId}
                    savedAreaDrafts={savedAreaDrafts}
                    movementPaths={movementPaths}
                    recentMarkers={recentMarkers}
                    operationalPeriods={operationalPeriods}
                    isMemoDisabled={!selectedSearchAreaMemoOpId || isMapMemoSaving}
                    memoComposer={isSearchAreaMemoComposerOpen ? renderMapMemoComposer('수색구역') : undefined}
                    memoDisabledReason="OP 확인 후 메모를 추가할 수 있습니다."
                    onClose={handleCloseSearchAreaPopup}
                    onOpenMemo={handleOpenSearchAreaMemoComposer}
                    onOpenRouteEditor={handleOpenRouteEditor}
                    onOpenAssign={handleOpenSearchAreaAssign}
                    onOpenSplit={handleOpenSearchAreaSplit}
                  />
                </div>
              ) : null}
              {selectedMarker && selectedMarkerPoint ? (
                <div
                  className={styles.markerSelectedOverlay}
                  style={createMarkerPopupStyle(selectedMarker, selectedMarkerPoint)}
                >
                  <section
                    className={styles.markerPopup}
                    role="dialog"
                    aria-label={getMarkerPopupAriaLabel(selectedMarker)}
                    onClick={(event) => event.stopPropagation()}
                    onPointerDown={(event) => event.stopPropagation()}
                  >
                    <header className={styles.markerPopupHeader}>
                      <div className={styles.markerPopupHeaderMain}>
                        <div className={styles.markerPopupBadge} aria-hidden="true">
                          <span className={styles.markerPopupBadgeIcon}>
                            <MarkerGlyph name={getMarkerPopupGlyphName(selectedMarker)} size={23} />
                          </span>
                        </div>
                        <div className={styles.markerPopupTitleGroup}>
                          <div className={styles.markerPopupTypeRow}>
                            <span className={styles.markerPopupType}>{getMarkerPopupTypeLabel(selectedMarker)}</span>
                            <span className={styles.markerPopupTypeMuted}>{getMarkerPopupOpLabel(selectedMarker)}</span>
                            {selectedMarker.sourceLabel ? (
                              <span className={styles.markerPopupTypeMuted}>{selectedMarker.sourceLabel}</span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                      <button
                        type="button"
                        className={styles.markerPopupClose}
                        aria-label="마커 정보 닫기"
                        onClick={handleCloseSelectedMarker}
                      >
                        ×
                      </button>
                    </header>
                    {hasMarkerPopupPhoto(selectedMarker) ? (
                      <figure
                        className={`${styles.markerPopupPhotoPreview} ${
                          selectedMarker.photoThumbnailUrl ? '' : styles.markerPopupPhotoPreviewEmpty
                        }`}
                      >
                        {selectedMarker.photoThumbnailUrl ? (
                          <img src={selectedMarker.photoThumbnailUrl} alt="" loading="lazy" decoding="async" />
                        ) : (
                          <figcaption className={styles.markerPopupPhotoFallback}>
                            <span className={styles.markerPopupPhotoFallbackIcon} aria-hidden="true">
                              <ImageIcon size={28} strokeWidth={2.2} />
                            </span>
                            <span>{getMarkerPopupPhotoCountLabel(selectedMarker)}</span>
                            <small>보기용 URL이 아직 제공되지 않았습니다.</small>
                          </figcaption>
                        )}
                        {selectedMarker.photoThumbnailUrl ? (
                          <a
                            className={styles.markerPopupPhotoViewButton}
                            href={selectedMarker.photoThumbnailUrl}
                            target="_blank"
                            rel="noreferrer"
                            aria-label="마커 사진 보기"
                          >
                            <ImageIcon size={18} strokeWidth={2.4} aria-hidden="true" />
                            <span>사진 보기</span>
                          </a>
                        ) : (
                          <button
                            type="button"
                            className={styles.markerPopupPhotoViewButton}
                            disabled
                            title="사진 보기 URL이 아직 제공되지 않았습니다."
                          >
                            <ImageIcon size={18} strokeWidth={2.4} aria-hidden="true" />
                            <span>사진 보기</span>
                          </button>
                        )}
                      </figure>
                    ) : null}
                    {selectedMarkerPopupContent ? (
                      <p className={styles.markerPopupBody}>{selectedMarkerPopupContent}</p>
                    ) : null}
                    <div className={styles.markerPopupDetail}>
                      <div className={styles.markerPopupRow}>
                        <span className={styles.markerPopupRowLabel}>작성자</span>
                        <span className={styles.markerPopupRowValue}>{getMarkerPopupAuthorLabel(selectedMarker)}</span>
                      </div>
                      <div className={styles.markerPopupRow}>
                        <span className={styles.markerPopupRowLabel}>마커 생성 시각</span>
                        <time className={styles.markerPopupRowValue} dateTime={selectedMarker.occurredAt}>
                          {getMarkerPopupDateTimeLabel(selectedMarker)}
                        </time>
                      </div>
                    </div>
                    <div className={styles.markerPopupActions}>
                      <button
                        type="button"
                        className={styles.markerPopupSecondaryButton}
                        disabled={!selectedMarkerMemoOpId || isMapMemoSaving}
                        title={!selectedMarkerMemoOpId ? 'OP 확인 후 메모를 추가할 수 있습니다.' : undefined}
                        onClick={handleOpenMarkerMemoComposer}
                      >
                        메모 추가
                      </button>
                    </div>
                    {isMarkerMemoComposerOpen ? renderMapMemoComposer('마커', { closeComposerOnSuccess: true }) : null}
                    {canCorrectSelectedReferenceMarker ? (
                      <div className={styles.markerPopupActions}>
                        {selectedMarkerCorrectionStatus === 'editing' ||
                        selectedMarkerCorrectionStatus === 'saving' ? (
                          <>
                            <span className={styles.markerPopupActionHint}>지도를 이동해 기준점을 맞추세요.</span>
                            <button
                              type="button"
                              className={styles.markerPopupActionButton}
                              disabled={selectedMarkerCorrectionStatus === 'saving'}
                              onClick={handleCorrectSelectedReferenceMarker}
                            >
                              {selectedMarkerCorrectionStatus === 'saving' ? '저장 중' : '저장'}
                            </button>
                            <button
                              type="button"
                              className={styles.markerPopupSecondaryButton}
                              disabled={selectedMarkerCorrectionStatus === 'saving'}
                              onClick={handleCancelSelectedReferenceMarkerCorrection}
                            >
                              취소
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            className={styles.markerPopupActionButton}
                            onClick={handleStartSelectedReferenceMarkerCorrection}
                          >
                            위치 보정
                          </button>
                        )}
                        {selectedMarkerCorrectionStatus === 'saved' ? (
                          <span className={styles.markerPopupActionStatus}>저장 완료</span>
                        ) : null}
                        {selectedMarkerCorrectionStatus === 'error' ? (
                          <span className={styles.markerPopupActionError}>저장 실패</span>
                        ) : null}
                      </div>
                    ) : null}
                  </section>
                </div>
              ) : null}
            </div>,
            overlayPortalTarget,
          )
        : null}
      <div className={styles.surface} aria-label="Search map">
        <div ref={mapContainerRef} className={styles.canvas} />
        {selectedMarkerCorrectionStatus === 'editing' || selectedMarkerCorrectionStatus === 'saving' ? (
          <div className={styles.referenceCorrectionTarget} aria-hidden="true">
            <span />
          </div>
        ) : null}
        {areaEditMapProps && mapInstance ? (
          <AreaEditMapCanvas {...areaEditMapProps} externalMap={mapInstance} hideCanvas />
        ) : null}
        {handoverMapProps && mapInstance ? (
          <HandoverComparisonMap {...handoverMapProps} externalMap={mapInstance} hideCanvas />
        ) : null}
        {false ? (
          <aside className={styles.initialMapNotice} aria-live="polite">
            <strong>전체 수색 구역 필요</strong>
            <span>초기 기준 마커 또는 관할 기본 위치로 지도를 열었습니다.</span>
          </aside>
        ) : null}
        {isRouteEditorEnabled && selectedSearchAreaId ? (
          <RouteEditorPanel
            areaLabel={routeEditorAreaLabel}
            anchorCount={routeEditorCoordinates.length}
            coordinates={routeEditorGeneratedCoordinates}
            policePhoneId={routeEditorPolicePhoneId}
            startedAtLocal={routeEditorStartedAtLocal}
            endedAtLocal={routeEditorEndedAtLocal}
            markerType={routeEditorMarkerType}
            markerMemo={routeEditorMarkerMemo}
            status={routeEditorStatus}
            errorMessage={routeEditorErrorMessage}
            onPolicePhoneIdChange={setRouteEditorPolicePhoneId}
            onStartedAtLocalChange={setRouteEditorStartedAtLocal}
            onEndedAtLocalChange={setRouteEditorEndedAtLocal}
            onMarkerTypeChange={setRouteEditorMarkerType}
            onMarkerMemoChange={setRouteEditorMarkerMemo}
            onUndo={handleUndoRouteEditorPoint}
            onClear={handleClearRouteEditor}
            onSave={handleSaveRouteEditorPath}
            onCreateMarker={handleCreateRouteEditorMarker}
            onClose={handleCloseRouteEditor}
          />
        ) : null}
      </div>
    </>
  );
}
