import { useEffect, useRef, useState } from 'react';
import maplibregl, {
  type GeoJSONSource,
  type LayerSpecification,
  type LngLatBoundsLike,
  type StyleSpecification,
} from 'maplibre-gl';
import { getVWorldApiKey } from '../../../../../shared/config';
import { areaColorTokens } from '../../constants/areaColorTokens';
import { searchAreaResponses } from '../../constants/mockSituationBoard';
import styles from './SearchMapCanvas.module.css';

const GWANGSAN_CENTER: [number, number] = [126.7525, 35.1598];
const GWANGSAN_MANIFEST_URL = '/map-data/gwangsan/manifest.json';
const MUDEUNGSAN_HIKING_TRAILS_URL = '/map-data/mudeungsan/trails.geojson';
const MUDEUNGSAN_OSM_TRAILS_URL = '/map-data/mudeungsan/osm-trails.geojson';
const MUDEUNGSAN_OSM_PEAKS_URL = '/map-data/mudeungsan/osm-peaks.geojson';
const V_WORLD_TILE_SIZE = 256;
const V_WORLD_MAX_ZOOM = 19;
const V_WORLD_BASE_OPACITY = 1;
const DEFAULT_FIT_PADDING = 44;
const ENABLE_LOCAL_ROUTE_EDITOR = false;
const ROUTE_EDITOR_SOURCE_ID = 'dev-route-editor-draft';
const ROUTE_EDITOR_LINE_LAYER_ID = 'dev-route-editor-draft-line';
const ROUTE_EDITOR_POINT_LAYER_ID = 'dev-route-editor-draft-point';

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
  if (!map.getLayer('vworld-base-raster')) {
    return;
  }
  map.setPaintProperty('vworld-base-raster', 'raster-opacity', V_WORLD_BASE_OPACITY);
}

function syncBaseMapOpacity(map: maplibregl.Map) {
  applyBaseRasterOpacity(map);
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
  onInitialBoundsReady?: (bounds: LngLatBoundsLike | null) => void;
  onMapReady?: (map: maplibregl.Map | null) => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SearchMapCanvas({
  onInitialBoundsReady,
  onMapReady,
}: SearchMapCanvasProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const isRouteEditorEnabledRef = useRef(getIsRouteEditorEnabled());
  const [routeEditorCoordinates, setRouteEditorCoordinates] = useState<Position[]>([]);
  const isRouteEditorEnabled = isRouteEditorEnabledRef.current;

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
      center: GWANGSAN_CENTER,
      zoom: 13,
      maxZoom: V_WORLD_MAX_ZOOM,
      attributionControl: false,
    });

    mapRef.current = map;
    onMapReady?.(map);

    const handleMapClick = (event: maplibregl.MapMouseEvent) => {
      if (!isRouteEditorEnabledRef.current) {
        return;
      }

      const routeEditorCoordinate: Position = [event.lngLat.lng, event.lngLat.lat];
      setRouteEditorCoordinates((currentCoordinates) => [...currentCoordinates, routeEditorCoordinate]);

    };

    map.on('click', handleMapClick);

    map.once('load', () => {
      syncBaseMapOpacity(map);

      void loadGwangsanManifest()
        .then((manifest) => {
          addMudeungsanHikingTrailLayersSafely(map);
          if (isRouteEditorEnabledRef.current) {
            addRouteEditorLayers(map);
          }

          const overallSearchAreaBounds = getFeatureCollectionBounds(selectedIncidentOverallSearchArea);
          const boundaryLayer = manifest.layers.find((layer) => layer.layerId === 'boundary');
          const initialBounds = overallSearchAreaBounds ?? (boundaryLayer ? toBounds(boundaryLayer.bbox) : null);
          onInitialBoundsReady?.(initialBounds);
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
      mapRef.current = null;
      onInitialBoundsReady?.(null);
      onMapReady?.(null);
      map.remove();
    };
  }, [onInitialBoundsReady, onMapReady]);

  return (
    <div className={styles.surface} aria-label="Search map">
      <div ref={mapContainerRef} className={styles.canvas} />
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
