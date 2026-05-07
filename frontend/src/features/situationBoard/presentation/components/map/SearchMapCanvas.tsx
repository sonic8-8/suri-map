import { useEffect, useRef, useState } from 'react';
import maplibregl, { type ExpressionSpecification, type LayerSpecification, type LngLatBoundsLike, type StyleSpecification } from 'maplibre-gl';
import { getVWorldApiKey } from '../../../../../shared/config';
import { areaColorTokens } from '../../constants/areaColorTokens';
import { searchAreaResponses } from '../../constants/mockSituationBoard';
import styles from './SearchMapCanvas.module.css';

const GWANGSAN_CENTER: [number, number] = [126.7525, 35.1598];
const GWANGSAN_MANIFEST_URL = '/map-data/gwangsan/manifest.json';
const V_WORLD_TILE_SIZE = 256;
const V_WORLD_MAX_ZOOM = 19;
const V_WORLD_BASE_OPACITY = 1;
const DEFAULT_FIT_PADDING = 44;
const COORDINATE_DISPLAY_MS = 4000;
const NO_SELECTED_SEARCH_AREA_ID = '__none__';
const COMPLETED_SEARCH_AREA_HATCH_IMAGE_ID = 'completed-search-area-hatch';
const SEARCH_AREA_FILL_LAYER_IDS = ['operational-overall_search_area-fill', 'operational-area-fill'] as const;
const COMPLETED_SEARCH_AREA_FILL_LAYER_IDS = [
  'operational-overall_search_area-completed-hatch',
  'operational-area-completed-hatch',
] as const;
const SEARCH_AREA_INTERACTIVE_LAYER_IDS = [
  'operational-overall_search_area-fill',
  'operational-overall_search_area-line',
  'operational-area-fill',
  'operational-area-line',
] as const;

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
type PolygonGeometry = { type: 'Polygon'; coordinates: Position[][] };
type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
type OperationalGeometry = PolygonGeometry | LineStringGeometry | PointGeometry;
type OperationalFeature = {
  type: 'Feature';
  properties: Record<string, string>;
  geometry: OperationalGeometry;
};
type OperationalFeatureCollection = {
  type: 'FeatureCollection';
  features: OperationalFeature[];
};
type ClickedCoordinate = {
  longitude: number;
  latitude: number;
};

const operationalPaths: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: { slot: 'path', mode: 'walk' },
      geometry: {
        type: 'LineString',
        coordinates: [
          [126.7785, 35.132],
          [126.7836, 35.1361],
          [126.7904, 35.139],
          [126.7989, 35.1436],
        ],
      },
    },
  ],
};

function toPolygonFeature(area: (typeof searchAreaResponses)[number]): OperationalFeature {
  const visualStyle = areaColorTokens[area.colorToken];
  const lineWidth = area.areaLevel === 'OVERALL' ? 3 : area.areaLevel === 'UNIT' ? 2.6 : 2.2;
  const lineOpacity = area.status === 'COMPLETED' ? 0.62 : 0.96;
  const optionalProperties = {
    ...('opId' in area ? { opId: area.opId } : {}),
    ...('parentSearchAreaId' in area ? { parentSearchAreaId: area.parentSearchAreaId } : {}),
  };

  return {
    type: 'Feature',
    properties: {
      slot: area.areaLevel === 'OVERALL' ? 'overall_search_area' : 'area',
      entityId: area.id,
      areaLevel: area.areaLevel,
      status: area.status,
      incidentId: area.incidentId,
      version: String(area.version),
      fillColor: visualStyle.fillColor,
      lineColor: visualStyle.lineColor,
      fillOpacity: String(visualStyle.fillOpacity),
      lineWidth: String(lineWidth),
      lineOpacity: String(lineOpacity),
      ...optionalProperties,
      ...(area.historyCount ? { historyCount: String(area.historyCount) } : {}),
    },
    geometry: {
      type: 'Polygon',
      coordinates: area.geometry.coordinates.map((ring) => ring.map((coordinate) => [coordinate[0], coordinate[1]])),
    },
  };
}

const selectedIncidentOverallSearchArea: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: searchAreaResponses.filter((area) => area.areaLevel === 'OVERALL').map(toPolygonFeature),
};

const operationalSearchAreas: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: searchAreaResponses.filter((area) => area.areaLevel !== 'OVERALL').map(toPolygonFeature),
};

const operationalMarkers: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: { slot: 'marker', markerType: 'clue' },
      geometry: { type: 'Point', coordinates: [126.7904, 35.139] },
    },
    {
      type: 'Feature',
      properties: { slot: 'marker', markerType: 'found' },
      geometry: { type: 'Point', coordinates: [126.801, 35.145] },
    },
  ],
};

const operationalPolicePhones: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        slot: 'police_phone',
        entityId: 'unit-1-team-a',
        freshness: 'fresh',
        colorToken: 'phoneTeamA',
        identityColor: areaColorTokens.phoneTeamA.lineColor,
      },
      geometry: { type: 'Point', coordinates: [126.7836, 35.1361] },
    },
    {
      type: 'Feature',
      properties: {
        slot: 'police_phone',
        entityId: 'unit-2-team-c',
        freshness: 'stale',
        colorToken: 'phoneTeamG',
        identityColor: areaColorTokens.phoneTeamG.lineColor,
      },
      geometry: { type: 'Point', coordinates: [126.7989, 35.1436] },
    },
  ],
};

function createVWorldBaseStyle(apiKey: string): StyleSpecification {
  return {
    version: 8,
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
        paint: {
          'raster-opacity': V_WORLD_BASE_OPACITY,
        },
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

function addGeoJsonSource(map: maplibregl.Map, sourceId: string, data: string | OperationalFeatureCollection) {
  if (map.getSource(sourceId)) {
    return;
  }
  map.addSource(sourceId, {
    type: 'geojson',
    data,
  });
}

function addLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) {
    return;
  }
  map.addLayer(layer);
}

function applyBaseRasterOpacity(map: maplibregl.Map) {
  if (!map.getLayer('vworld-base-raster')) {
    return;
  }
  map.setPaintProperty('vworld-base-raster', 'raster-opacity', V_WORLD_BASE_OPACITY);
}

function createSearchAreaFillOpacityExpression(selectedSearchAreaId: string): ExpressionSpecification {
  return [
    'case',
    ['==', ['get', 'entityId'], selectedSearchAreaId],
    ['to-number', ['get', 'fillOpacity']],
    0,
  ];
}

function createCompletedSearchAreaHatchOpacityExpression(selectedSearchAreaId: string): ExpressionSpecification {
  return [
    'case',
    ['all', ['==', ['get', 'status'], 'COMPLETED'], ['!=', ['get', 'entityId'], selectedSearchAreaId]],
    1,
    0,
  ];
}

function createCompletedSearchAreaHatchImage(): ImageData {
  const canvas = document.createElement('canvas');
  canvas.width = 10;
  canvas.height = 10;

  const context = canvas.getContext('2d');
  if (!context) {
    return new ImageData(canvas.width, canvas.height);
  }

  context.clearRect(0, 0, canvas.width, canvas.height);
  context.strokeStyle = 'rgba(40, 58, 78, 0.3)';
  context.lineWidth = 0.5;
  context.lineCap = 'butt';
  context.beginPath();
  context.moveTo(0, 10);
  context.lineTo(10, 0);
  context.stroke();

  return context.getImageData(0, 0, canvas.width, canvas.height);
}

function addCompletedSearchAreaHatchImage(map: maplibregl.Map) {
  if (map.hasImage(COMPLETED_SEARCH_AREA_HATCH_IMAGE_ID)) {
    return;
  }

  map.addImage(COMPLETED_SEARCH_AREA_HATCH_IMAGE_ID, createCompletedSearchAreaHatchImage());
}

function applySelectedSearchAreaFill(map: maplibregl.Map, selectedSearchAreaId: string) {
  SEARCH_AREA_FILL_LAYER_IDS.forEach((layerId) => {
    if (!map.getLayer(layerId)) {
      return;
    }

    map.setPaintProperty(layerId, 'fill-opacity', createSearchAreaFillOpacityExpression(selectedSearchAreaId));
  });

  COMPLETED_SEARCH_AREA_FILL_LAYER_IDS.forEach((layerId) => {
    if (!map.getLayer(layerId)) {
      return;
    }

    map.setPaintProperty(layerId, 'fill-opacity', createCompletedSearchAreaHatchOpacityExpression(selectedSearchAreaId));
  });
}

function syncBaseMapOpacity(map: maplibregl.Map) {
  applyBaseRasterOpacity(map);
}

function addGwangsanBaseLayers(map: maplibregl.Map, manifest: GwangsanMapManifest) {
  const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
  if (!boundaryLayer) {
    return;
  }

  addGeoJsonSource(map, 'gwangsan-boundary', boundaryLayer.url);

  addLayer(map, {
    id: 'gwangsan-boundary-fill',
    type: 'fill',
    source: 'gwangsan-boundary',
    paint: { 'fill-color': '#eef7ed', 'fill-opacity': 0.34 },
  });
  addLayer(map, {
    id: 'gwangsan-boundary-line',
    type: 'line',
    source: 'gwangsan-boundary',
    paint: { 'line-color': '#2f6f4e', 'line-opacity': 0.82, 'line-width': 1.7 },
  });
}

function addOperationalLayers(map: maplibregl.Map) {
  addGeoJsonSource(map, 'operational-overall_search_area', selectedIncidentOverallSearchArea);
  addGeoJsonSource(map, 'operational-area', operationalSearchAreas);
  addGeoJsonSource(map, 'operational-path', operationalPaths);
  addGeoJsonSource(map, 'operational-marker', operationalMarkers);
  addGeoJsonSource(map, 'operational-police_phone', operationalPolicePhones);
  addCompletedSearchAreaHatchImage(map);

  addLayer(map, {
    id: 'operational-overall_search_area-completed-hatch',
    type: 'fill',
    source: 'operational-overall_search_area',
    paint: {
      'fill-pattern': COMPLETED_SEARCH_AREA_HATCH_IMAGE_ID,
      'fill-opacity': createCompletedSearchAreaHatchOpacityExpression(NO_SELECTED_SEARCH_AREA_ID),
    },
  });
  addLayer(map, {
    id: 'operational-overall_search_area-fill',
    type: 'fill',
    source: 'operational-overall_search_area',
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': createSearchAreaFillOpacityExpression(NO_SELECTED_SEARCH_AREA_ID),
    },
  });
  addLayer(map, {
    id: 'operational-overall_search_area-line',
    type: 'line',
    source: 'operational-overall_search_area',
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
    },
  });
  addLayer(map, {
    id: 'operational-area-completed-hatch',
    type: 'fill',
    source: 'operational-area',
    paint: {
      'fill-pattern': COMPLETED_SEARCH_AREA_HATCH_IMAGE_ID,
      'fill-opacity': createCompletedSearchAreaHatchOpacityExpression(NO_SELECTED_SEARCH_AREA_ID),
    },
  });
  addLayer(map, {
    id: 'operational-area-fill',
    type: 'fill',
    source: 'operational-area',
    paint: {
      'fill-color': ['get', 'fillColor'],
      'fill-opacity': createSearchAreaFillOpacityExpression(NO_SELECTED_SEARCH_AREA_ID),
    },
  });
  addLayer(map, {
    id: 'operational-area-line',
    type: 'line',
    source: 'operational-area',
    paint: {
      'line-color': ['get', 'lineColor'],
      'line-width': ['to-number', ['get', 'lineWidth']],
      'line-opacity': ['to-number', ['get', 'lineOpacity']],
      'line-dasharray': [2, 1],
    },
  });
  addLayer(map, {
    id: 'operational-path-line',
    type: 'line',
    source: 'operational-path',
    paint: { 'line-color': '#e15241', 'line-width': 3, 'line-opacity': 0.9 },
  });
  addLayer(map, {
    id: 'operational-police_phone-circle',
    type: 'circle',
    source: 'operational-police_phone',
    paint: {
      'circle-color': ['get', 'identityColor'],
      'circle-radius': 6,
      'circle-stroke-color': ['match', ['get', 'freshness'], 'stale', '#f59f00', '#ffffff'],
      'circle-stroke-width': 2,
    },
  });
  addLayer(map, {
    id: 'operational-marker-circle',
    type: 'circle',
    source: 'operational-marker',
    paint: {
      'circle-color': ['match', ['get', 'markerType'], 'found', '#d63384', '#6741d9'],
      'circle-radius': 7,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
    },
  });
}

async function loadGwangsanManifest(): Promise<GwangsanMapManifest> {
  const response = await fetch(GWANGSAN_MANIFEST_URL);
  if (!response.ok) {
    throw new Error(`Failed to load Gwangsan map manifest: ${response.status}`);
  }
  return (await response.json()) as GwangsanMapManifest;
}

function getSearchAreaEntityId(event: maplibregl.MapLayerMouseEvent): string | null {
  const entityId = event.features?.[0]?.properties?.entityId;
  return typeof entityId === 'string' ? entityId : null;
}

function getSearchAreaEntityIdAtPoint(map: maplibregl.Map, point: maplibregl.Point): string | null {
  const features = map.queryRenderedFeatures(point, { layers: [...SEARCH_AREA_INTERACTIVE_LAYER_IDS] });
  const feature = features.find((currentFeature) => typeof currentFeature.properties?.entityId === 'string');
  return typeof feature?.properties?.entityId === 'string' ? feature.properties.entityId : null;
}

function formatCoordinate(value: number): string {
  return value.toFixed(6);
}

type SearchMapCanvasProps = {
  onMapReady?: (map: maplibregl.Map | null) => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SearchMapCanvas({ onMapReady, selectedSearchAreaId, onSelectSearchArea }: SearchMapCanvasProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const coordinatePopupRef = useRef<maplibregl.Popup | null>(null);
  const coordinateHideTimerRef = useRef<number | null>(null);
  const [clickedCoordinate, setClickedCoordinate] = useState<ClickedCoordinate | null>(null);

  useEffect(() => {
    const nextSelectedSearchAreaId = selectedSearchAreaId ?? NO_SELECTED_SEARCH_AREA_ID;

    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }

    applySelectedSearchAreaFill(map, nextSelectedSearchAreaId);
  }, [selectedSearchAreaId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) {
      return;
    }
    syncBaseMapOpacity(map);
  });

  useEffect(() => {
    if (!mapContainerRef.current) {
      return;
    }

    const vWorldApiKey = getVWorldApiKey();

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: createVWorldBaseStyle(vWorldApiKey),
      center: GWANGSAN_CENTER,
      zoom: 13,
      maxZoom: V_WORLD_MAX_ZOOM,
      attributionControl: false,
    });

    map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');
    mapRef.current = map;
    onMapReady?.(map);

    const clearCoordinateHideTimer = () => {
      if (coordinateHideTimerRef.current === null) {
        return;
      }
      window.clearTimeout(coordinateHideTimerRef.current);
      coordinateHideTimerRef.current = null;
    };

    const handleMapClick = (event: maplibregl.MapMouseEvent) => {
      const coordinate = {
        longitude: event.lngLat.lng,
        latitude: event.lngLat.lat,
      };
      setClickedCoordinate(coordinate);

      coordinatePopupRef.current?.remove();
      coordinatePopupRef.current = new maplibregl.Popup({
        closeButton: false,
        closeOnClick: false,
        offset: 12,
      })
        .setLngLat(event.lngLat)
        .setHTML(
          `<div class="${styles.coordinatePopup}">위도 ${formatCoordinate(coordinate.latitude)}<br />경도 ${formatCoordinate(
            coordinate.longitude,
          )}</div>`,
        )
        .addTo(map);

      clearCoordinateHideTimer();
      coordinateHideTimerRef.current = window.setTimeout(() => {
        setClickedCoordinate(null);
        coordinatePopupRef.current?.remove();
        coordinatePopupRef.current = null;
        coordinateHideTimerRef.current = null;
      }, COORDINATE_DISPLAY_MS);
    };

    map.on('click', handleMapClick);

    map.once('load', () => {
      syncBaseMapOpacity(map);

      void loadGwangsanManifest()
        .then((manifest) => {
          addGwangsanBaseLayers(map, manifest);
          addOperationalLayers(map);
          applySelectedSearchAreaFill(map, selectedSearchAreaId ?? NO_SELECTED_SEARCH_AREA_ID);

          const handleSearchAreaClick = (event: maplibregl.MapMouseEvent) => {
            const entityId = getSearchAreaEntityIdAtPoint(map, event.point);
            if (!entityId) {
              return;
            }

            onSelectSearchArea(entityId);
          };

          map.on('click', handleSearchAreaClick);

          SEARCH_AREA_INTERACTIVE_LAYER_IDS.forEach((layerId) => {
            map.on('mouseenter', layerId, () => {
              map.getCanvas().style.cursor = 'pointer';
            });
            map.on('mouseleave', layerId, () => {
              map.getCanvas().style.cursor = '';
            });
          });

          const overallSearchAreaBounds = getFeatureCollectionBounds(selectedIncidentOverallSearchArea);
          const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
          const initialBounds = overallSearchAreaBounds ?? (boundaryLayer ? toBounds(boundaryLayer.bbox) : null);
          if (initialBounds) {
            map.fitBounds(initialBounds, { padding: DEFAULT_FIT_PADDING, duration: 0, maxZoom: 15 });
          }
        })
        .catch((error: unknown) => {
          console.error(error);
        });
    });

    return () => {
      map.off('click', handleMapClick);
      clearCoordinateHideTimer();
      coordinatePopupRef.current?.remove();
      coordinatePopupRef.current = null;
      mapRef.current = null;
      onMapReady?.(null);
      map.remove();
    };
  }, [onMapReady]);

  return (
    <div className={styles.surface} aria-label="수색 지도">
      <div ref={mapContainerRef} className={styles.canvas} />
      {clickedCoordinate ? (
        <div className={styles.coordinatePanel} aria-live="polite">
          <span>위도 {formatCoordinate(clickedCoordinate.latitude)}</span>
          <span>경도 {formatCoordinate(clickedCoordinate.longitude)}</span>
        </div>
      ) : null}
    </div>
  );
}
