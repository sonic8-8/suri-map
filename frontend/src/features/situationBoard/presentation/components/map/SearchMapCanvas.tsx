import { useEffect, useMemo, useRef, useState } from 'react';
import { useCallback } from 'react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';
import { getVWorldApiKey } from '../../../../../shared/config';
import {
  AreaEditMapCanvas,
  type AreaEditMapCanvasProps,
} from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import {
  createVWorldBaseStyle,
  V_WORLD_BASE_LAYER_ID,
  V_WORLD_BASE_OPACITY,
  V_WORLD_MAX_ZOOM,
} from '../../../../../shared/map/vworldBaseMap';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import {
  createMovementPathFeatureCollection,
  createSearchAreaDraftFeatureCollection,
  type BoardMapFeature,
  type BoardMapFeatureCollection,
  type BoardMapGeometry,
} from '../../../../../shared/model/boardMapFeatures';
import type { MovementPath, RecentMarker } from '../../constants/mockSituationBoard';
import {
  clearMarkerElements,
  hasRenderedMarkerAtPoint,
  removeMarkerPopup,
  raiseMarkerLayer,
  syncMarkerElements,
  syncMarkerPopups,
  type MarkerInstance,
  type MarkerInteractionHandlers,
} from './boardMarkerLayer';
import styles from './SearchMapCanvas.module.css';

const DEFAULT_JURISDICTION_CENTER: [number, number] = [126.7525, 35.1598];
const GWANGSAN_MANIFEST_URL = '/map-data/gwangsan/manifest.json';
const MUDEUNGSAN_HIKING_TRAILS_URL = '/map-data/mudeungsan/trails.geojson';
const MUDEUNGSAN_OSM_TRAILS_URL = '/map-data/mudeungsan/osm-trails.geojson';
const MUDEUNGSAN_OSM_PEAKS_URL = '/map-data/mudeungsan/osm-peaks.geojson';
const DEFAULT_FIT_PADDING = 44;
const ENABLE_LOCAL_ROUTE_EDITOR = false;
const ROUTE_EDITOR_SOURCE_ID = 'dev-route-editor-draft';
const ROUTE_EDITOR_LINE_LAYER_ID = 'dev-route-editor-draft-line';
const ROUTE_EDITOR_POINT_LAYER_ID = 'dev-route-editor-draft-point';
const OVERALL_SEARCH_AREA_SOURCE_ID = 'operational-overall_search_area';
const SEARCH_AREA_FILL_LAYER_ID = 'operational-overall_search_area-fill';
const SEARCH_AREA_LINE_LAYER_ID = 'operational-overall_search_area-line';
const MOVEMENT_PATH_SOURCE_ID = 'operational-movement-path';
const MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID = 'operational-movement-path-compare-highlight';
const MOVEMENT_PATH_COMPARE_LAYER_ID = 'operational-movement-path-compare';
const MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID = 'operational-movement-path-vehicle-glow';
const MOVEMENT_PATH_VEHICLE_LAYER_ID = 'operational-movement-path-vehicle';
const MOVEMENT_PATH_FOOT_GLOW_LAYER_ID = 'operational-movement-path-foot-glow';
const MOVEMENT_PATH_FOOT_LAYER_ID = 'operational-movement-path-foot';
const MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID = 'operational-movement-path-unknown-glow';
const MOVEMENT_PATH_UNKNOWN_LAYER_ID = 'operational-movement-path-unknown';
const INITIAL_MAP_FALLBACK_ZOOM = 12;

type GwangsanLayerId = 'boundary';

type GwangsanMapLayerManifest = {
  layerId: GwangsanLayerId;
  url: string;
  sourceCodes: string[];
  crs: 'EPSG:4326';
  bbox: [number, number, number, number];
  featureCount: number;
};

type GwangsanMapManifest = {
  id: string;
  crs: 'EPSG:4326';
  layers: GwangsanMapLayerManifest[];
};

type Position = [number, number];
type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
type OperationalGeometry = BoardMapGeometry;
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
  | { state: 'overall-ready'; bounds: LngLatBoundsLike; overallSearchArea: OperationalFeatureCollection }
  | { state: 'fallback'; bounds: LngLatBoundsLike | null };

export type InitialMapState = InitialMapResolution['state'];

export type LayerVisibility = {
  vehiclePath: boolean;
  footPath: boolean;
  searchArea: boolean;
  marker: boolean;
};

const EMPTY_OPERATIONAL_FEATURE_COLLECTION: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [],
};

function createOperationalFeatureCollectionSignature(collection: OperationalFeatureCollection): string {
  return collection.features
    .map((feature) =>
      [
        feature.properties.entityId,
        feature.properties.areaLevel ?? '',
        feature.properties.status ?? '',
        feature.properties.version ?? '',
        JSON.stringify(feature.geometry.coordinates),
      ].join('|'),
    )
    .join(';');
}

function toBounds(bbox: [number, number, number, number]): LngLatBoundsLike {
  return [
    [bbox[0], bbox[1]],
    [bbox[2], bbox[3]],
  ];
}

function extendBounds(bounds: maplibregl.LngLatBounds, coordinates: unknown): void {
  if (!Array.isArray(coordinates)) {
    return;
  }
  if (typeof coordinates[0] === 'number' && typeof coordinates[1] === 'number') {
    bounds.extend(coordinates as Position);
    return;
  }
  coordinates.forEach((item) => extendBounds(bounds, item));
}

function getFeatureCollectionBounds(collection: OperationalFeatureCollection): LngLatBoundsLike | null {
  const bounds = new maplibregl.LngLatBounds();
  collection.features.forEach((feature) => extendBounds(bounds, feature.geometry.coordinates));
  return bounds.isEmpty() ? null : bounds;
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

function setOperationalGeoJsonSourceData(map: maplibregl.Map, sourceId: string, data: OperationalFeatureCollection) {
  const source = map.getSource(sourceId);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(data);
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

function getAssignedSearchAreaBounds(searchAreas: OperationalFeatureCollection): LngLatBoundsLike | null {
  const overallSearchAreas: OperationalFeatureCollection = {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => feature.geometry.type === 'Polygon' && feature.properties.areaLevel === 'OVERALL'),
  };
  const overallSearchAreaBounds = getFeatureCollectionBounds(overallSearchAreas);
  if (overallSearchAreaBounds) {
    return overallSearchAreaBounds;
  }

  const availableSearchAreas: OperationalFeatureCollection = {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => feature.geometry.type === 'Polygon'),
  };
  return getFeatureCollectionBounds(availableSearchAreas);
}

function resolveInitialMapView(
  fallbackBounds: LngLatBoundsLike | null,
  assignedSearchAreas: OperationalFeatureCollection,
): InitialMapResolution {
  const assignedSearchAreaBounds = getAssignedSearchAreaBounds(assignedSearchAreas);
  if (assignedSearchAreaBounds) {
    return {
      state: 'overall-ready',
      bounds: assignedSearchAreaBounds,
      overallSearchArea: assignedSearchAreas,
    };
  }

  return {
    state: 'fallback',
    bounds: fallbackBounds,
  };
}

function addLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) {
    return;
  }
  map.addLayer(layer);
}

function getIsRouteEditorEnabled(): boolean {
  if (ENABLE_LOCAL_ROUTE_EDITOR) {
    return true;
  }

  if (typeof window === 'undefined') {
    return false;
  }

  return new URLSearchParams(window.location.search).get('routeEditor') === '1';
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

function addRouteEditorLayers(map: maplibregl.Map) {
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

function syncRouteEditorDraft(map: maplibregl.Map, coordinates: Position[]) {
  const source = map.getSource(ROUTE_EDITOR_SOURCE_ID);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(createRouteEditorGeoJson(coordinates));
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

function addOverallSearchAreaLayer(map: maplibregl.Map, overallSearchArea: OperationalFeatureCollection) {
  addGeoJsonSource(map, OVERALL_SEARCH_AREA_SOURCE_ID, overallSearchArea);

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
    id: SEARCH_AREA_LINE_LAYER_ID,
    type: 'line',
    source: OVERALL_SEARCH_AREA_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
      'line-dasharray': [2, 1.2],
    },
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
  ].forEach((layerId) => {
    if (map.getLayer(layerId)) {
      map.moveLayer(layerId);
    }
  });
}

function syncSelectedSearchArea(map: maplibregl.Map, selectedSearchAreaId: string | null) {
  if (!map.getLayer(SEARCH_AREA_FILL_LAYER_ID) || !map.getLayer(SEARCH_AREA_LINE_LAYER_ID)) {
    return;
  }

  const selectedFilter = selectedSearchAreaId ? ['==', ['get', 'entityId'], selectedSearchAreaId] : false;
  map.setPaintProperty(SEARCH_AREA_FILL_LAYER_ID, 'fill-opacity', ['case', selectedFilter, 0.28, 0.12]);
  map.setPaintProperty(SEARCH_AREA_LINE_LAYER_ID, 'line-width', [
    'case',
    selectedFilter,
    ['+', ['to-number', ['get', 'lineWidth']], 1.4],
    ['to-number', ['get', 'lineWidth']],
  ]);
  map.setPaintProperty(SEARCH_AREA_LINE_LAYER_ID, 'line-opacity', ['case', selectedFilter, 1, ['to-number', ['get', 'lineOpacity']]]);
}

function setLayerVisibility(map: maplibregl.Map, layerId: string, isVisible: boolean) {
  if (!map.getLayer(layerId)) {
    return;
  }

  map.setLayoutProperty(layerId, 'visibility', isVisible ? 'visible' : 'none');
}

function syncLayerVisibility(map: maplibregl.Map, layerVisibility: LayerVisibility) {
  setLayerVisibility(map, SEARCH_AREA_FILL_LAYER_ID, layerVisibility.searchArea);
  setLayerVisibility(map, SEARCH_AREA_LINE_LAYER_ID, layerVisibility.searchArea);
  setLayerVisibility(map, MOVEMENT_PATH_COMPARE_HIGHLIGHT_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_VEHICLE_LAYER_ID, layerVisibility.vehiclePath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_GLOW_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_FOOT_LAYER_ID, layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_UNKNOWN_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
  setLayerVisibility(map, MOVEMENT_PATH_COMPARE_LAYER_ID, layerVisibility.vehiclePath || layerVisibility.footPath);
}

function addMudeungsanHikingTrailLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, 'mudeungsan-hiking-trails', MUDEUNGSAN_HIKING_TRAILS_URL);
  addGeoJsonSource(map, 'mudeungsan-osm-trails', MUDEUNGSAN_OSM_TRAILS_URL);
  addGeoJsonSource(map, 'mudeungsan-osm-peaks', MUDEUNGSAN_OSM_PEAKS_URL);

  addLayer(map, {
    id: 'mudeungsan-osm-trails-line',
    type: 'line',
    source: 'mudeungsan-osm-trails',
    paint: {
      'line-color': '#1b6f3a',
      'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.45, 14, 1.05, 16, 1.8],
      'line-opacity': 0.74,
    },
  });

  addLayer(map, {
    id: 'mudeungsan-hiking-trails-casing',
    type: 'line',
    source: 'mudeungsan-hiking-trails',
    paint: {
      'line-color': '#ffffff',
      'line-width': ['interpolate', ['linear'], ['zoom'], 11, 1.6, 14, 2.8, 16, 4.2],
      'line-opacity': 0.88,
    },
  });
  addLayer(map, {
    id: 'mudeungsan-hiking-trails-line',
    type: 'line',
    source: 'mudeungsan-hiking-trails',
    paint: {
      'line-color': '#2f9e44',
      'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.9, 14, 1.8, 16, 3],
      'line-opacity': 0.86,
      'line-dasharray': [1.2, 0.7],
    },
  });
  addLayer(map, {
    id: 'mudeungsan-osm-peaks-circle',
    type: 'circle',
    source: 'mudeungsan-osm-peaks',
    paint: {
      'circle-color': '#f08c00',
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 11, 2.2, 14, 3.6, 16, 5],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 1.4,
      'circle-opacity': 0.9,
    },
  });
  addLayer(map, {
    id: 'mudeungsan-osm-peaks-label',
    type: 'symbol',
    source: 'mudeungsan-osm-peaks',
    minzoom: 11.5,
    layout: {
      'text-field': ['get', 'name'],
      'text-font': ['Noto Sans Regular'],
      'text-size': ['interpolate', ['linear'], ['zoom'], 11.5, 10, 14, 11.5, 16, 13],
      'text-offset': [0, 1.05],
      'text-anchor': 'top',
      'text-allow-overlap': false,
      'text-ignore-placement': false,
      'symbol-sort-key': ['case', ['has', 'ele'], ['to-number', ['get', 'ele']], 0],
    },
    paint: {
      'text-color': '#3d2b16',
      'text-halo-color': '#ffffff',
      'text-halo-width': 1.35,
      'text-halo-blur': 0.2,
    },
  });
}

function addMudeungsanHikingTrailLayersSafely(map: maplibregl.Map) {
  try {
    addMudeungsanHikingTrailLayers(map);
  } catch (error) {
    console.error('[map] failed to add Mudeungsan hiking trail overlay', error);
  }
}

async function loadGwangsanManifest(): Promise<GwangsanMapManifest> {
  const response = await fetch(GWANGSAN_MANIFEST_URL);
  if (!response.ok) {
    throw new Error(`Failed to load Gwangsan map manifest: ${response.status}`);
  }
  return (await response.json()) as GwangsanMapManifest;
}

type SearchMapCanvasProps = {
  activeOperationalPeriodId: string | null;
  incidentId: string;
  layerVisibility: LayerVisibility;
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  visibleMarkerIds: string[];
  savedAreaDrafts: CompletedAreaDraft[];
  onInitialBoundsReady?: (bounds: LngLatBoundsLike | null) => void;
  onInitialMapStateReady?: (state: InitialMapResolution['state'] | null) => void;
  onMapReady?: (map: maplibregl.Map | null) => void;
  areaEditMapProps?: AreaEditMapCanvasProps | null;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SearchMapCanvas({
  activeOperationalPeriodId,
  incidentId,
  layerVisibility,
  movementPaths,
  recentMarkers,
  visibleMarkerIds,
  savedAreaDrafts,
  onInitialBoundsReady,
  onInitialMapStateReady,
  onMapReady,
  areaEditMapProps,
  selectedSearchAreaId,
  onSelectSearchArea,
}: SearchMapCanvasProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const isRouteEditorEnabledRef = useRef(getIsRouteEditorEnabled());
  const selectedSearchAreaIdRef = useRef(selectedSearchAreaId);
  const areaEditMapPropsRef = useRef(areaEditMapProps);
  const layerVisibilityRef = useRef(layerVisibility);
  const recentMarkersRef = useRef(recentMarkers);
  const markerInstancesRef = useRef<Map<string, MarkerInstance>>(new Map());
  const hoverMarkerPopupRef = useRef<maplibregl.Popup | null>(null);
  const selectedMarkerPopupRef = useRef<maplibregl.Popup | null>(null);
  const onSelectSearchAreaRef = useRef(onSelectSearchArea);
  const [routeEditorCoordinates, setRouteEditorCoordinates] = useState<Position[]>([]);
  const [mapInstance, setMapInstance] = useState<maplibregl.Map | null>(null);
  const [hoveredMarkerId, setHoveredMarkerId] = useState<string | null>(null);
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);
  const isRouteEditorEnabled = isRouteEditorEnabledRef.current;
  const assignedSearchAreas = useMemo(
    () => createSearchAreaDraftFeatureCollection(savedAreaDrafts, { incidentId, includeSlot: true }),
    [incidentId, savedAreaDrafts],
  );
  const movementPathFeatures = useMemo(
    () =>
      createMovementPathFeatureCollection(movementPaths, activeOperationalPeriodId, {
        includeLabel: true,
      }),
    [activeOperationalPeriodId, movementPaths],
  );
  const assignedSearchAreasSignature = useMemo(
    () => createOperationalFeatureCollectionSignature(assignedSearchAreas),
    [assignedSearchAreas],
  );
  const assignedSearchAreasRef = useRef(assignedSearchAreas);
  const movementPathFeaturesRef = useRef(movementPathFeatures);
  const fittedSearchAreasSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    assignedSearchAreasRef.current = assignedSearchAreas;
  }, [assignedSearchAreas]);

  useEffect(() => {
    movementPathFeaturesRef.current = movementPathFeatures;
  }, [movementPathFeatures]);

  useEffect(() => {
    selectedSearchAreaIdRef.current = selectedSearchAreaId;
  }, [selectedSearchAreaId]);

  useEffect(() => {
    areaEditMapPropsRef.current = areaEditMapProps;
  }, [areaEditMapProps]);

  const handleHoverMarker = useCallback((markerId: string) => {
    setHoveredMarkerId(markerId);
  }, []);

  const handleLeaveMarker = useCallback(() => {
    setHoveredMarkerId(null);
  }, []);

  const handleSelectMarker = useCallback((markerId: string) => {
    setSelectedMarkerId(markerId);
    setHoveredMarkerId(null);
  }, []);

  const handleCloseSelectedMarker = useCallback(() => {
    setSelectedMarkerId(null);
  }, []);

  const markerInteractionHandlers = useMemo<MarkerInteractionHandlers>(
    () => ({
      onHoverMarker: handleHoverMarker,
      onLeaveMarker: handleLeaveMarker,
      onSelectMarker: handleSelectMarker,
      onCloseSelectedMarker: handleCloseSelectedMarker,
    }),
    [handleCloseSelectedMarker, handleHoverMarker, handleLeaveMarker, handleSelectMarker],
  );

  useEffect(() => {
    layerVisibilityRef.current = layerVisibility;
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncMarkerElements(
      map,
      recentMarkersRef.current,
      visibleMarkerIds,
      markerInstancesRef,
      layerVisibility.marker,
      markerInteractionHandlers,
    );
  }, [layerVisibility, markerInteractionHandlers, visibleMarkerIds]);

  useEffect(() => {
    recentMarkersRef.current = recentMarkers;
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncMarkerElements(
      map,
      recentMarkers,
      visibleMarkerIds,
      markerInstancesRef,
      layerVisibilityRef.current.marker,
      markerInteractionHandlers,
    );
  }, [markerInteractionHandlers, recentMarkers, visibleMarkerIds]);

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
    const handlePointerDown = (event: PointerEvent) => {
      if (!selectedMarkerId) {
        return;
      }

      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }

      const isInsidePopup = selectedMarkerPopupRef.current?.getElement().contains(target) ?? false;
      if (!isInsidePopup) {
        setSelectedMarkerId(null);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
    };
  }, [selectedMarkerId]);

  useEffect(() => {
    onSelectSearchAreaRef.current = onSelectSearchArea;
  }, [onSelectSearchArea]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    setOperationalGeoJsonSourceData(map, MOVEMENT_PATH_SOURCE_ID, movementPathFeatures);
  }, [movementPathFeatures]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncSearchAreaSourceData(map, assignedSearchAreas, layerVisibilityRef.current.searchArea);
    syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
    const assignedSearchAreaBounds = getAssignedSearchAreaBounds(assignedSearchAreas);
    if (!assignedSearchAreaBounds) {
      onInitialBoundsReady?.(null);
      onInitialMapStateReady?.(null);
      return;
    }

    onInitialBoundsReady?.(assignedSearchAreaBounds);
    onInitialMapStateReady?.('overall-ready');
    if (fittedSearchAreasSignatureRef.current !== assignedSearchAreasSignature) {
      fittedSearchAreasSignatureRef.current = assignedSearchAreasSignature;
      map.fitBounds(assignedSearchAreaBounds, { padding: DEFAULT_FIT_PADDING, duration: 420, maxZoom: 15 });
    }
  }, [assignedSearchAreas, assignedSearchAreasSignature, onInitialBoundsReady, onInitialMapStateReady]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncSelectedSearchArea(map, selectedSearchAreaId);
  }, [selectedSearchAreaId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncSearchAreaSourceData(map, assignedSearchAreasRef.current, layerVisibility.searchArea);
    syncLayerVisibility(map, layerVisibility);
    syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
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
      return;
    }

    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    syncRouteEditorDraft(map, routeEditorCoordinates);
    console.info('[routeEditor] draft coordinates', routeEditorCoordinates);
  }, [isRouteEditorEnabled, routeEditorCoordinates]);

  const handleClearRouteEditor = () => {
    setRouteEditorCoordinates([]);
  };

  const handleCopyRouteEditorGeoJson = () => {
    const geoJson = {
      type: 'Feature',
      properties: {
        slot: 'dev_route_editor',
        target: 'PolicePhone mock route',
      },
      geometry: {
        type: 'LineString',
        coordinates: routeEditorCoordinates,
      },
    };
    const serializedGeoJson = JSON.stringify(geoJson, null, 2);

    if (navigator.clipboard) {
      void navigator.clipboard.writeText(serializedGeoJson).catch((error: unknown) => {
        console.error('[routeEditor] failed to copy GeoJSON', error);
      });
    }

    console.info('[routeEditor] GeoJSON', geoJson);
  };

  useEffect(() => {
    if (!mapContainerRef.current) {
      return;
    }

    const vWorldApiKey = getVWorldApiKey();

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: createVWorldBaseStyle(vWorldApiKey),
      center: DEFAULT_JURISDICTION_CENTER,
      zoom: INITIAL_MAP_FALLBACK_ZOOM,
      maxZoom: V_WORLD_MAX_ZOOM,
      attributionControl: false,
    });

    mapRef.current = map;
    setMapInstance(map);
    onMapReady?.(map);

    const handleMapClick = (event: maplibregl.MapMouseEvent) => {
      if (!isRouteEditorEnabledRef.current) {
        if (areaEditMapPropsRef.current || hasRenderedMarkerAtPoint(map, event.point)) {
          return;
        }

        if (!map.getLayer(SEARCH_AREA_FILL_LAYER_ID) || !map.getLayer(SEARCH_AREA_LINE_LAYER_ID)) {
          return;
        }

        if (!layerVisibilityRef.current.searchArea) {
          return;
        }

        const features = map.queryRenderedFeatures(event.point, { layers: [SEARCH_AREA_FILL_LAYER_ID, SEARCH_AREA_LINE_LAYER_ID] });
        const searchAreaId = features.find((feature) => typeof feature.properties?.entityId === 'string')?.properties
          ?.entityId;
        if (typeof searchAreaId === 'string') {
          onSelectSearchAreaRef.current(searchAreaId);
        }
        return;
      }

      const routeEditorCoordinate: Position = [event.lngLat.lng, event.lngLat.lat];
      setRouteEditorCoordinates((currentCoordinates) => [...currentCoordinates, routeEditorCoordinate]);

    };

    map.on('click', handleMapClick);

    map.once('load', () => {
      syncBaseMapOpacity(map);
      const currentAssignedSearchAreas = assignedSearchAreasRef.current;
      const currentMovementPathFeatures = movementPathFeaturesRef.current;
      addOverallSearchAreaLayer(map, currentAssignedSearchAreas);
      addMovementPathLayers(map, currentMovementPathFeatures);
      syncSelectedSearchArea(map, selectedSearchAreaIdRef.current);
      syncLayerVisibility(map, layerVisibilityRef.current);
      syncMarkerElements(
        map,
        recentMarkersRef.current,
        visibleMarkerIds,
        markerInstancesRef,
        layerVisibilityRef.current.marker,
        markerInteractionHandlers,
      );

      void loadGwangsanManifest()
        .then((manifest) => {
          addMudeungsanHikingTrailLayersSafely(map);
          raiseMovementPathLayers(map);
          raiseMarkerLayer(map);
          if (isRouteEditorEnabledRef.current) {
            addRouteEditorLayers(map);
          }

          const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
          const fallbackBounds = boundaryLayer ? toBounds(boundaryLayer.bbox) : null;
          const initialMapResolution = resolveInitialMapView(fallbackBounds, assignedSearchAreasRef.current);

          onInitialBoundsReady?.(initialMapResolution.bounds);
          onInitialMapStateReady?.(initialMapResolution.state);
          if (initialMapResolution.bounds) {
            fittedSearchAreasSignatureRef.current = createOperationalFeatureCollectionSignature(assignedSearchAreasRef.current);
            map.fitBounds(initialMapResolution.bounds, { padding: DEFAULT_FIT_PADDING, duration: 0, maxZoom: 15 });
          } else {
            map.setCenter(DEFAULT_JURISDICTION_CENTER);
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
      map.remove();
    };
  }, [onInitialBoundsReady, onInitialMapStateReady, onMapReady]);

  return (
    <div className={styles.surface} aria-label="Search map">
      <div ref={mapContainerRef} className={styles.canvas} />
      {areaEditMapProps && mapInstance ? (
        <AreaEditMapCanvas {...areaEditMapProps} externalMap={mapInstance} hideCanvas />
      ) : null}
      {false ? (
        <aside className={styles.initialMapNotice} aria-live="polite">
          <strong>전체 수색 구역 필요</strong>
          <span>초기 기준 마커 또는 관할 기본 위치로 지도를 열었습니다.</span>
        </aside>
      ) : null}
      {isRouteEditorEnabled ? (
        <aside className={styles.routeEditorPanel} aria-label="PolicePhone mock route editor">
          <div className={styles.routeEditorHeader}>
            <strong>Route Editor</strong>
            <span>{routeEditorCoordinates.length} points</span>
          </div>
          <pre className={styles.routeEditorCoordinates}>{JSON.stringify(routeEditorCoordinates, null, 2)}</pre>
          <div className={styles.routeEditorActions}>
            <button type="button" onClick={handleClearRouteEditor}>
              Clear
            </button>
            <button type="button" onClick={handleCopyRouteEditorGeoJson}>
              Copy GeoJSON
            </button>
          </div>
        </aside>
      ) : null}
    </div>
  );
}

