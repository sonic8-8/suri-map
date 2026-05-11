import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Focus, Minus, Plus } from 'lucide-react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
import { getAreaColor, getAreaVisualStyle } from '../../../../shared/model/areaColorRegistry';
import { createVWorldBaseStyle, V_WORLD_MAX_ZOOM } from '../../../../shared/map/vworldBaseMap';
import {
  createBoardMapMarkers,
  createBoardMovementPaths,
  type BoardMapMarker,
  type BoardMovementPath,
} from '../../../../shared/model/boardMapSlots';
import type { HandoverBoardResponseDto } from '../../data/getHandoverBoard';
import styles from './HandoverComparisonMap.module.css';

export type HandoverComparisonMapProps = {
  externalMap?: maplibregl.Map | null;
  hideCanvas?: boolean;
  incidentId: string;
  board: HandoverBoardResponseDto | null;
  focusedOpId: string | null;
  selectedOpIds: string[];
};

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
const AREA_SOURCE_ID = 'handover-comparison-area';
const PATH_SOURCE_ID = 'handover-comparison-path';
const MARKER_SOURCE_ID = 'handover-comparison-marker';
const AREA_FILL_LAYER_ID = 'handover-comparison-area-fill';
const AREA_LINE_LAYER_ID = 'handover-comparison-area-line';
const PATH_LINE_LAYER_ID = 'handover-comparison-path-line';
const MARKER_CIRCLE_LAYER_ID = 'handover-comparison-marker-circle';
const MARKER_SYMBOL_LAYER_ID = 'handover-comparison-marker-symbol';

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
  const featureCollections = useMemo(
    () => createComparisonFeatureCollections(board, incidentId, selectedOpIds, focusedOpId),
    [board, focusedOpId, incidentId, selectedOpIds],
  );
  const featureCollectionsRef = useRef(featureCollections);
  const hasVisibleEvidence =
    featureCollections.areas.features.length > 0 ||
    featureCollections.paths.features.length > 0 ||
    featureCollections.markers.features.length > 0;

  useEffect(() => {
    featureCollectionsRef.current = featureCollections;
  }, [featureCollections]);

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
      addComparisonLayers(map);
      syncComparisonSources(map, latestFeatureCollections);
      boundsRef.current = getCollectionsBounds(latestFeatureCollections);
      fitMapToBounds(map, boundsRef.current);
    });

    return () => {
      mapRef.current = null;
      map.remove();
    };
  }, [externalMap]);

  useEffect(() => {
    if (!externalMap) return;

    mapRef.current = externalMap;

    const initializeExternalLayers = () => {
      const latestFeatureCollections = featureCollectionsRef.current;
      addComparisonLayers(externalMap);
      syncComparisonSources(externalMap, latestFeatureCollections);
      boundsRef.current = getCollectionsBounds(latestFeatureCollections);
    };

    if (externalMap.loaded()) {
      initializeExternalLayers();
    } else {
      externalMap.once('load', initializeExternalLayers);
    }

    return () => {
      externalMap.off('load', initializeExternalLayers);
      syncComparisonSources(externalMap, {
        areas: emptyFeatureCollection(),
        paths: emptyFeatureCollection(),
        markers: emptyFeatureCollection(),
      });
      mapRef.current = null;
    };
  }, [externalMap]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) return;

    syncComparisonSources(map, featureCollections);
    boundsRef.current = getCollectionsBounds(featureCollections);
    fitMapToBounds(map, boundsRef.current);
  }, [featureCollections]);

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
  addGeoJsonSource(map, AREA_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, PATH_SOURCE_ID, emptyFeatureCollection());
  addGeoJsonSource(map, MARKER_SOURCE_ID, emptyFeatureCollection());

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
    id: PATH_LINE_LAYER_ID,
    type: 'line',
    source: PATH_SOURCE_ID,
    filter: ['all', ['has', 'color'], ['!=', ['get', 'color'], '']],
    paint: {
      'line-color': ['get', 'color'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
      'line-dasharray': ['case', ['==', ['get', 'focused'], 'true'], ['literal', [1, 0]], ['literal', [1.8, 1.1]]],
    },
  } as LayerSpecification);

  addLayer(map, {
    id: MARKER_CIRCLE_LAYER_ID,
    type: 'circle',
    source: MARKER_SOURCE_ID,
    paint: {
      'circle-color': ['get', 'color'],
      'circle-radius': ['to-number', ['get', 'radius']],
      'circle-opacity': ['to-number', ['get', 'opacity']],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': ['case', ['==', ['get', 'focused'], 'true'], 2.4, 1.4],
    },
  });

  addLayer(map, {
    id: MARKER_SYMBOL_LAYER_ID,
    type: 'symbol',
    source: MARKER_SOURCE_ID,
    layout: {
      'text-field': ['get', 'markerGlyph'],
      'text-size': ['case', ['==', ['get', 'focused'], 'true'], 12, 10],
      'text-font': ['Noto Sans Regular'],
      'text-allow-overlap': true,
      'text-ignore-placement': true,
    },
    paint: {
      'text-color': '#ffffff',
      'text-halo-color': 'rgba(15, 23, 42, 0.22)',
      'text-halo-width': 0.8,
    },
  });
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

function extendBounds(bounds: maplibregl.LngLatBounds, coordinates: unknown): void {
  if (!Array.isArray(coordinates)) return;
  if (typeof coordinates[0] === 'number' && typeof coordinates[1] === 'number') {
    bounds.extend(coordinates as Position);
    return;
  }
  coordinates.forEach((item) => extendBounds(bounds, item));
}

function createComparisonFeatureCollections(
  board: HandoverBoardResponseDto | null,
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
  const overallRows = readSlotRows(board, 'overall_search_area');
  const areaRows = readSlotRows(board, 'area').filter((row) => rowBelongsToSelectedOp(row, selectedOpIdSet));
  const routeColorsByPolicePhoneId = createRouteColorsByPolicePhoneId(areaRows);
  const paths = createBoardMovementPaths(board)
    .filter((path) => rowBelongsToSelectedOpId(path.opId, selectedOpIdSet))
    .map((path) => ({
      ...path,
      routeColor: path.policePhoneId ? routeColorsByPolicePhoneId.get(path.policePhoneId) ?? null : null,
    }))
    .filter((path) => path.routeColor);
  const markers = createBoardMapMarkers(board).filter((marker) => rowBelongsToSelectedOpId(marker.opId, selectedOpIdSet));

  return {
    areas: {
      type: 'FeatureCollection' as const,
      features: [
        ...overallRows.flatMap((row, index) => createAreaFeature(row, index, incidentId, 'OVERALL')),
        ...areaRows.flatMap((row, index) => createAreaFeature(row, index, incidentId)),
      ],
    },
    paths: {
      type: 'FeatureCollection' as const,
      features: paths.map((path) => createPathFeatureFromBoardPath(path, incidentId, focusedOpId)),
    },
    markers: {
      type: 'FeatureCollection' as const,
      features: markers.map((marker) => createMarkerFeatureFromBoardMarker(marker, incidentId, focusedOpId, selectedOpIds)),
    },
  };
}

function createAreaFeature(
  row: Record<string, unknown>,
  index: number,
  incidentId: string,
  defaultAreaLevel = 'UNIT',
): ComparisonFeature[] {
  const geometry = readGeometry(row);
  if (!geometry || geometry.type !== 'Polygon') return [];

  const opId = readRowOpId(row);
  const areaLevel = readString(row, 'areaLevel') ?? defaultAreaLevel;
  const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId') ?? `area-${index}`;
  const visualStyle = getAreaVisualStyle(areaId);
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
): ComparisonFeature {
  const focused = !path.opId || path.opId === focusedOpId;

  return {
    type: 'Feature',
    properties: {
      slot: 'path',
      entityId: path.id,
      incidentId,
      opId: path.opId,
      focused: String(focused),
      color: path.routeColor ?? '',
      lineOpacity: focused ? 0.94 : 0.62,
      lineWidth: focused ? 4.4 : 3,
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
        markerGlyph: markerTypeGlyph(marker.markerType),
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

function createRouteColorsByPolicePhoneId(areaRows: Record<string, unknown>[]) {
  const routeColorsByPolicePhoneId = new Map<string, string>();

  areaRows.forEach((row) => {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const assignedAccounts = row.assignedAccounts;
    if (!areaId || !Array.isArray(assignedAccounts)) return;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const policePhoneId = readPolicePhoneId(account);
      if (policePhoneId) routeColorsByPolicePhoneId.set(policePhoneId, getAreaColor(areaId));
    });
  });

  return routeColorsByPolicePhoneId;
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

function markerTypeGlyph(markerType: string) {
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

function readSlotRows(board: HandoverBoardResponseDto, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
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

function emptyFeatureCollection(): ComparisonFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}
