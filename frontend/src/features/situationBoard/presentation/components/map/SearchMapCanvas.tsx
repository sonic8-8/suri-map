import { useEffect, useMemo, useRef, useState } from 'react';
import { useCallback } from 'react';
import { createPortal } from 'react-dom';
import maplibregl, { type GeoJSONSource, type LayerSpecification, type LngLatBoundsLike } from 'maplibre-gl';
import { getVWorldApiKey } from '../../../../../shared/config';
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
  createOperationalFeatureCollectionSignature,
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
  syncMarkerPopups,
  type MarkerInstance,
  type MarkerInteractionHandlers,
} from './boardMarkerLayer';
import { SearchAreaInspectorCard } from './SearchAreaInspectorCard';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { RouteEditorPanel } from './RouteEditorPanel';
import { addRouteEditorLayers, getIsRouteEditorEnabled, syncRouteEditorDraft } from './routeEditorLayer';
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
const INITIAL_MAP_FALLBACK_ZOOM = 12;

type OperationalFeatureCollection = BoardMapFeatureCollection;
type SearchAreaLevel = 'OVERALL' | 'UNIT' | 'TEAM';
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

function syncSearchAreaSourceData(map: maplibregl.Map, searchAreas: OperationalFeatureCollection, isVisible: boolean) {
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
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const isRouteEditorEnabledRef = useRef(getIsRouteEditorEnabled());
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
  const [routeEditorCoordinates, setRouteEditorCoordinates] = useState<Position[]>([]);
  const [mapInstance, setMapInstance] = useState<maplibregl.Map | null>(null);
  const [mapViewportVersion, setMapViewportVersion] = useState(0);
  const [hoveredMarkerId, setHoveredMarkerId] = useState<string | null>(null);
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);
  const [searchAreaPopupLngLat, setSearchAreaPopupLngLat] = useState<maplibregl.LngLatLike | null>(null);
  const searchAreaPopupOverlayRef = useRef<HTMLDivElement | null>(null);
  const searchAreaPopupSearchAreaIdRef = useRef<string | null>(null);
  const isRouteEditorEnabled = isRouteEditorEnabledRef.current;
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
  const visibleMovementPathFeatures = useMemo(
    () => filterMovementPathsByPolicePhoneLegendFilters(movementPathFeatures, selectedPolicePhoneLegendFilters),
    [movementPathFeatures, selectedPolicePhoneLegendFilters],
  );
  const assignedSearchAreasSignature = useMemo(
    () => createOperationalFeatureCollectionSignature(assignedSearchAreas),
    [assignedSearchAreas],
  );
  const assignedSearchAreasRef = useRef(visibleAssignedSearchAreas);
  const movementPathFeaturesRef = useRef(visibleMovementPathFeatures);
  const fittedSearchAreasSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    assignedSearchAreasRef.current = visibleAssignedSearchAreas;
  }, [visibleAssignedSearchAreas]);

  useEffect(() => {
    movementPathFeaturesRef.current = visibleMovementPathFeatures;
  }, [visibleMovementPathFeatures]);

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
      hoveredMarkerId,
      selectedMarkerId,
    );
  }, [hoveredMarkerId, layerVisibility, markerInteractionHandlers, selectedMarkerId, visibleMarkerIds]);

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
      const bounds = getSearchAreaBoundsById(visibleAssignedSearchAreas, focusedSearchAreaId);
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
  }, [visibleAssignedSearchAreas, focusedSearchAreaId, focusedSearchAreaSequence]);

  useEffect(() => {
    onSelectSearchAreaRef.current = onSelectSearchArea;
  }, [onSelectSearchArea]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    setOperationalGeoJsonSourceData(map, MOVEMENT_PATH_SOURCE_ID, visibleMovementPathFeatures);
  }, [visibleMovementPathFeatures]);

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
    if (fittedSearchAreasSignatureRef.current !== assignedSearchAreasSignature) {
      fittedSearchAreasSignatureRef.current = assignedSearchAreasSignature;
      map.fitBounds(assignedSearchAreaBounds, { padding: DEFAULT_FIT_PADDING, duration: 420, maxZoom: 15 });
    }
  }, [
    assignedSearchAreas,
    assignedSearchAreasSignature,
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
      setRouteEditorCoordinates((currentCoordinates) => [...currentCoordinates, routeEditorCoordinate]);
    };

    map.on('click', handleMapClick);

    map.once('load', () => {
      if (mapRef.current !== map) return;
      try {
        syncBaseMapOpacity(map);
        const currentAssignedSearchAreas = assignedSearchAreasRef.current;
        const currentMovementPathFeatures = movementPathFeaturesRef.current;
        addSearchAreaLayers(map, currentAssignedSearchAreas);
        addMovementPathLayers(map, currentMovementPathFeatures);
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
            fittedSearchAreasSignatureRef.current = createOperationalFeatureCollectionSignature(
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
                    onClose={handleCloseSearchAreaPopup}
                    onOpenAssign={handleOpenSearchAreaAssign}
                    onOpenSplit={handleOpenSearchAreaSplit}
                  />
                </div>
              ) : null}
              {selectedMarker && selectedMarkerPoint ? (
                <div
                  className={styles.markerSelectedOverlay}
                  style={{
                    left: `${selectedMarkerPoint.x}px`,
                    top: `${selectedMarkerPoint.y}px`,
                    transform: `translate(-50%, calc(-100% - ${MARKER_SELECTED_POPUP_OFFSET_PX}px))`,
                  }}
                >
                  <section
                    className={styles.markerPopup}
                    role="dialog"
                    aria-label={selectedMarker.title}
                    onClick={(event) => event.stopPropagation()}
                    onPointerDown={(event) => event.stopPropagation()}
                  >
                    <header className={styles.markerPopupHeader}>
                      <div className={styles.markerPopupHeaderMain}>
                        <div className={styles.markerPopupBadge} aria-hidden="true">
                          <span className={styles.markerPopupType}>
                            {selectedMarker.markerTypeLabel ?? selectedMarker.markerType}
                          </span>
                        </div>
                        <div className={styles.markerPopupTitleGroup}>
                          <div className={styles.markerPopupTypeRow}>
                            <span className={styles.markerPopupEyebrow}>마커 정보</span>
                            {selectedMarker.markerTypeLabel ? (
                              <span className={styles.markerPopupType}>{selectedMarker.markerTypeLabel}</span>
                            ) : null}
                          </div>
                          <div className={styles.markerPopupTitle}>{selectedMarker.title}</div>
                          {selectedMarker.timeLabel || selectedMarker.occurredAt ? (
                            <div className={styles.markerPopupMeta}>
                              {selectedMarker.timeLabel ?? selectedMarker.occurredAt}
                            </div>
                          ) : null}
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
                    {selectedMarker.photoThumbnailUrl ? (
                      <figure className={styles.markerPopupPhotoPreview}>
                        <img src={selectedMarker.photoThumbnailUrl} alt="" loading="lazy" decoding="async" />
                      </figure>
                    ) : null}
                    {(selectedMarker.memo ?? selectedMarker.summary) ? (
                      <p className={styles.markerPopupBody}>{selectedMarker.memo ?? selectedMarker.summary}</p>
                    ) : null}
                    <div className={styles.markerPopupDetail}>
                      {selectedMarker.reporterLabel ? (
                        <div className={styles.markerPopupRow}>
                          <span className={styles.markerPopupRowLabel}>보고자</span>
                          <span className={styles.markerPopupRowValue}>{selectedMarker.reporterLabel}</span>
                        </div>
                      ) : null}
                      {selectedMarker.sourceLabel ? (
                        <div className={styles.markerPopupRow}>
                          <span className={styles.markerPopupRowLabel}>출처</span>
                          <span className={styles.markerPopupRowValue}>{selectedMarker.sourceLabel}</span>
                        </div>
                      ) : null}
                      {selectedMarker.coordinateLabel ? (
                        <div className={styles.markerPopupRow}>
                          <span className={styles.markerPopupRowLabel}>좌표</span>
                          <span className={styles.markerPopupRowValue}>{selectedMarker.coordinateLabel}</span>
                        </div>
                      ) : null}
                      {typeof selectedMarker.photoCount === 'number' && selectedMarker.photoCount > 0 ? (
                        <div className={styles.markerPopupRow}>
                          <span className={styles.markerPopupRowLabel}>사진</span>
                          <span className={styles.markerPopupRowValue}>{`사진 ${selectedMarker.photoCount}장`}</span>
                        </div>
                      ) : null}
                    </div>
                  </section>
                </div>
              ) : null}
            </div>,
            overlayPortalTarget,
          )
        : null}
      <div className={styles.surface} aria-label="Search map">
        <div ref={mapContainerRef} className={styles.canvas} />
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
        {isRouteEditorEnabled ? (
          <RouteEditorPanel
            coordinates={routeEditorCoordinates}
            onClear={handleClearRouteEditor}
            onCopyGeoJson={handleCopyRouteEditorGeoJson}
          />
        ) : null}
      </div>
    </>
  );
}
