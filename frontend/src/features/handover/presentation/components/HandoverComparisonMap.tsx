import { useCallback, useEffect, useMemo, useRef, type MutableRefObject } from 'react';
import { Focus, Minus, Plus } from 'lucide-react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
import { getAreaVisualStyle, type AreaVisualStyle } from '../../../../shared/model/areaColorRegistry';
import { createVWorldBaseStyle, V_WORLD_MAX_ZOOM } from '../../../../shared/map/vworldBaseMap';
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
import { type IncidentBoardResponse, type BoardSlotName } from '../../../board/api/incidentBoardApi';
import type { RecentMarker } from '../../../situationBoard/presentation/constants/mockSituationBoard';
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

type Position = [number, number];
type PolygonGeometry = { type: 'Polygon'; coordinates: Position[][] };
type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
type ComparisonGeometry = PolygonGeometry | LineStringGeometry | PointGeometry;
type ComparisonFeature = {
  type: 'Feature';
  properties: Record<string, string | number>;
  geometry: ComparisonGeometry;
};
type ComparisonFeatureCollection = {
  type: 'FeatureCollection';
  features: ComparisonFeature[];
};

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

const opColorPalette = ['#2563eb', '#f59e0b', '#0f766e', '#7c3aed', '#dc2626', '#0891b2'];
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
  const boardMarkers = useMemo(
    () => createComparisonBoardMarkers(board, selectedOpIds),
    [board, selectedOpIds],
  );
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
  collections: { areas: ComparisonFeatureCollection; paths: ComparisonFeatureCollection; markers: ComparisonFeatureCollection },
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

function createComparisonFeatureCollections(
  board: IncidentBoardResponse | null,
  incidentId: string,
  selectedOpIds: string[],
  focusedOpId: string | null,
) {
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
    routeColorsByAssignee.policePhoneId,
    {
      accountId: routeColorsByAssignee.accountOpId,
      policePhoneId: routeColorsByAssignee.policePhoneOpId,
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

function createOverallAreaFeatureCollection(board: IncidentBoardResponse | null, incidentId: string) {
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

function createComparisonBoardMarkers(
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
        fillColor: visualStyle.fillColor,
        lineColor: visualStyle.lineColor,
        fillOpacity: Math.max(visualStyle.fillOpacity, 0.18),
        lineOpacity: 0.98,
        lineWidth: areaKind === 'overall' ? 3 : areaKind === 'unit' ? 2.6 : 2.2,
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

function createMarkerFeature(
  row: Record<string, unknown>,
  index: number,
  incidentId: string,
  focusedOpId: string | null,
  selectedOpIds: string[],
): ComparisonFeature[] {
  const geometry = readGeometry(row);
  if (!geometry || geometry.type !== 'Point') return [];

  const opId = readRowOpId(row);
  const focused = !opId || opId === focusedOpId;
  const markerType = readString(row, 'markerType') ?? readString(row, 'type') ?? 'NOTE';

  return [
    {
      type: 'Feature',
      properties: {
        slot: 'marker',
        entityId: readString(row, 'id') ?? readString(row, 'markerId') ?? `marker-${index}`,
        incidentId,
        opId: opId ?? '',
        markerType,
        focused: String(focused),
        color: focused ? getMarkerColor(markerType) : getOpColor(opId, selectedOpIds),
        radius: focused ? 7 : 5.5,
        opacity: focused ? 0.95 : 0.72,
      },
      geometry,
    },
  ];
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
  if (value.type === 'Polygon' && Array.isArray(value.coordinates)) return true;
  if (value.type === 'LineString' && Array.isArray(value.coordinates)) return true;
  return value.type === 'Point' && Array.isArray(value.coordinates);
}

function rowBelongsToSelectedOp(row: Record<string, unknown>, selectedOpIdSet: Set<string>) {
  const opId = readRowOpId(row);
  return opId === null || selectedOpIdSet.has(opId);
}

function rowBelongsToSelectedOpId(opId: string, selectedOpIdSet: Set<string>) {
  return opId === '' || selectedOpIdSet.has(opId);
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
  const routeColorsByPolicePhoneId = new Map<string, string>();
  const routeColorsByAccountOpId = new Map<string, string>();
  const routeColorsByPolicePhoneOpId = new Map<string, string>();

  areaRows.forEach((row) => {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const opId = readRowOpId(row);
    const assignedAccounts = row.assignedAccounts;
    if (!areaId || !Array.isArray(assignedAccounts)) return;
    const routeColor = areaVisualStylesByAreaId.get(areaId)?.lineColor ?? getAreaVisualStyle(areaId).lineColor;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const accountId = readAccountId(account);
      const policePhoneId = readPolicePhoneId(account);
      if (accountId) {
        routeColorsByAccountId.set(accountId, routeColor);
        if (opId) routeColorsByAccountOpId.set(createRouteColorAssigneeKey(opId, accountId), routeColor);
      }
      if (policePhoneId) {
        routeColorsByPolicePhoneId.set(policePhoneId, routeColor);
        if (opId) routeColorsByPolicePhoneOpId.set(createRouteColorAssigneeKey(opId, policePhoneId), routeColor);
      }
    });
  });

  return {
    accountId: routeColorsByAccountId,
    policePhoneId: routeColorsByPolicePhoneId,
    accountOpId: routeColorsByAccountOpId,
    policePhoneOpId: routeColorsByPolicePhoneOpId,
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

function readSlotRows(board: IncidentBoardResponse, slot: BoardSlotName): Record<string, unknown>[] {
  const raw = board.slots[slot] as unknown;
  if (!raw) return [];
  if (Array.isArray(raw)) return (raw as unknown[]).filter(isRecord);
  return isRecord(raw) ? [raw] : [];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readPolicePhoneId(row: Record<string, unknown>) {
  return (
    readString(row, 'policePhoneId') ??
    readString(row, 'police_phone_id') ??
    readString(row, 'phoneId') ??
    readString(row, 'deviceId') ??
    readString(row, 'device_id')
  );
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

function emptyFeatureCollection(): ComparisonFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}
