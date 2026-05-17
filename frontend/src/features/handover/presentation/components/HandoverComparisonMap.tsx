import { useCallback, useEffect, useMemo, useRef, type MutableRefObject } from 'react';
import { Focus, Minus, Plus } from 'lucide-react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
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
  externalMap?: maplibregl.Map | null;
  hideCanvas?: boolean;
  incidentId: string;
  board: IncidentBoardResponse | null;
  focusedOpId: string | null;
  selectedOpIds: string[];
};

export type HandoverComparisonMapSharedProps = Omit<HandoverComparisonMapProps, 'externalMap' | 'hideCanvas'>;

const DEFAULT_JURISDICTION_CENTER: Position = [126.7525, 35.1598];
const DEFAULT_ZOOM = 12;
const FIT_PADDING = 42;
const FIT_MAX_ZOOM = 15;
const OVERALL_AREA_SOURCE_ID = 'handover-comparison-overall-area';
const AREA_SOURCE_ID = 'handover-comparison-area';
const PATH_SOURCE_ID = 'handover-comparison-path';
const MARKER_SOURCE_ID = 'handover-comparison-marker';
const OVERALL_AREA_FILL_LAYER_ID = 'handover-comparison-overall-area-fill';
const OVERALL_AREA_LINE_LAYER_ID = 'handover-comparison-overall-area-line';
const AREA_FILL_LAYER_ID = 'handover-comparison-area-fill';
const AREA_LINE_LAYER_ID = 'handover-comparison-area-line';
const PATH_GLOW_LAYER_ID = 'handover-comparison-path-glow';
const PATH_LINE_LAYER_ID = 'handover-comparison-path-line';

export function HandoverComparisonMap({
  externalMap = null,
  hideCanvas = false,
  incidentId,
  board,
  focusedOpId,
  selectedOpIds,
}: HandoverComparisonMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const boundsRef = useRef<LngLatBoundsLike | null>(null);
  const scheduledFitTimerRef = useRef<number | null>(null);
  const markerInstancesRef = useRef<Map<string, MarkerInstance>>(new Map());
  const overallAreaFeatures = useMemo(() => createOverallAreaFeatureCollection(board, incidentId), [board, incidentId]);
  const featureCollections = useMemo(
    () => createComparisonFeatureCollections(board, incidentId, selectedOpIds, focusedOpId),
    [board, focusedOpId, incidentId, selectedOpIds],
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
  const overallAreaFeaturesRef = useRef(overallAreaFeatures);
  const boardMarkersRef = useRef(boardMarkers);
  const visibleMarkerIdsRef = useRef(visibleMarkerIds);
  const hasVisibleEvidence =
    overallAreaFeatures.features.length > 0 ||
    featureCollections.areas.features.length > 0 ||
    featureCollections.paths.features.length > 0 ||
    featureCollections.markers.features.length > 0;

  useEffect(() => {
    featureCollectionsRef.current = featureCollections;
  }, [featureCollections]);

  useEffect(() => {
    overallAreaFeaturesRef.current = overallAreaFeatures;
  }, [overallAreaFeatures]);

  useEffect(() => {
    boardMarkersRef.current = boardMarkers;
    visibleMarkerIdsRef.current = visibleMarkerIds;
  }, [boardMarkers, visibleMarkerIds]);

  const fitToEvidence = useCallback(() => {
    const map = mapRef.current;
    const bounds = boundsRef.current;
    if (!map || !bounds) return;

    map.fitBounds(bounds, {
      padding: FIT_PADDING,
      duration: 420,
      maxZoom: FIT_MAX_ZOOM,
    });
  }, []);

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

  useEffect(() => {
    if (externalMap) return;
    if (!containerRef.current) return;

    const apiKey = getVWorldApiKey();
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: createVWorldBaseStyle(apiKey),
      center: DEFAULT_JURISDICTION_CENTER,
      zoom: DEFAULT_ZOOM,
      maxZoom: V_WORLD_MAX_ZOOM,
      attributionControl: false,
    });

    mapRef.current = map;
    map.once('load', () => {
      const latestFeatureCollections = featureCollectionsRef.current;
      const latestOverallAreaFeatures = overallAreaFeaturesRef.current;
      addComparisonLayers(map);
      syncOverallAreaSource(map, latestOverallAreaFeatures);
      syncComparisonSources(map, latestFeatureCollections);
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
        markers: latestFeatureCollections.markers,
      });
      fitMapToBounds(map, boundsRef.current);
    });

    return () => {
      clearScheduledFit(scheduledFitTimerRef);
      clearMarkerElements(markerInstancesRef);
      mapRef.current = null;
      map.remove();
    };
  }, [externalMap, markerInteractionHandlers]);

  useEffect(() => {
    if (!externalMap) return;

    mapRef.current = externalMap;

    const initializeExternalLayers = () => {
      const latestFeatureCollections = featureCollectionsRef.current;
      const latestOverallAreaFeatures = overallAreaFeaturesRef.current;
      addComparisonLayers(externalMap);
      syncOverallAreaSource(externalMap, latestOverallAreaFeatures);
      syncComparisonSources(externalMap, latestFeatureCollections);
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
        markers: latestFeatureCollections.markers,
      });
      scheduleFitToEvidence(externalMap, boundsRef.current);
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
      syncOverallAreaSource(externalMap, emptyFeatureCollection());
      syncComparisonSources(externalMap, {
        areas: emptyFeatureCollection(),
        paths: emptyFeatureCollection(),
        markers: emptyFeatureCollection(),
      });
      mapRef.current = null;
    };
  }, [externalMap, markerInteractionHandlers, scheduleFitToEvidence]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) return;

    addComparisonLayers(map);
    syncOverallAreaSource(map, overallAreaFeatures);
    syncComparisonSources(map, featureCollections);
    syncMarkerElements(map, boardMarkers, visibleMarkerIds, markerInstancesRef, true, markerInteractionHandlers);
    boundsRef.current = getCollectionsBounds({
      areas: combineFeatureCollections(overallAreaFeatures, featureCollections.areas),
      paths: featureCollections.paths,
      markers: featureCollections.markers,
    });
    if (externalMap) {
      scheduleFitToEvidence(map, boundsRef.current);
    } else {
      fitMapToBounds(map, boundsRef.current);
    }
  }, [
    boardMarkers,
    externalMap,
    featureCollections,
    markerInteractionHandlers,
    overallAreaFeatures,
    scheduleFitToEvidence,
    visibleMarkerIds,
  ]);

  return (
    <div className={`${styles.surface}${hideCanvas ? ` ${styles.externalSurface}` : ''}`} aria-label="OP 비교 지도">
      {hideCanvas ? null : <div ref={containerRef} className={styles.canvas} />}
      <div className={styles.toolbar} aria-label="지도 조작">
        <button type="button" title="선택 OP 범위 보기" onClick={fitToEvidence}>
          <Focus aria-hidden="true" />
        </button>
        <button type="button" title="확대" onClick={() => mapRef.current?.zoomIn()}>
          <Plus aria-hidden="true" />
        </button>
        <button type="button" title="축소" onClick={() => mapRef.current?.zoomOut()}>
          <Minus aria-hidden="true" />
        </button>
      </div>

      {!hasVisibleEvidence ? (
        <aside className={styles.emptyOverlay} aria-live="polite">
          <strong>표시할 OP 기록이 없습니다.</strong>
          <span>선택한 OP에 경로, 구역, 마커 기록이 있으면 이 지도에 함께 표시됩니다.</span>
        </aside>
      ) : null}

      <div className={styles.legend} aria-label="OP 비교 범례">
        <span className={styles.legendItem}>
          <span className={styles.legendSwatch} />
          현재 선택 OP
        </span>
        <span className={styles.legendItem}>
          <span className={styles.legendSwatchCompare} />
          비교 OP
        </span>
        <span className={styles.legendItem}>
          <span className={styles.legendMarker} />
          마커
        </span>
      </div>
    </div>
  );
}

function addComparisonLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, OVERALL_AREA_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, AREA_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, PATH_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, MARKER_SOURCE_ID, emptyFeatureCollection());

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
    id: AREA_LINE_LAYER_ID,
    type: 'line',
    source: AREA_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
      'line-dasharray': [2, 1.2],
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

function setGeoJsonSourceData(map: maplibregl.Map, sourceId: string, data: ComparisonFeatureCollection) {
  const source = map.getSource(sourceId);
  if (!source || !('setData' in source)) return;
  (source as GeoJSONSource).setData(data);
}

function fitMapToBounds(map: maplibregl.Map, bounds: LngLatBoundsLike | null) {
  if (bounds) {
    map.fitBounds(bounds, { padding: FIT_PADDING, duration: 420, maxZoom: FIT_MAX_ZOOM });
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
