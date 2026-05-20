import maplibregl, { type GeoJSONSource, type LayerSpecification } from 'maplibre-gl';

import { V_WORLD_BASE_LAYER_ID, V_WORLD_BASE_OPACITY } from '../../../../../shared/map/vworldBaseMap';
import type {
  BoardMapFeature,
  BoardMapFeatureCollection,
} from '../../../../../shared/model/boardMapFeatures';
import type { Position } from './searchMapCanvasData';

export const DEFAULT_GWANGJU_CENTER: [number, number] = [126.8325, 35.1547];
export const GWANGJU_BBOX: [number, number, number, number] = [126.647507, 35.052595, 127.017482, 35.256837];
export const DEFAULT_FIT_PADDING = 44;
export const FOCUSED_SEARCH_AREA_FIT_PADDING = 72;
export const FOCUSED_SEARCH_AREA_FIT_MAX_ZOOM = 16;
export const MARKER_SELECTED_POPUP_OFFSET_PX = 60;
export const MOVEMENT_PATH_SOURCE_ID = 'operational-movement-path';
export const INITIAL_MAP_FALLBACK_ZOOM = 12;

const ENABLE_LOCAL_ROUTE_EDITOR = false;
const ROUTE_EDITOR_SOURCE_ID = 'dev-route-editor-draft';
const ROUTE_EDITOR_LINE_LAYER_ID = 'dev-route-editor-draft-line';
const ROUTE_EDITOR_POINT_LAYER_ID = 'dev-route-editor-draft-point';
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
export const SEARCH_AREA_RENDER_LAYER_IDS = [
  SEARCH_AREA_FILL_LAYER_ID,
  SEARCH_AREA_COMPLETED_HATCH_LAYER_ID,
  ...SEARCH_AREA_LINE_LAYER_ORDER,
];
const MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID = 'operational-movement-path-compare-highlight';
const MOVEMENT_PATH_COMPARE_LAYER_ID = 'operational-movement-path-compare';
const MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID = 'operational-movement-path-vehicle-glow';
const MOVEMENT_PATH_VEHICLE_LAYER_ID = 'operational-movement-path-vehicle';
const MOVEMENT_PATH_FOOT_GLOW_LAYER_ID = 'operational-movement-path-foot-glow';
const MOVEMENT_PATH_FOOT_LAYER_ID = 'operational-movement-path-foot';
const MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID = 'operational-movement-path-unknown-glow';
const MOVEMENT_PATH_UNKNOWN_LAYER_ID = 'operational-movement-path-unknown';

type SearchAreaLevel = 'OVERALL' | 'UNIT' | 'TEAM';
type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
type OperationalFeature = BoardMapFeature;
type OperationalFeatureCollection = BoardMapFeatureCollection;
type RouteEditorFeatureCollection = {
  type: 'FeatureCollection';
  features: Array<
    | {
        type: 'Feature';
        properties: { slot: 'dev_route_editor'; geometryType: 'line' };
        geometry: LineStringGeometry;
      }
    | {
        type: 'Feature';
        properties: { slot: 'dev_route_editor'; geometryType: 'point'; index: string };
        geometry: PointGeometry;
      }
  >;
};
type InitialMapResolution =
  | { state: 'overall-ready'; bounds: maplibregl.LngLatBoundsLike; overallSearchArea: OperationalFeatureCollection }
  | { state: 'fallback'; bounds: maplibregl.LngLatBoundsLike | null };

export type InitialMapState = InitialMapResolution['state'];

export type LayerVisibility = {
  vehiclePath: boolean;
  footPath: boolean;
  searchArea: boolean;
  marker: boolean;
};

export function setOperationalGeoJsonSourceData(
  map: maplibregl.Map,
  sourceId: string,
  data: OperationalFeatureCollection,
) {
  const source = map.getSource(sourceId);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(data);
}

export function syncSearchAreaSourceDataWhenAvailable(
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

export function hasSearchAreaLayers(map: maplibregl.Map) {
  return SEARCH_AREA_RENDER_LAYER_IDS.every((layerId) => Boolean(map.getLayer(layerId)));
}

export function addRouteEditorLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, ROUTE_EDITOR_SOURCE_ID, createRouteEditorGeoJson([]));

  addLayer(map, {
    id: ROUTE_EDITOR_LINE_LAYER_ID,
    type: 'line',
    source: ROUTE_EDITOR_SOURCE_ID,
    filter: ['==', ['get', 'geometryType'], 'line'],
    paint: {
      'line-color': '#0b7285',
      'line-width': 4,
      'line-opacity': 0.92,
      'line-dasharray': [1.4, 0.7],
    },
  });

  addLayer(map, {
    id: ROUTE_EDITOR_POINT_LAYER_ID,
    type: 'circle',
    source: ROUTE_EDITOR_SOURCE_ID,
    filter: ['==', ['get', 'geometryType'], 'point'],
    paint: {
      'circle-color': '#0b7285',
      'circle-radius': 5,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
    },
  });
}

export function syncRouteEditorDraft(map: maplibregl.Map, coordinates: Position[]) {
  const source = map.getSource(ROUTE_EDITOR_SOURCE_ID);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(createRouteEditorGeoJson(coordinates));
}

export function syncBaseMapOpacity(map: maplibregl.Map) {
  applyBaseRasterOpacity(map);
}

export function addSearchAreaLayers(map: maplibregl.Map, overallSearchArea: OperationalFeatureCollection) {
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

export function addMovementPathLayers(map: maplibregl.Map, movementPaths: OperationalFeatureCollection) {
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

export function raiseMovementPathLayers(map: maplibregl.Map) {
  [
    MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID,
    MOVEMENT_PATH_COMPARE_LAYER_ID,
    MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID,
    MOVEMENT_PATH_FOOT_GLOW_LAYER_ID,
    MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID,
    MOVEMENT_PATH_VEHICLE_LAYER_ID,
    MOVEMENT_PATH_FOOT_LAYER_ID,
    MOVEMENT_PATH_UNKNOWN_LAYER_ID,
  ].forEach((layerId) => {
    if (map.getLayer(layerId)) {
      map.moveLayer(layerId);
    }
  });
}

export function syncSelectedSearchArea(map: maplibregl.Map, selectedSearchAreaId: string | null) {
  if (!hasSearchAreaLayers(map)) {
    return;
  }

  const selectedFilter = selectedSearchAreaId ? ['==', ['get', 'entityId'], selectedSearchAreaId] : false;
  map.setPaintProperty(SEARCH_AREA_FILL_LAYER_ID, 'fill-opacity', ['case', selectedFilter, 0.28, 0.12]);
  SEARCH_AREA_LINE_LAYER_STYLES.forEach((style) => {
    map.setPaintProperty(style.id, 'line-opacity', ['case', selectedFilter, 1, style.lineOpacity]);
  });
}

export function syncLayerVisibility(map: maplibregl.Map, layerVisibility: LayerVisibility) {
  setLayerVisibility(map, SEARCH_AREA_FILL_LAYER_ID, layerVisibility.searchArea);
  setLayerVisibility(map, SEARCH_AREA_COMPLETED_HATCH_LAYER_ID, layerVisibility.searchArea);
  SEARCH_AREA_LINE_LAYER_ORDER.forEach((layerId) => setLayerVisibility(map, layerId, layerVisibility.searchArea));
  setLayerVisibility(map, MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_GLOW_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_COMPARE_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
}

export function filterSearchAreasByLegendFilters(
  searchAreas: OperationalFeatureCollection,
  selectedFilterIds: import('../../constants/mockSituationBoard').SearchAreaLegendFilterId[],
): OperationalFeatureCollection {
  const selectedFilterIdSet = new Set(selectedFilterIds);

  return {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => {
      if (feature.properties.areaLevel === 'OVERALL') {
        return selectedFilterIdSet.has('overall_area');
      }

      if (feature.properties.areaLevel === 'UNIT') {
        return selectedFilterIdSet.has('unit_area');
      }

      if (feature.properties.areaLevel === 'TEAM' && feature.properties.status === 'COMPLETED') {
        return selectedFilterIdSet.has('completed_team_area');
      }

      if (feature.properties.areaLevel === 'TEAM') {
        return selectedFilterIdSet.has('team_area');
      }

      return true;
    }),
  };
}

export function filterMovementPathsByPolicePhoneLegendFilters(
  movementPaths: OperationalFeatureCollection,
  selectedFilterIds: import('../../constants/mockSituationBoard').PolicePhoneLegendFilterId[],
): OperationalFeatureCollection {
  const selectedFilterIdSet = new Set(selectedFilterIds);

  return {
    type: 'FeatureCollection',
    features: movementPaths.features.filter((feature) => {
      if (feature.properties.isActiveOp === 'true' && !selectedFilterIdSet.has('active_phone')) {
        return false;
      }

      switch (feature.properties.freshnessStatus) {
        case 'ONLINE':
          return selectedFilterIdSet.has('phone_online');
        case 'STALE':
          return selectedFilterIdSet.has('phone_stale');
        case 'LOST':
          return selectedFilterIdSet.has('phone_lost');
        default:
          return true;
      }
    }),
  };
}

export function getIsRouteEditorEnabled(): boolean {
  if (ENABLE_LOCAL_ROUTE_EDITOR) {
    return true;
  }

  if (typeof window === 'undefined') {
    return false;
  }

  return new URLSearchParams(window.location.search).get('routeEditor') === '1';
}

function addGeoJsonSource(
  map: maplibregl.Map,
  sourceId: string,
  data: string | OperationalFeatureCollection | RouteEditorFeatureCollection,
) {
  if (map.getSource(sourceId)) {
    return;
  }
  map.addSource(sourceId, {
    type: 'geojson',
    data,
  });
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

function applyBaseRasterOpacity(map: maplibregl.Map) {
  if (!map.getLayer(V_WORLD_BASE_LAYER_ID)) {
    return;
  }
  map.setPaintProperty(V_WORLD_BASE_LAYER_ID, 'raster-opacity', V_WORLD_BASE_OPACITY);
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

function createRouteEditorGeoJson(coordinates: Position[]): RouteEditorFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { slot: 'dev_route_editor', geometryType: 'line' },
        geometry: { type: 'LineString', coordinates },
      },
      ...coordinates.map((coordinate, index) => ({
        type: 'Feature' as const,
        properties: { slot: 'dev_route_editor' as const, geometryType: 'point' as const, index: String(index + 1) },
        geometry: { type: 'Point' as const, coordinates: coordinate },
      })),
    ],
  };
}

function setLayerVisibility(map: maplibregl.Map, layerId: string, isVisible: boolean) {
  if (!map.getLayer(layerId)) {
    return;
  }

  map.setLayoutProperty(layerId, 'visibility', isVisible ? 'visible' : 'none');
}

function getManifestGroupStatus(items: readonly OperationalFeature[]) {
  return { label: '대기', tone: 'waiting' as const };
}

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

export const EMPTY_OPERATIONAL_FEATURE_COLLECTION: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [],
};
