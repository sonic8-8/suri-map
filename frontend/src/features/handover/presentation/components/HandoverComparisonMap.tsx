import { useCallback, useEffect, useMemo, useRef, type CSSProperties, type MutableRefObject } from 'react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
import { MapControls } from '../../../../shared/ui';
import { createVWorldBaseStyle, V_WORLD_MAX_ZOOM } from '../../../../shared/map/vworldBaseMap';
import {
  createComparisonBoardMarkers,
  createComparisonFeatureCollections,
  createOverallAreaFeatureCollection,
  emptyFeatureCollection,
  type ComparisonFeatureCollection,
  type ComparisonFeatureCollections,
  type Position,
} from './handoverComparisonMapData';
import { type IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
import {
  clearMarkerElements,
  syncMarkerElements,
  type MarkerInstance,
  type MarkerInteractionHandlers,
} from '../../../situationBoard/presentation/components/map/boardMarkerLayer';
import styles from './HandoverComparisonMap.module.css';

export type HandoverComparisonMapProps = {
  baseMapMode?: 'standalone' | 'shared-base-map' | 'shared-situation-board';
  externalMap?: maplibregl.Map | null;
  hideCanvas?: boolean;
  isMapExpanded?: boolean;
  rightPanelWidthPx?: number;
  incidentId: string;
  board: IncidentBoardResponse | null;
  focusedOpId: string | null;
  selectedOpIds: string[];
  comparisonHighlightGeometryGeojson?: string | null;
  onToggleMapExpanded?: () => void;
};

export type HandoverComparisonMapSharedProps = Omit<
  HandoverComparisonMapProps,
  'externalMap' | 'hideCanvas' | 'isMapExpanded' | 'onToggleMapExpanded'
>;

const DEFAULT_JURISDICTION_CENTER: Position = [126.7525, 35.1598];
const DEFAULT_ZOOM = 12;
const FIT_PADDING = 42;
const FIT_MAX_ZOOM = 15;
const OVERALL_AREA_SOURCE_ID = 'handover-comparison-overall-area';
const AREA_SOURCE_ID = 'handover-comparison-area';
const PATH_SOURCE_ID = 'handover-comparison-path';
const MARKER_SOURCE_ID = 'handover-comparison-marker';
const REGION_HIGHLIGHT_SOURCE_ID = 'handover-comparison-region-highlight';
const OVERALL_AREA_FILL_LAYER_ID = 'handover-comparison-overall-area-fill';
const OVERALL_AREA_COMPLETED_HATCH_LAYER_ID = 'handover-comparison-overall-area-completed-hatch';
const OVERALL_AREA_LINE_LAYER_ID = 'handover-comparison-overall-area-line';
const AREA_FILL_LAYER_ID = 'handover-comparison-area-fill';
const AREA_COMPLETED_HATCH_PATTERN_ID = 'handover-comparison-completed-area-hatch';
const AREA_COMPLETED_HATCH_LAYER_ID = 'handover-comparison-area-completed-hatch';
const AREA_LINE_LAYER_IDS = {
  overall: 'handover-comparison-area-line-overall',
  unit: 'handover-comparison-area-line-unit',
  team: 'handover-comparison-area-line-team',
} as const;
const PATH_GLOW_LAYER_ID = 'handover-comparison-path-glow';
const PATH_LINE_LAYER_ID = 'handover-comparison-path-line';
const REGION_HIGHLIGHT_FILL_LAYER_ID = 'handover-comparison-region-highlight-fill';
const REGION_HIGHLIGHT_LINE_LAYER_ID = 'handover-comparison-region-highlight-line';

export function HandoverComparisonMap({
  baseMapMode = 'standalone',
  externalMap = null,
  hideCanvas = false,
  isMapExpanded = false,
  rightPanelWidthPx,
  incidentId,
  board,
  focusedOpId,
  selectedOpIds,
  comparisonHighlightGeometryGeojson = null,
  onToggleMapExpanded = () => {},
}: HandoverComparisonMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const boundsRef = useRef<LngLatBoundsLike | null>(null);
  const scheduledFitTimerRef = useRef<number | null>(null);
  const markerInstancesRef = useRef<Map<string, MarkerInstance>>(new Map());
  const isSharedSituationBoardMap = baseMapMode === 'shared-situation-board';
  const surfaceStyle = useMemo<CSSProperties>(() => {
    return {
      display: 'contents',
      ...(rightPanelWidthPx == null ? {} : { '--handover-right-panel-width': `${rightPanelWidthPx}px` }),
    } as CSSProperties;
  }, [rightPanelWidthPx]);
  const overallAreaFeatures = useMemo(() => createOverallAreaFeatureCollection(board, incidentId), [board, incidentId]);
  const visibleOverallAreaFeatures = useMemo(
    () => (isSharedSituationBoardMap ? emptyFeatureCollection() : overallAreaFeatures),
    [isSharedSituationBoardMap, overallAreaFeatures],
  );
  const featureCollections = useMemo(
    () => createComparisonFeatureCollections(board, incidentId, selectedOpIds, focusedOpId),
    [board, focusedOpId, incidentId, selectedOpIds],
  );
  const comparisonHighlightFeatures = useMemo(
    () => createComparisonHighlightFeatureCollection(comparisonHighlightGeometryGeojson),
    [comparisonHighlightGeometryGeojson],
  );
  const boardMarkers = useMemo(() => createComparisonBoardMarkers(board, selectedOpIds), [board, selectedOpIds]);
  const visibleMarkerIds = useMemo(() => boardMarkers.map((marker) => marker.id), [boardMarkers]);
  const markerInteractionHandlers = useMemo<MarkerInteractionHandlers>(
    () => ({
      onHoverMarker: () => {},
      onLeaveMarker: () => {},
      onSelectMarker: () => {},
      onCloseSelectedMarker: () => {},
    }),
    [],
  );
  const featureCollectionsRef = useRef(featureCollections);
  const comparisonHighlightFeaturesRef = useRef(comparisonHighlightFeatures);
  const overallAreaFeaturesRef = useRef(visibleOverallAreaFeatures);
  const boardMarkersRef = useRef(boardMarkers);
  const visibleMarkerIdsRef = useRef(visibleMarkerIds);
  const hasVisibleEvidence =
    visibleOverallAreaFeatures.features.length > 0 ||
    featureCollections.areas.features.length > 0 ||
    featureCollections.paths.features.length > 0 ||
    featureCollections.markers.features.length > 0 ||
    comparisonHighlightFeatures.features.length > 0;

  useEffect(() => {
    featureCollectionsRef.current = featureCollections;
  }, [featureCollections]);

  useEffect(() => {
    comparisonHighlightFeaturesRef.current = comparisonHighlightFeatures;
  }, [comparisonHighlightFeatures]);

  useEffect(() => {
    overallAreaFeaturesRef.current = visibleOverallAreaFeatures;
  }, [visibleOverallAreaFeatures]);

  useEffect(() => {
    boardMarkersRef.current = boardMarkers;
    visibleMarkerIdsRef.current = visibleMarkerIds;
  }, [boardMarkers, visibleMarkerIds]);

  const scheduleFitToEvidence = useCallback((map: maplibregl.Map, bounds: LngLatBoundsLike | null) => {
    if (!bounds) return;

    if (scheduledFitTimerRef.current !== null) {
      window.clearTimeout(scheduledFitTimerRef.current);
    }

    scheduledFitTimerRef.current = window.setTimeout(() => {
      scheduledFitTimerRef.current = null;
      if (mapRef.current !== map || !map.loaded()) return;
      fitMapToBounds(map, bounds);
    }, 0);
  }, []);

  const fitToEvidence = useCallback(() => {
    const map = mapRef.current;
    const bounds = boundsRef.current;
    if (!map || !bounds) return;

    fitMapToBounds(map, bounds);
  }, []);

  useEffect(() => {
    if (externalMap) return;
    if (!containerRef.current) return;

    const apiKey = getVWorldApiKey();
    let map: maplibregl.Map;
    try {
      map = new maplibregl.Map({
        container: containerRef.current,
        style: createVWorldBaseStyle(apiKey),
        center: DEFAULT_JURISDICTION_CENTER,
        zoom: DEFAULT_ZOOM,
        maxZoom: V_WORLD_MAX_ZOOM,
        attributionControl: false,
      });
    } catch (error) {
      console.error('Failed to initialize handover comparison map', error);
      return;
    }

    mapRef.current = map;
    map.once('load', () => {
      if (mapRef.current !== map) return;
      const latestFeatureCollections = featureCollectionsRef.current;
      const latestOverallAreaFeatures = overallAreaFeaturesRef.current;
      runMapMutation(() => {
        addComparisonLayers(map);
        syncOverallAreaSource(map, latestOverallAreaFeatures);
        syncComparisonSources(map, latestFeatureCollections);
        syncComparisonHighlightSource(map, comparisonHighlightFeaturesRef.current);
        syncMarkerElements(
          map,
          boardMarkersRef.current,
          visibleMarkerIdsRef.current,
          markerInstancesRef,
          true,
          markerInteractionHandlers,
        );
        boundsRef.current = getCollectionsBounds({
          areas: combineFeatureCollections(latestOverallAreaFeatures, latestFeatureCollections.areas),
          paths: latestFeatureCollections.paths,
          markers: combineFeatureCollections(latestFeatureCollections.markers, comparisonHighlightFeaturesRef.current),
        });
        fitMapToBounds(map, boundsRef.current);
      }, 'Failed to initialize handover comparison layers');
    });

    return () => {
      clearScheduledFit(scheduledFitTimerRef);
      clearMarkerElements(markerInstancesRef);
      mapRef.current = null;
      runMapMutation(() => map.remove(), 'Failed to remove handover comparison map');
    };
  }, [externalMap, markerInteractionHandlers]);

  useEffect(() => {
    if (!externalMap) return;

    mapRef.current = externalMap;

    const initializeExternalLayers = () => {
      if (mapRef.current !== externalMap) return;
      const latestFeatureCollections = featureCollectionsRef.current;
      const latestOverallAreaFeatures = overallAreaFeaturesRef.current;
      runMapMutation(() => {
        addComparisonLayers(externalMap);
        syncOverallAreaSource(externalMap, latestOverallAreaFeatures);
        syncComparisonSources(externalMap, latestFeatureCollections);
        syncComparisonHighlightSource(externalMap, comparisonHighlightFeaturesRef.current);
        syncMarkerElements(
          externalMap,
          boardMarkersRef.current,
          visibleMarkerIdsRef.current,
          markerInstancesRef,
          true,
          markerInteractionHandlers,
        );
        boundsRef.current = getCollectionsBounds({
          areas: combineFeatureCollections(latestOverallAreaFeatures, latestFeatureCollections.areas),
          paths: latestFeatureCollections.paths,
          markers: combineFeatureCollections(latestFeatureCollections.markers, comparisonHighlightFeaturesRef.current),
        });
        scheduleFitToEvidence(externalMap, boundsRef.current);
      }, 'Failed to initialize external handover comparison layers');
    };

    if (externalMap.loaded()) {
      initializeExternalLayers();
    } else {
      externalMap.once('load', initializeExternalLayers);
    }

    return () => {
      clearScheduledFit(scheduledFitTimerRef);
      clearMarkerElements(markerInstancesRef);
      externalMap.off('load', initializeExternalLayers);
      runMapMutation(() => {
        syncOverallAreaSource(externalMap, emptyFeatureCollection());
        syncComparisonSources(externalMap, {
          areas: emptyFeatureCollection(),
          paths: emptyFeatureCollection(),
          markers: emptyFeatureCollection(),
        });
        syncComparisonHighlightSource(externalMap, emptyFeatureCollection());
      }, 'Failed to clear external handover comparison layers');
      mapRef.current = null;
    };
  }, [externalMap, markerInteractionHandlers, scheduleFitToEvidence]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) return;

    runMapMutation(() => {
      addComparisonLayers(map);
      syncOverallAreaSource(map, visibleOverallAreaFeatures);
      syncComparisonSources(map, featureCollections);
      syncComparisonHighlightSource(map, comparisonHighlightFeatures);
      syncMarkerElements(map, boardMarkers, visibleMarkerIds, markerInstancesRef, true, markerInteractionHandlers);
      boundsRef.current = getCollectionsBounds({
        areas: combineFeatureCollections(visibleOverallAreaFeatures, featureCollections.areas),
        paths: featureCollections.paths,
        markers: combineFeatureCollections(featureCollections.markers, comparisonHighlightFeatures),
      });
      if (externalMap) {
        scheduleFitToEvidence(map, boundsRef.current);
      } else {
        fitMapToBounds(map, boundsRef.current);
      }
    }, 'Failed to sync handover comparison map');
  }, [
    boardMarkers,
    comparisonHighlightFeatures,
    externalMap,
    featureCollections,
    markerInteractionHandlers,
    scheduleFitToEvidence,
    visibleMarkerIds,
    visibleOverallAreaFeatures,
  ]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    map.resize();
    const resizeTimer = window.setTimeout(() => {
      map.resize();
    }, 220);

    return () => {
      window.clearTimeout(resizeTimer);
    };
  }, [isMapExpanded]);

  return (
    <div
      className={`${styles.surface}${hideCanvas ? ` ${styles.externalSurface}` : ''}`}
      style={surfaceStyle}
      aria-label="handover comparison map"
    >
      {hideCanvas ? null : <div ref={containerRef} className={styles.canvas} />}
      {hideCanvas ? null : (
        <MapControls
          isMapExpanded={isMapExpanded}
          onFitIncidentSearchArea={fitToEvidence}
          onToggleMapExpanded={onToggleMapExpanded}
          onZoomIn={() => mapRef.current?.zoomIn()}
          onZoomOut={() => mapRef.current?.zoomOut()}
        />
      )}
      {!isSharedSituationBoardMap && !hasVisibleEvidence ? (
        <aside className={styles.emptyOverlay} aria-live="polite">
          <strong>현재 OP 데이터가 없습니다.</strong>
          <span>OP를 최대 2개까지 켜면 수색 경로, 구역, 마커가 표시됩니다.</span>
        </aside>
      ) : null}

    </div>
  );
}
function addComparisonLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, OVERALL_AREA_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, AREA_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, PATH_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, MARKER_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, REGION_HIGHLIGHT_SOURCE_ID, emptyFeatureCollection());
  addCompletedAreaHatchPattern(map);

  addLayer(map, {
    id: OVERALL_AREA_FILL_LAYER_ID,
    type: 'fill',
    source: OVERALL_AREA_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': 0.12,
    },
  });

  addLayer(map, {
    id: OVERALL_AREA_COMPLETED_HATCH_LAYER_ID,
    type: 'fill',
    source: OVERALL_AREA_SOURCE_ID,
    filter: ['==', ['get', 'status'], 'COMPLETED'],
    paint: {
      'fill-pattern': AREA_COMPLETED_HATCH_PATTERN_ID,
      'fill-opacity': 0.45,
    },
  });

  addLayer(map, {
    id: OVERALL_AREA_LINE_LAYER_ID,
    type: 'line',
    source: OVERALL_AREA_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
      'line-dasharray': [2, 1.2],
    },
  } as LayerSpecification);

  addLayer(map, {
    id: AREA_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': 0.12,
    },
  });

  addLayer(map, {
    id: AREA_COMPLETED_HATCH_LAYER_ID,
    type: 'fill',
    source: AREA_SOURCE_ID,
    filter: ['==', ['get', 'status'], 'COMPLETED'],
    paint: {
      'fill-pattern': AREA_COMPLETED_HATCH_PATTERN_ID,
      'fill-opacity': 0.45,
    },
  });

  addLayer(map, {
    id: AREA_LINE_LAYER_IDS.overall,
    type: 'line',
    source: AREA_SOURCE_ID,
    filter: ['==', ['get', 'areaLevel'], 'OVERALL'],
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': 2,
      'line-opacity': 0.92,
      'line-dasharray': [2, 1.2],
    },
  } as LayerSpecification);

  addLayer(map, {
    id: AREA_LINE_LAYER_IDS.unit,
    type: 'line',
    source: AREA_SOURCE_ID,
    filter: ['==', ['get', 'areaLevel'], 'UNIT'],
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': 1,
      'line-gap-width': 3,
      'line-opacity': 0.98,
    },
  } as LayerSpecification);

  addLayer(map, {
    id: AREA_LINE_LAYER_IDS.team,
    type: 'line',
    source: AREA_SOURCE_ID,
    filter: ['==', ['get', 'areaLevel'], 'TEAM'],
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': 1,
      'line-opacity': 0.98,
    },
  } as LayerSpecification);

  addLayer(map, {
    id: PATH_GLOW_LAYER_ID,
    type: 'line',
    source: PATH_SOURCE_ID,
    filter: ['all', ['has', 'color'], ['!=', ['get', 'color'], '']],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'color'],
      'line-width': ['+', ['to-number', ['get', 'lineWidth']], 4.6],
      'line-opacity': ['to-number', ['get', 'outerOpacity']],
    },
  } as LayerSpecification);

  addLayer(map, {
    id: PATH_LINE_LAYER_ID,
    type: 'line',
    source: PATH_SOURCE_ID,
    filter: ['all', ['has', 'color'], ['!=', ['get', 'color'], '']],
    layout: {
      'line-cap': 'round',
      'line-join': 'round',
    },
    paint: {
      'line-color': ['get', 'coreColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
    },
  } as LayerSpecification);

  addLayer(map, {
    id: REGION_HIGHLIGHT_FILL_LAYER_ID,
    type: 'fill',
    source: REGION_HIGHLIGHT_SOURCE_ID,
    paint: {
      'fill-color': '#facc15',
      'fill-opacity': 0.28,
    },
  });

  addLayer(map, {
    id: REGION_HIGHLIGHT_LINE_LAYER_ID,
    type: 'line',
    source: REGION_HIGHLIGHT_SOURCE_ID,
    paint: {
      'line-color': '#f59e0b',
      'line-width': 3,
      'line-opacity': 0.96,
    },
  });
}

function addCompletedAreaHatchPattern(map: maplibregl.Map) {
  if (map.hasImage(AREA_COMPLETED_HATCH_PATTERN_ID)) {
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

  map.addImage(AREA_COMPLETED_HATCH_PATTERN_ID, context.getImageData(0, 0, canvas.width, canvas.height));
}

function syncOverallAreaSource(map: maplibregl.Map, data: ComparisonFeatureCollection) {
  setGeoJsonSourceData(map, OVERALL_AREA_SOURCE_ID, data);
}

function addGeoJsonSource(map: maplibregl.Map, sourceId: string, data: ComparisonFeatureCollection) {
  if (map.getSource(sourceId)) return;
  map.addSource(sourceId, {
    type: 'geojson',
    data,
  });
}

function addLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) return;
  map.addLayer(layer);
}

function syncComparisonSources(
  map: maplibregl.Map,
  collections: ComparisonFeatureCollections,
) {
  setGeoJsonSourceData(map, AREA_SOURCE_ID, collections.areas);
  setGeoJsonSourceData(map, PATH_SOURCE_ID, collections.paths);
  setGeoJsonSourceData(map, MARKER_SOURCE_ID, collections.markers);
}

function syncComparisonHighlightSource(map: maplibregl.Map, data: ComparisonFeatureCollection) {
  setGeoJsonSourceData(map, REGION_HIGHLIGHT_SOURCE_ID, data);
}

function createComparisonHighlightFeatureCollection(geometryGeojson: string | null): ComparisonFeatureCollection {
  const geometry = parseComparisonGeometry(geometryGeojson);
  if (!geometry) return emptyFeatureCollection();

  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { kind: 'opComparisonRegionHighlight' },
        geometry,
      },
    ],
  };
}

function parseComparisonGeometry(
  geometryGeojson: string | null,
): ComparisonFeatureCollection['features'][number]['geometry'] | null {
  if (!geometryGeojson) return null;

  try {
    const parsed = JSON.parse(geometryGeojson) as unknown;
    if (!isRecord(parsed) || !Array.isArray(parsed.coordinates)) return null;
    if (!['Polygon', 'MultiPolygon', 'LineString', 'Point'].includes(String(parsed.type))) return null;
    return parsed as ComparisonFeatureCollection['features'][number]['geometry'];
  } catch {
    return null;
  }
}

function setGeoJsonSourceData(map: maplibregl.Map, sourceId: string, data: ComparisonFeatureCollection) {
  const source = map.getSource(sourceId);
  if (!source || !('setData' in source)) return;
  runMapMutation(() => (source as GeoJSONSource).setData(data), `Failed to update source ${sourceId}`);
}

function fitMapToBounds(map: maplibregl.Map, bounds: LngLatBoundsLike | null) {
  if (bounds) {
    runMapMutation(
      () => map.fitBounds(bounds, { padding: FIT_PADDING, duration: 420, maxZoom: FIT_MAX_ZOOM }),
      'Failed to fit handover comparison map',
    );
  }
}

function runMapMutation(mutate: () => void, message: string) {
  try {
    mutate();
  } catch (error) {
    console.warn(message, error);
  }
}

function clearScheduledFit(timerRef: MutableRefObject<number | null>) {
  if (timerRef.current === null) return;
  window.clearTimeout(timerRef.current);
  timerRef.current = null;
}

function getCollectionsBounds(collections: {
  areas: ComparisonFeatureCollection;
  paths: ComparisonFeatureCollection;
  markers: ComparisonFeatureCollection;
}): LngLatBoundsLike | null {
  const bounds = new maplibregl.LngLatBounds();
  [...collections.areas.features, ...collections.paths.features, ...collections.markers.features].forEach((feature) => {
    extendBounds(bounds, feature.geometry.coordinates);
  });
  return bounds.isEmpty() ? null : bounds;
}

function combineFeatureCollections(
  left: ComparisonFeatureCollection,
  right: ComparisonFeatureCollection,
): ComparisonFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [...left.features, ...right.features],
  };
}

function extendBounds(bounds: maplibregl.LngLatBounds, coordinates: unknown): void {
  if (!Array.isArray(coordinates)) return;
  if (typeof coordinates[0] === 'number' && typeof coordinates[1] === 'number') {
    bounds.extend(coordinates as Position);
    return;
  }
  coordinates.forEach((item) => extendBounds(bounds, item));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
