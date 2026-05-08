import { useCallback, useEffect, useRef, useState } from 'react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
  type StyleSpecification,
} from 'maplibre-gl';

import { getVWorldApiKey } from '../../../../shared/config';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import type { AreaEditPosition, AreaNodeKind, CompletedAreaDraft } from '../constants/mockAreaEdit';
import styles from './AreaEditMapCanvas.module.css';

const DEFAULT_JURISDICTION_CENTER: AreaEditPosition = [126.7525, 35.1598];
const GWANGSAN_MANIFEST_URL = '/map-data/gwangsan/manifest.json';
const MUDEUNGSAN_HIKING_TRAILS_URL = '/map-data/mudeungsan/trails.geojson';
const MUDEUNGSAN_OSM_TRAILS_URL = '/map-data/mudeungsan/osm-trails.geojson';
const MUDEUNGSAN_OSM_PEAKS_URL = '/map-data/mudeungsan/osm-peaks.geojson';
const V_WORLD_TILE_SIZE = 256;
const V_WORLD_MAX_ZOOM = 19;
const V_WORLD_BASE_OPACITY = 1;
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

function createVWorldBaseStyle(apiKey: string): StyleSpecification {
  return {
    version: 8,
    glyphs: 'https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf',
    sources: {
      'vworld-base-raster': {
        type: 'raster',
        tiles: [`https://api.vworld.kr/req/wmts/1.0.0/${apiKey}/Base/{z}/{y}/{x}.png`],
        tileSize: V_WORLD_TILE_SIZE,
        maxzoom: V_WORLD_MAX_ZOOM,
        attribution: 'VWorld',
      },
    },
    layers: [
      {
        id: 'vworld-base-raster',
        type: 'raster',
        source: 'vworld-base-raster',
        paint: { 'raster-opacity': V_WORLD_BASE_OPACITY },
      },
    ],
  };
}

function toBounds(bbox: [number, number, number, number]): LngLatBoundsLike {
  return [
    [bbox[0], bbox[1]],
    [bbox[2], bbox[3]],
  ];
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
    console.error('[AreaEditMap] 臾대벑???몃젅???덉씠??異붽? ?ㅽ뙣', error);
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

  return {
    type: 'Feature',
    properties: {
      entityId: draft.areaId,
      areaLevel: draft.kind.toUpperCase(),
      status: 'DRAFT_COMPLETED',
      fillColor: visualStyle.fillColor,
      lineColor: visualStyle.lineColor,
      fillOpacity: String(Math.max(visualStyle.fillOpacity, 0.18)),
      lineWidth: String(draft.kind === 'overall' ? 3.2 : draft.kind === 'unit' ? 2.8 : 2.4),
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

function addDrawingLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, { type: 'FeatureCollection', features: [] });
  addGeoJsonSource(map, AREA_EDIT_DRAFT_SOURCE_ID, { type: 'FeatureCollection', features: [] });

  addLayer(map, {
    id: AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID,
    type: 'fill',
    source: AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID,
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': ['to-number', ['get', 'fillOpacity']],
    },
  });
  addLayer(map, {
    id: AREA_EDIT_COMPLETED_DRAFT_LINE_LAYER_ID,
    type: 'line',
    source: AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID,
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
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
    return '援ъ뿭? ?쒕줈 ?ㅻⅨ 瑗?쭞??3媛??댁긽?쇰줈 ?レ븘???⑸땲??';
  }

  if (!areSamePoint(closedRing[0], closedRing[closedRing.length - 1])) {
    return '援ъ뿭???꾨즺?섎젮硫?留덉?留??먯씠 ?쒖옉?먭낵 媛숈븘???⑸땲??';
  }

  if (hasSelfIntersection(closedRing)) {
    return '援ъ뿭 寃쎄퀎?좎씠 ?쒕줈 援먯감?⑸땲?? 援먯감?섏? ?딅뒗 ?섎굹???ロ엺 援ъ뿭?쇰줈 ?ㅼ떆 吏?뺥븯??떆??';
  }

  return null;
}

export type AreaEditMapCanvasProps = {
  canCompleteDraft: boolean;
  completedDrafts: CompletedAreaDraft[];
  draftPoints: AreaEditPosition[];
  isDrawing: boolean;
  onBoundsReady?: (bounds: LngLatBoundsLike | null) => void;
  onCloseDraft: (coordinates: AreaEditPosition[]) => void;
  onConfirmDraft: () => void;
  onDraftPointAdd: (position: AreaEditPosition) => void;
  onMapReady?: (map: maplibregl.Map | null) => void;
  selectedAreaColorToken: AreaColorToken | null;
  selectedAreaId: string | null;
  onSelectArea: (areaId: string) => void;
  onUndoDraft: () => void;
  onValidationMessage: (message: string) => void;
};

export function AreaEditMapCanvas({
  canCompleteDraft,
  completedDrafts,
  draftPoints,
  isDrawing,
  onBoundsReady,
  onCloseDraft,
  onConfirmDraft,
  onDraftPointAdd,
  onMapReady,
  selectedAreaColorToken,
  selectedAreaId,
  onSelectArea,
  onUndoDraft,
  onValidationMessage,
}: AreaEditMapCanvasProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [floatingControlPosition, setFloatingControlPosition] = useState<{ x: number; y: number } | null>(null);
  const completedDraftsRef = useRef(completedDrafts);
  const draftPointsRef = useRef(draftPoints);
  const isDrawingRef = useRef(isDrawing);
  const selectedAreaColorTokenRef = useRef(selectedAreaColorToken);
  const selectedAreaIdRef = useRef(selectedAreaId);

  const onMapReadyRef = useRef(onMapReady);
  const onBoundsReadyRef = useRef(onBoundsReady);
  const onSelectAreaRef = useRef(onSelectArea);
  const onDraftPointAddRef = useRef(onDraftPointAdd);
  const onCloseDraftRef = useRef(onCloseDraft);
  const onValidationMessageRef = useRef(onValidationMessage);

  useEffect(() => { onMapReadyRef.current = onMapReady; }, [onMapReady]);
  useEffect(() => { onBoundsReadyRef.current = onBoundsReady; }, [onBoundsReady]);
  useEffect(() => { onSelectAreaRef.current = onSelectArea; }, [onSelectArea]);
  useEffect(() => { onDraftPointAddRef.current = onDraftPointAdd; }, [onDraftPointAdd]);
  useEffect(() => { onCloseDraftRef.current = onCloseDraft; }, [onCloseDraft]);
  useEffect(() => { onValidationMessageRef.current = onValidationMessage; }, [onValidationMessage]);

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

  useEffect(() => {
    completedDraftsRef.current = completedDrafts;
    const map = mapRef.current;
    if (!map) return;
    setGeoJsonSourceData(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, buildCompletedDraftFeatureCollection(completedDrafts));
  }, [completedDrafts]);

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
  }, [isDrawing, updateFloatingControlPosition]);

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
    if (!isDrawingRef.current) return;

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
      setGeoJsonSourceData(map, AREA_EDIT_COMPLETED_DRAFT_SOURCE_ID, buildCompletedDraftFeatureCollection(completedDraftsRef.current));
      setGeoJsonSourceData(map, AREA_EDIT_DRAFT_SOURCE_ID, buildDraftFeatureCollection(draftPointsRef.current, selectedAreaColorTokenRef.current));
      map.setFilter(AREA_EDIT_SELECTED_FILL_LAYER_ID, ['==', ['get', 'entityId'], selectedAreaIdRef.current ?? '']);
      map.on('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      map.on('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      map.on('click', AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID, handleAreaClick);
      map.on('click', handleMapClick);
      map.on('move', updateFloatingControlPosition);
      map.on('zoom', updateFloatingControlPosition);
      map.on('resize', updateFloatingControlPosition);

      void loadGwangsanManifest()
        .then((manifest) => {
          addMudeungsanHikingTrailLayersSafely(map);

          const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
          const bounds = boundaryLayer ? toBounds(boundaryLayer.bbox) : null;

          if (bounds) {
            map.fitBounds(bounds, { padding: DEFAULT_FIT_PADDING, duration: 0, maxZoom: 15 });
          } else {
            map.setCenter(DEFAULT_JURISDICTION_CENTER);
            map.setZoom(INITIAL_MAP_FALLBACK_ZOOM);
          }

          onBoundsReadyRef.current?.(bounds);
        })
        .catch((error: unknown) => {
          console.error('[AreaEditMap] 吏??珥덇린???ㅻ쪟', error);
          onBoundsReadyRef.current?.(null);
        });
    });

    return () => {
      map.off('click', AREA_EDIT_FILL_LAYER_ID, handleAreaClick);
      map.off('click', AREA_EDIT_LINE_LAYER_ID, handleAreaClick);
      map.off('click', AREA_EDIT_COMPLETED_DRAFT_FILL_LAYER_ID, handleAreaClick);
      map.off('click', handleMapClick);
      map.off('move', updateFloatingControlPosition);
      map.off('zoom', updateFloatingControlPosition);
      map.off('resize', updateFloatingControlPosition);
      mapRef.current = null;
      onMapReadyRef.current?.(null);
      onBoundsReadyRef.current?.(null);
      map.remove();
    };
  }, [handleAreaClick, handleMapClick, updateFloatingControlPosition]);

  return (
    <div className={styles.surface} aria-label="구역 편집 지도">
      <div ref={mapContainerRef} className={styles.canvas} />
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
