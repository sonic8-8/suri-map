import { useCallback, useEffect, useRef, useState } from 'react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import { createVWorldBaseStyle, V_WORLD_MAX_ZOOM } from '../../../../shared/map/vworldBaseMap';
import { getRouteCoreColor } from '../../../../shared/model/boardMapFeatures';
import type { AreaEditPosition, CompletedAreaDraft } from '../constants/mockAreaEdit';
import styles from './AreaEditMapCanvas.module.css';

const DEFAULT_JURISDICTION_CENTER: AreaEditPosition = [126.7525, 35.1598];
const GWANGSAN_MANIFEST_URL = '/map-data/gwangsan/manifest.json';
const MUDEUNGSAN_HIKING_TRAILS_URL = '/map-data/mudeungsan/trails.geojson';
const MUDEUNGSAN_OSM_TRAILS_URL = '/map-data/mudeungsan/osm-trails.geojson';
const MUDEUNGSAN_OSM_PEAKS_URL = '/map-data/mudeungsan/osm-peaks.geojson';
const DEFAULT_FIT_PADDING = 44;
const INITIAL_MAP_FALLBACK_ZOOM = 12;
const CLOSE_VERTEX_PIXEL_THRESHOLD = 12;

const AREA_EDIT_SOURCE_ID = 'area-edit-search-areas';
const AREA_EDIT_FILL_LAYER_ID = 'area-edit-fill';
const AREA_EDIT_LINE_LAYER_ID = 'area-edit-line';
const AREA_EDIT_SELECTED_FILL_LAYER_ID = 'area-edit-selected-fill';
const AREA_EDIT_DRAFT_SOURCE_ID = 'area-edit-draft';
const AREA_EDIT_DRAFT_FILL_LAYER_ID = 'area-edit-draft-fill';
const AREA_EDIT_DRAFT_LINE_LAYER_ID = 'area-edit-draft-line';
const AREA_EDIT_DRAFT_VERTEX_LAYER_ID = 'area-edit-draft-vertices';
const AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID = 'area-edit-completed-drafts';
const AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID = 'area-edit-completed-draft-fill';
const AREA_EDIT_COMPLETED_DRAFT_LINE_LAYER_ID = 'area-edit-completed-draft-line';
const AREA_EDIT_MOVEMENT_PATH_SOURCE_ID = 'area-edit-movement-path';
const AREA_EDIT_MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID = 'area-edit-movement-path-vehicle-glow';
const AREA_EDIT_MOVEMENT_PATH_VEHICLE_LAYER_ID = 'area-edit-movement-path-vehicle';
const AREA_EDIT_MOVEMENT_PATH_FOOT_GLOW_LAYER_ID = 'area-edit-movement-path-foot-glow';
const AREA_EDIT_MOVEMENT_PATH_FOOT_LAYER_ID = 'area-edit-movement-path-foot';
const AREA_EDIT_MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID = 'area-edit-movement-path-unknown-glow';
const AREA_EDIT_MOVEMENT_PATH_UNKNOWN_LAYER_ID = 'area-edit-movement-path-unknown';
const AREA_EDIT_MARKER_SOURCE_ID = 'area-edit-marker';
const AREA_EDIT_MARKER_LAYER_ID = 'area-edit-marker-circle';
const AREA_EDIT_MARKER_SYMBOL_LAYER_ID = 'area-edit-marker-symbol';

export type AreaEditMovementPath = {
  id: string;
  policePhoneId: string | null;
  routeColor: string | null;
  opId: string;
  movementType: 'VEHICLE' | 'FOOT' | 'UNKNOWN';
  coordinates: AreaEditPosition[];
};

export type AreaEditMapMarker = {
  id: string;
  markerType: 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';
  coordinates: AreaEditPosition;
};

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

type AreaFeatureProperties = Record<string, string>;
type PolygonFeature = {
  type: 'Feature';
  properties: AreaFeatureProperties;
  geometry: { type: 'Polygon'; coordinates: AreaEditPosition[][] };
};
type LineFeature = {
  type: 'Feature';
  properties: AreaFeatureProperties;
  geometry: { type: 'LineString'; coordinates: AreaEditPosition[] };
};
type PointFeature = {
  type: 'Feature';
  properties: AreaFeatureProperties;
  geometry: { type: 'Point'; coordinates: AreaEditPosition };
};
type AreaFeature = PolygonFeature | LineFeature | PointFeature;
type AreaFeatureCollection = {
  type: 'FeatureCollection';
  features: AreaFeature[];
};

function toBounds(bbox: [number, number, number, number]): LngLatBoundsLike {
  return [
    [bbox[0], bbox[1]],
    [bbox[2], bbox[3]],
  ];
}

function toRingBounds(ring: AreaEditPosition[]): LngLatBoundsLike | null {
  if (ring.length === 0) return null;

  let minLon = ring[0][0];
  let minLat = ring[0][1];
  let maxLon = ring[0][0];
  let maxLat = ring[0][1];

  for (const [lon, lat] of ring) {
    minLon = Math.min(minLon, lon);
    minLat = Math.min(minLat, lat);
    maxLon = Math.max(maxLon, lon);
    maxLat = Math.max(maxLat, lat);
  }

  return [
    [minLon, minLat],
    [maxLon, maxLat],
  ];
}

function findOverallDraftBounds(completedDrafts: CompletedAreaDraft[]): LngLatBoundsLike | null {
  const overallDraft = completedDrafts.find((draft) => draft.kind === 'overall' && draft.coordinates.length >= 4);
  return overallDraft ? toRingBounds(overallDraft.coordinates) : null;
}

function addGeoJsonSource(map: maplibregl.Map, sourceId: string, data: string | AreaFeatureCollection) {
  if (map.getSource(sourceId)) return;
  map.addSource(sourceId, { type: 'geojson', data });
}

function setGeoJsonSourceData(map: maplibregl.Map, sourceId: string, data: AreaFeatureCollection) {
  const source = map.getSource(sourceId);
  if (!source) return;
  (source as GeoJSONSource).setData(data);
}

function addLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) return;
  map.addLayer(layer);
}

async function loadGwangsanManifest(): Promise<GwangsanMapManifest> {
  const response = await fetch(GWANGSAN_MANIFEST_URL);
  if (!response.ok) throw new Error(`Gwangsan manifest load failed: ${response.status}`);
  return (await response.json()) as GwangsanMapManifest;
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
    console.error('[AreaEditMap] 무등산 등산로 레이어 추가 실패', error);
  }
}

function buildAreaFeatureCollection(): AreaFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}

function toCompletedDraftFeature(draft: CompletedAreaDraft): PolygonFeature {
  const visualStyle = areaColorTokens[draft.colorToken];
  const lineWidthByKind: Record<CompletedAreaDraft['kind'], number> = {
    overall: 3,
    unit: 2.6,
    team: 2.2,
  };

  return {
    type: 'Feature',
    properties: {
      entityId: draft.areaId,
      areaLevel: draft.kind.toUpperCase(),
      status: 'DRAFT_COMPLETED',
      fillColor: visualStyle.fillColor,
      lineColor: visualStyle.lineColor,
      fillOpacity: '0.12',
      lineWidth: String(lineWidthByKind[draft.kind]),
      lineOpacity: '0.98',
    },
    geometry: {
      type: 'Polygon',
      coordinates: [draft.coordinates],
    },
  };
}

function buildCompletedDraftFeatureCollection(completedDrafts: CompletedAreaDraft[]): AreaFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: completedDrafts.map(toCompletedDraftFeature),
  };
}

function toMovementPathFeature(path: AreaEditMovementPath, activeOperationalPeriodId: string | null): LineFeature {
  return {
    type: 'Feature',
    properties: {
      entityId: path.id,
      policePhoneId: path.policePhoneId ?? '',
      deviceColor: path.routeColor ?? '',
      routeCoreColor: getRouteCoreColor(path.routeColor),
      opId: path.opId,
      movementType: path.movementType,
      isActiveOp: String(path.opId === activeOperationalPeriodId),
    },
    geometry: {
      type: 'LineString',
      coordinates: path.coordinates,
    },
  };
}

function buildMovementPathFeatureCollection(
  movementPaths: AreaEditMovementPath[],
  activeOperationalPeriodId: string | null,
): AreaFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: movementPaths
      .filter((path) => path.coordinates.length >= 2 && path.routeColor)
      .map((path) => toMovementPathFeature(path, activeOperationalPeriodId)),
  };
}

function toMarkerFeature(marker: AreaEditMapMarker): PointFeature {
  return {
    type: 'Feature',
    properties: {
      entityId: marker.id,
      markerType: marker.markerType,
      markerGlyph: markerTypeGlyph(marker.markerType),
    },
    geometry: {
      type: 'Point',
      coordinates: marker.coordinates,
    },
  };
}

function markerTypeGlyph(markerType: AreaEditMapMarker['markerType']) {
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

function buildMarkerFeatureCollection(markers: AreaEditMapMarker[]): AreaFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: markers.map(toMarkerFeature),
  };
}

function buildDraftFeatureCollection(draftPoints: AreaEditPosition[], colorToken: AreaColorToken | null): AreaFeatureCollection {
  const visualStyle = areaColorTokens[colorToken ?? 'areaColor001'];
  const features: AreaFeature[] = draftPoints.map((position, index) => ({
    type: 'Feature',
    properties: {
      index: String(index),
      isStart: String(index === 0),
      fillColor: visualStyle.fillColor,
      lineColor: visualStyle.lineColor,
    },
    geometry: { type: 'Point', coordinates: position },
  }));

  if (draftPoints.length >= 2) {
    features.push({
      type: 'Feature',
      properties: { kind: 'draft-line', lineColor: visualStyle.lineColor },
      geometry: { type: 'LineString', coordinates: draftPoints },
    });
  }

  if (draftPoints.length >= 3) {
    features.push({
      type: 'Feature',
      properties: { kind: 'draft-fill', fillColor: visualStyle.fillColor },
      geometry: { type: 'Polygon', coordinates: [[...draftPoints, draftPoints[0]]] },
    });
  }

  return {
    type: 'FeatureCollection',
    features,
  };
}

function addSearchAreaLayers(map: maplibregl.Map, areas: AreaFeatureCollection) {
  addGeoJsonSource(map, AREA_EDIT_SOURCE_ID, areas);

  addLayer(map, {
    id: AREA_EDIT_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_EDIT_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': ['to-number', ['get', 'fillOpacity']],
    },
  });

  addLayer(map, {
    id: AREA_EDIT_LINE_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
    },
  });

  addLayer(map, {
    id: AREA_EDIT_SELECTED_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_EDIT_SOURCE_ID,
    filter: ['==', ['get', 'entityId'], ''],
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': 0.42,
    },
  });
}

function addDrawingLayers(map: maplibregl.Map, options: { showCompletedDrafts?: boolean } = {}) {
  const showCompletedDrafts = options.showCompletedDrafts ?? true;

  addGeoJsonSource(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, { type: 'FeatureCollection', features: [] });
  addGeoJsonSource(map, AREA_EDIT_DRAFT_SOURCE_ID, { type: 'FeatureCollection', features: [] });

  addLayer(map, {
    id: AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': showCompletedDrafts ? ['to-number', ['get', 'fillOpacity']] : 0,
    },
  });
  addLayer(map, {
    id: AREA_EDIT_COMPLETED_DRAFT_LINE_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': showCompletedDrafts ? ['to-number', ['get', 'lineOpacity']] : 0,
      'line-dasharray': [2, 1.2],
    },
  });
  addLayer(map, {
    id: AREA_EDIT_DRAFT_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_EDIT_DRAFT_SOURCE_ID,
    filter: ['==', ['geometry-type'], 'Polygon'],
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': 0.08,
    },
  });
  addLayer(map, {
    id: AREA_EDIT_DRAFT_LINE_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_DRAFT_SOURCE_ID,
    filter: ['==', ['geometry-type'], 'LineString'],
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': 2.8,
      'line-opacity': 0.96,
    },
  });
  addLayer(map, {
    id: AREA_EDIT_DRAFT_VERTEX_LAYER_ID,
    type: 'circle',
    source: AREA_EDIT_DRAFT_SOURCE_ID,
    filter: ['==', ['geometry-type'], 'Point'],
    paint: {
      'circle-color': ['case', ['==', ['get', 'isStart'], 'true'], '#ffffff', ['get', 'fillColor']],
      'circle-radius': ['case', ['==', ['get', 'isStart'], 'true'], 7, 5],
      'circle-stroke-color': ['get', 'lineColor'],
      'circle-stroke-width': 2.4,
    },
  });
}

function addMovementPathLayers(map: maplibregl.Map, movementPaths: AreaFeatureCollection) {
  addGeoJsonSource(map, AREA_EDIT_MOVEMENT_PATH_SOURCE_ID, movementPaths);

  addLayer(map, {
    id: AREA_EDIT_MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    id: AREA_EDIT_MOVEMENT_PATH_VEHICLE_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    id: AREA_EDIT_MOVEMENT_PATH_FOOT_GLOW_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    id: AREA_EDIT_MOVEMENT_PATH_FOOT_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    id: AREA_EDIT_MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    id: AREA_EDIT_MOVEMENT_PATH_UNKNOWN_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
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
    AREA_EDIT_MOVEMENT_PATH_VEHICLE_GLOW_LAYER_ID,
    AREA_EDIT_MOVEMENT_PATH_FOOT_GLOW_LAYER_ID,
    AREA_EDIT_MOVEMENT_PATH_UNKNOWN_GLOW_LAYER_ID,
    AREA_EDIT_MOVEMENT_PATH_VEHICLE_LAYER_ID,
    AREA_EDIT_MOVEMENT_PATH_FOOT_LAYER_ID,
    AREA_EDIT_MOVEMENT_PATH_UNKNOWN_LAYER_ID,
  ].forEach((layerId) => {
    if (map.getLayer(layerId)) {
      map.moveLayer(layerId);
    }
  });
}

function raiseMarkerLayers(map: maplibregl.Map) {
  [AREA_EDIT_MARKER_LAYER_ID, AREA_EDIT_MARKER_SYMBOL_LAYER_ID].forEach((layerId) => {
    if (map.getLayer(layerId)) {
      map.moveLayer(layerId);
    }
  });
}

function addMarkerLayers(map: maplibregl.Map, markers: AreaFeatureCollection) {
  addGeoJsonSource(map, AREA_EDIT_MARKER_SOURCE_ID, markers);

  addLayer(map, {
    id: AREA_EDIT_MARKER_LAYER_ID,
    type: 'circle',
    source: AREA_EDIT_MARKER_SOURCE_ID,
    paint: {
      'circle-color': [
        'match',
        ['get', 'markerType'],
        'CLUE',
        '#f59e0b',
        'PERSON_FOUND',
        '#dc2626',
        'FIELD_CONDITION',
        '#0ea5e9',
        'SUPPORT_REQUEST',
        '#7c3aed',
        'NOTE',
        '#475569',
        '#334155',
      ],
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 11, 8, 14, 10, 16, 12],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
      'circle-opacity': 0.94,
    },
  });

  addLayer(map, {
    id: AREA_EDIT_MARKER_SYMBOL_LAYER_ID,
    type: 'symbol',
    source: AREA_EDIT_MARKER_SOURCE_ID,
    layout: {
      'text-field': ['get', 'markerGlyph'],
      'text-size': ['interpolate', ['linear'], ['zoom'], 11, 10, 14, 12, 16, 14],
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

function signedArea(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function areSamePoint(a: AreaEditPosition, b: AreaEditPosition) {
  return a[0] === b[0] && a[1] === b[1];
}

function isBetween(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (
    Math.min(a[0], b[0]) <= c[0] &&
    c[0] <= Math.max(a[0], b[0]) &&
    Math.min(a[1], b[1]) <= c[1] &&
    c[1] <= Math.max(a[1], b[1])
  );
}

function segmentsIntersect(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition, d: AreaEditPosition) {
  const abC = signedArea(a, b, c);
  const abD = signedArea(a, b, d);
  const cdA = signedArea(c, d, a);
  const cdB = signedArea(c, d, b);

  if (abC === 0 && isBetween(a, b, c)) return true;
  if (abD === 0 && isBetween(a, b, d)) return true;
  if (cdA === 0 && isBetween(c, d, a)) return true;
  if (cdB === 0 && isBetween(c, d, b)) return true;

  return (abC > 0) !== (abD > 0) && (cdA > 0) !== (cdB > 0);
}

function isAdjacentEdge(firstIndex: number, secondIndex: number, edgeCount: number) {
  return Math.abs(firstIndex - secondIndex) === 1 || (firstIndex === 0 && secondIndex === edgeCount - 1);
}

function hasSelfIntersection(closedRing: AreaEditPosition[]) {
  const edgeCount = closedRing.length - 1;

  for (let firstIndex = 0; firstIndex < edgeCount; firstIndex += 1) {
    const firstStart = closedRing[firstIndex];
    const firstEnd = closedRing[firstIndex + 1];

    for (let secondIndex = firstIndex + 1; secondIndex < edgeCount; secondIndex += 1) {
      if (isAdjacentEdge(firstIndex, secondIndex, edgeCount)) continue;

      const secondStart = closedRing[secondIndex];
      const secondEnd = closedRing[secondIndex + 1];
      if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) return true;
    }
  }

  return false;
}

function countDistinctPoints(points: AreaEditPosition[]) {
  const pointKeys = new Set(points.map((point) => `${point[0]},${point[1]}`));
  return pointKeys.size;
}

function validateClosedRing(closedRing: AreaEditPosition[]) {
  if (closedRing.length < 4 || countDistinctPoints(closedRing.slice(0, -1)) < 3) {
    return '구역은 서로 다른 꼭짓점 3개 이상으로 닫아야 합니다.';
  }

  if (!areSamePoint(closedRing[0], closedRing[closedRing.length - 1])) {
    return '구역을 완료하려면 마지막 점이 시작점과 같아야 합니다.';
  }

  if (hasSelfIntersection(closedRing)) {
    return '구역 경계선이 서로 교차합니다. 교차하지 않는 하나의 닫힌 구역으로 다시 지정하세요.';
  }

  return null;
}

export type AreaEditMapCanvasProps = {
  externalMap?: maplibregl.Map | null;
  hideCanvas?: boolean;
  canCompleteDraft: boolean;
  completedDrafts: CompletedAreaDraft[];
  draftPoints: AreaEditPosition[];
  isDrawing: boolean;
  activeOperationalPeriodId: string | null;
  mapMarkers: AreaEditMapMarker[];
  movementPaths: AreaEditMovementPath[];
  normalSelectedAreaId: string | null;
  normalSelectedAreaPosition: AreaEditPosition | null;
  onBoundsReady?: (bounds: LngLatBoundsLike | null) => void;
  onClearNormalAreaSelection: () => void;
  onCloseDraft: (coordinates: AreaEditPosition[]) => void;
  onConfirmDraft: () => void;
  onDraftPointAdd: (position: AreaEditPosition) => void;
  onMapReady?: (map: maplibregl.Map | null) => void;
  onNormalAreaSelect: (areaId: string, position: AreaEditPosition) => void;
  onRequestAreaDelete: (areaId: string) => void;
  selectedAreaColorToken: AreaColorToken | null;
  selectedAreaId: string | null;
  onSelectArea: (areaId: string) => void;
  onUndoDraft: () => void;
  onValidationMessage: (message: string) => void;
};

export function AreaEditMapCanvas({
  externalMap = null,
  hideCanvas = false,
  activeOperationalPeriodId,
  canCompleteDraft,
  completedDrafts,
  draftPoints,
  isDrawing,
  mapMarkers,
  movementPaths,
  normalSelectedAreaId,
  normalSelectedAreaPosition,
  onBoundsReady,
  onClearNormalAreaSelection,
  onCloseDraft,
  onConfirmDraft,
  onDraftPointAdd,
  onMapReady,
  onNormalAreaSelect,
  onRequestAreaDelete,
  selectedAreaColorToken,
  selectedAreaId,
  onSelectArea,
  onUndoDraft,
  onValidationMessage,
}: AreaEditMapCanvasProps) {
  const shouldRenderReferenceLayers = !externalMap;
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [floatingControlPosition, setFloatingControlPosition] = useState<{ x: number; y: number } | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);
  const completedDraftsRef = useRef(completedDrafts);
  const fittedOverallAreaIdRef = useRef<string | null>(null);
  const draftPointsRef = useRef(draftPoints);
  const isDrawingRef = useRef(isDrawing);
  const activeOperationalPeriodIdRef = useRef(activeOperationalPeriodId);
  const mapMarkersRef = useRef(mapMarkers);
  const movementPathsRef = useRef(movementPaths);
  const normalSelectedAreaIdRef = useRef(normalSelectedAreaId);
  const normalSelectedAreaPositionRef = useRef(normalSelectedAreaPosition);
  const selectedAreaColorTokenRef = useRef(selectedAreaColorToken);
  const selectedAreaIdRef = useRef(selectedAreaId);

  const onMapReadyRef = useRef(onMapReady);
  const onBoundsReadyRef = useRef(onBoundsReady);
  const onSelectAreaRef = useRef(onSelectArea);
  const onNormalAreaSelectRef = useRef(onNormalAreaSelect);
  const onClearNormalAreaSelectionRef = useRef(onClearNormalAreaSelection);
  const onDraftPointAddRef = useRef(onDraftPointAdd);
  const onCloseDraftRef = useRef(onCloseDraft);
  const onValidationMessageRef = useRef(onValidationMessage);

  useEffect(() => { onMapReadyRef.current = onMapReady; }, [onMapReady]);
  useEffect(() => { onBoundsReadyRef.current = onBoundsReady; }, [onBoundsReady]);
  useEffect(() => { onSelectAreaRef.current = onSelectArea; }, [onSelectArea]);
  useEffect(() => { onNormalAreaSelectRef.current = onNormalAreaSelect; }, [onNormalAreaSelect]);
  useEffect(() => { onClearNormalAreaSelectionRef.current = onClearNormalAreaSelection; }, [onClearNormalAreaSelection]);
  useEffect(() => { onDraftPointAddRef.current = onDraftPointAdd; }, [onDraftPointAdd]);
  useEffect(() => { onCloseDraftRef.current = onCloseDraft; }, [onCloseDraft]);
  useEffect(() => { onValidationMessageRef.current = onValidationMessage; }, [onValidationMessage]);

  useEffect(() => {
    activeOperationalPeriodIdRef.current = activeOperationalPeriodId;
    if (!shouldRenderReferenceLayers) return;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(
      map,
      AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
      buildMovementPathFeatureCollection(movementPathsRef.current, activeOperationalPeriodId),
    );
  }, [activeOperationalPeriodId, shouldRenderReferenceLayers]);

  useEffect(() => {
    movementPathsRef.current = movementPaths;
    if (!shouldRenderReferenceLayers) return;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(
      map,
      AREA_EDIT_MOVEMENT_PATH_SOURCE_ID,
      buildMovementPathFeatureCollection(movementPaths, activeOperationalPeriodIdRef.current),
    );
  }, [movementPaths, shouldRenderReferenceLayers]);

  useEffect(() => {
    mapMarkersRef.current = mapMarkers;
    if (!shouldRenderReferenceLayers) return;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(map, AREA_EDIT_MARKER_SOURCE_ID, buildMarkerFeatureCollection(mapMarkers));
  }, [mapMarkers, shouldRenderReferenceLayers]);

  const updateFloatingControlPosition = useCallback(() => {
    const map = mapRef.current;
    const points = draftPointsRef.current;

    if (!map || !isDrawingRef.current || points.length === 0) {
      setFloatingControlPosition(null);
      return;
    }

    const anchor = points[points.length - 1];
    const projectedPoint = map.project(anchor);
    setFloatingControlPosition({ x: projectedPoint.x, y: projectedPoint.y });
  }, []);

  const updateTooltipPosition = useCallback(() => {
    const map = mapRef.current;
    const position = normalSelectedAreaPositionRef.current;

    if (!map || !position || isDrawingRef.current) {
      setTooltipPosition(null);
      return;
    }

    const projectedPoint = map.project(position);
    setTooltipPosition({ x: projectedPoint.x, y: projectedPoint.y });
  }, []);

  useEffect(() => {
    completedDraftsRef.current = completedDrafts;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, buildCompletedDraftFeatureCollection(completedDrafts));

    const overallDraft = completedDrafts.find((draft) => draft.kind === 'overall' && draft.coordinates.length >= 4);
    if (!overallDraft || fittedOverallAreaIdRef.current === overallDraft.areaId) return;

    const bounds = toRingBounds(overallDraft.coordinates);
    if (!bounds) return;

    fittedOverallAreaIdRef.current = overallDraft.areaId;
    map.fitBounds(bounds, { padding: DEFAULT_FIT_PADDING, duration: 260, maxZoom: 15 });
    onBoundsReadyRef.current?.(bounds);
  }, [completedDrafts]);

  useEffect(() => {
    normalSelectedAreaIdRef.current = normalSelectedAreaId;
    normalSelectedAreaPositionRef.current = normalSelectedAreaPosition;
    updateTooltipPosition();
  }, [normalSelectedAreaId, normalSelectedAreaPosition, updateTooltipPosition]);

  useEffect(() => {
    draftPointsRef.current = draftPoints;
    const map = mapRef.current;
    if (map) {
      setGeoJsonSourceData(map, AREA_EDIT_DRAFT_SOURCE_ID, buildDraftFeatureCollection(draftPoints, selectedAreaColorTokenRef.current));
    }
    updateFloatingControlPosition();
  }, [draftPoints, updateFloatingControlPosition]);

  useEffect(() => {
    selectedAreaColorTokenRef.current = selectedAreaColorToken;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(map, AREA_EDIT_DRAFT_SOURCE_ID, buildDraftFeatureCollection(draftPointsRef.current, selectedAreaColorToken));
  }, [selectedAreaColorToken]);

  useEffect(() => {
    isDrawingRef.current = isDrawing;
    mapRef.current?.getCanvas().classList.toggle(styles.drawingCursor, isDrawing);
    updateFloatingControlPosition();
    updateTooltipPosition();
  }, [isDrawing, updateFloatingControlPosition, updateTooltipPosition]);

  useEffect(() => {
    selectedAreaIdRef.current = selectedAreaId;
    const map = mapRef.current;
    if (!map || !map.getLayer(AREA_EDIT_SELECTED_FILL_LAYER_ID)) return;
    map.setFilter(AREA_EDIT_SELECTED_FILL_LAYER_ID, ['==', ['get', 'entityId'], selectedAreaId ?? '']);
  }, [selectedAreaId]);

  const handleAreaClick = useCallback((event: maplibregl.MapLayerMouseEvent) => {
    if (isDrawingRef.current) return;
    const feature = event.features?.[0];
    if (!feature) return;
    const entityId = feature.properties?.entityId as string | undefined;
    if (entityId) onSelectAreaRef.current(entityId);
  }, []);

  const handleMapClick = useCallback((event: maplibregl.MapMouseEvent) => {
    if (!isDrawingRef.current) {
      const map = mapRef.current;
      const clickedFeatures = map?.queryRenderedFeatures(event.point, {
        layers: [AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID, AREA_EDIT_COMPLETED_DRAFT_LINE_LAYER_ID],
      });
      const clickedAreaId = clickedFeatures?.[0]?.properties?.entityId as string | undefined;

      if (clickedAreaId) {
        onNormalAreaSelectRef.current(clickedAreaId, [event.lngLat.lng, event.lngLat.lat]);
        return;
      }

      onClearNormalAreaSelectionRef.current();
      return;
    }

    const position: AreaEditPosition = [event.lngLat.lng, event.lngLat.lat];
    const currentPoints = draftPointsRef.current;
    const firstPoint = currentPoints[0];

    if (firstPoint && currentPoints.length >= 2) {
      const map = mapRef.current;
      const firstPixel = map?.project(firstPoint);
      if (firstPixel && Math.hypot(firstPixel.x - event.point.x, firstPixel.y - event.point.y) <= CLOSE_VERTEX_PIXEL_THRESHOLD) {
        const closedRing: AreaEditPosition[] = [...currentPoints, firstPoint];
        const validationError = validateClosedRing(closedRing);

        if (validationError) {
          onValidationMessageRef.current(validationError);
          return;
        }

        onCloseDraftRef.current(closedRing);
        return;
      }
    }

    onDraftPointAddRef.current(position);
  }, []);

  useEffect(() => {
    if (!externalMap) return;

    mapRef.current = externalMap;
    onMapReadyRef.current?.(externalMap);
    let isInitialized = false;

    const initializeExternalLayers = () => {
      if (isInitialized || !externalMap.isStyleLoaded()) return;
      isInitialized = true;
      addSearchAreaLayers(externalMap, buildAreaFeatureCollection());
      addDrawingLayers(externalMap, { showCompletedDrafts: false });
      setGeoJsonSourceData(
        externalMap,
        AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID,
        buildCompletedDraftFeatureCollection(completedDraftsRef.current),
      );
      setGeoJsonSourceData(
        externalMap,
        AREA_EDIT_DRAFT_SOURCE_ID,
        buildDraftFeatureCollection(draftPointsRef.current, selectedAreaColorTokenRef.current),
      );
      externalMap.setFilter(AREA_EDIT_SELECTED_FILL_LAYER_ID, ['==', ['get', 'entityId'], selectedAreaIdRef.current ?? '']);
      externalMap.on('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      externalMap.on('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      externalMap.on('click', handleMapClick);
      externalMap.on('move', updateFloatingControlPosition);
      externalMap.on('zoom', updateFloatingControlPosition);
      externalMap.on('move', updateTooltipPosition);
      externalMap.on('zoom', updateTooltipPosition);
      externalMap.on('resize', updateFloatingControlPosition);
      externalMap.on('resize', updateTooltipPosition);
    };

    if (externalMap.isStyleLoaded()) {
      initializeExternalLayers();
    } else {
      externalMap.once('load', initializeExternalLayers);
      externalMap.on('styledata', initializeExternalLayers);
    }

    return () => {
      externalMap.off('load', initializeExternalLayers);
      externalMap.off('styledata', initializeExternalLayers);
      externalMap.off('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      externalMap.off('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      externalMap.off('click', handleMapClick);
      externalMap.off('move', updateFloatingControlPosition);
      externalMap.off('zoom', updateFloatingControlPosition);
      externalMap.off('move', updateTooltipPosition);
      externalMap.off('zoom', updateTooltipPosition);
      externalMap.off('resize', updateFloatingControlPosition);
      externalMap.off('resize', updateTooltipPosition);
      externalMap.getCanvas().classList.remove(styles.drawingCursor);
      setGeoJsonSourceData(externalMap, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, buildAreaFeatureCollection());
      setGeoJsonSourceData(externalMap, AREA_EDIT_DRAFT_SOURCE_ID, buildAreaFeatureCollection());
      setGeoJsonSourceData(externalMap, AREA_EDIT_MARKER_SOURCE_ID, buildAreaFeatureCollection());
      setGeoJsonSourceData(externalMap, AREA_EDIT_MOVEMENT_PATH_SOURCE_ID, buildAreaFeatureCollection());
      if (externalMap.getLayer(AREA_EDIT_SELECTED_FILL_LAYER_ID)) {
        externalMap.setFilter(AREA_EDIT_SELECTED_FILL_LAYER_ID, ['==', ['get', 'entityId'], '']);
      }
      mapRef.current = null;
      onMapReadyRef.current?.(null);
      onBoundsReadyRef.current?.(null);
    };
  }, [externalMap, handleAreaClick, handleMapClick, updateFloatingControlPosition, updateTooltipPosition]);

  useEffect(() => {
    if (externalMap) return;
    if (!mapContainerRef.current) return;

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
    onMapReadyRef.current?.(map);

    map.once('load', () => {
      addSearchAreaLayers(map, buildAreaFeatureCollection());
      addDrawingLayers(map);
      addMovementPathLayers(
        map,
        buildMovementPathFeatureCollection(movementPathsRef.current, activeOperationalPeriodIdRef.current),
      );
      addMarkerLayers(map, buildMarkerFeatureCollection(mapMarkersRef.current));
      setGeoJsonSourceData(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, buildCompletedDraftFeatureCollection(completedDraftsRef.current));
      setGeoJsonSourceData(map, AREA_EDIT_DRAFT_SOURCE_ID, buildDraftFeatureCollection(draftPointsRef.current, selectedAreaColorTokenRef.current));
      map.setFilter(AREA_EDIT_SELECTED_FILL_LAYER_ID, ['==', ['get', 'entityId'], selectedAreaIdRef.current ?? '']);
      map.on('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      map.on('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      map.on('click', handleMapClick);
      map.on('move', updateFloatingControlPosition);
      map.on('zoom', updateFloatingControlPosition);
      map.on('move', updateTooltipPosition);
      map.on('zoom', updateTooltipPosition);
      map.on('resize', updateFloatingControlPosition);
      map.on('resize', updateTooltipPosition);

      void loadGwangsanManifest()
        .then((manifest) => {
          addMudeungsanHikingTrailLayersSafely(map);
          raiseMovementPathLayers(map);
          raiseMarkerLayers(map);

          const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
          const overallBounds = findOverallDraftBounds(completedDraftsRef.current);
          // TODO(area-edit): 사건 상세 계약에 좌표 필드가 생기면 active OVERALL bounds와 광산 fallback 사이에서
          // 사건 좌표를 초기 지도 기준으로 사용한다. 현재 사건 상세에는 lastSeenLocationText 문자열만 있어
          // 지도 중심을 계산하면 문서 계약 밖의 추정 로직이 된다.
          const bounds = overallBounds ?? (boundaryLayer ? toBounds(boundaryLayer.bbox) : null);

          if (bounds) {
            map.fitBounds(bounds, { padding: DEFAULT_FIT_PADDING, duration: 0, maxZoom: 15 });
            if (overallBounds) {
              const overallDraft = completedDraftsRef.current.find((draft) => draft.kind === 'overall');
              fittedOverallAreaIdRef.current = overallDraft?.areaId ?? null;
            }
          } else {
            map.setCenter(DEFAULT_JURISDICTION_CENTER);
            map.setZoom(INITIAL_MAP_FALLBACK_ZOOM);
          }

          onBoundsReadyRef.current?.(bounds);
        })
        .catch((error: unknown) => {
          console.error('[AreaEditMap] 지도 초기화 오류', error);
          onBoundsReadyRef.current?.(null);
        });
    });

    return () => {
      map.off('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      map.off('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      map.off('click', handleMapClick);
      map.off('move', updateFloatingControlPosition);
      map.off('zoom', updateFloatingControlPosition);
      map.off('move', updateTooltipPosition);
      map.off('zoom', updateTooltipPosition);
      map.off('resize', updateFloatingControlPosition);
      map.off('resize', updateTooltipPosition);
      mapRef.current = null;
      onMapReadyRef.current?.(null);
      onBoundsReadyRef.current?.(null);
      map.remove();
    };
  }, [externalMap, handleAreaClick, handleMapClick, updateFloatingControlPosition, updateTooltipPosition]);

  const selectedTooltipDraft = normalSelectedAreaId ? completedDrafts.find((draft) => draft.areaId === normalSelectedAreaId) : null;

  return (
    <div className={`${styles.surface}${hideCanvas ? ` ${styles.externalSurface}` : ''}`} aria-label="구역 편집 지도">
      {hideCanvas ? null : <div ref={mapContainerRef} className={styles.canvas} />}
      {tooltipPosition && selectedTooltipDraft ? (
        <div
          className={styles.areaTooltip}
          style={{
            left: tooltipPosition.x,
            top: tooltipPosition.y,
          }}
        >
          <button
            type="button"
            className={styles.areaTooltipDeleteButton}
            onClick={(event) => {
              event.stopPropagation();
              onRequestAreaDelete(selectedTooltipDraft.areaId);
            }}
          >
            삭제
          </button>
          <strong>{selectedTooltipDraft.label}</strong>
          <span>{selectedTooltipDraft.kind.toUpperCase()} 담당 구역</span>
        </div>
      ) : null}
      {floatingControlPosition ? (
        <button
          type="button"
          className={`${styles.vertexControl} ${canCompleteDraft ? styles.completeControl : styles.undoControl}`}
          style={{
            left: floatingControlPosition.x,
            top: floatingControlPosition.y,
          }}
          aria-label={canCompleteDraft ? '구역 완료' : '최근 꼭짓점 되돌리기'}
          title={canCompleteDraft ? '완료' : '되돌리기'}
          onClick={canCompleteDraft ? onConfirmDraft : onUndoDraft}
        >
          {canCompleteDraft ? '✓' : '↶'}
        </button>
      ) : null}
    </div>
  );
}

