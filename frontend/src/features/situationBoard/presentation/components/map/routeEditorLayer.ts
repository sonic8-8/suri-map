import maplibregl, { type GeoJSONSource, type LayerSpecification } from 'maplibre-gl';

import type { Position } from './searchMapCanvasData';

const ENABLE_LOCAL_ROUTE_EDITOR = false;
const ROUTE_EDITOR_SOURCE_ID = 'dev-route-editor-draft';
const ROUTE_EDITOR_LINE_LAYER_ID = 'dev-route-editor-draft-line';
const ROUTE_EDITOR_POINT_LAYER_ID = 'dev-route-editor-draft-point';
const ROUTE_EDITOR_ANCHOR_POINT_LAYER_ID = 'dev-route-editor-draft-anchor-point';

type LineStringGeometry = { type: 'LineString'; coordinates: Position[] };
type PointGeometry = { type: 'Point'; coordinates: Position };
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
        properties: { slot: 'dev_route_editor'; geometryType: 'point'; index: string; pointKind: 'generated' | 'anchor' };
        geometry: PointGeometry;
      }
  >;
};

function addRouteEditorGeoJsonSource(map: maplibregl.Map, data: RouteEditorFeatureCollection) {
  if (map.getSource(ROUTE_EDITOR_SOURCE_ID)) {
    return;
  }

  map.addSource(ROUTE_EDITOR_SOURCE_ID, {
    type: 'geojson',
    data,
  });
}

function addRouteEditorLayer(map: maplibregl.Map, layer: LayerSpecification) {
  if (map.getLayer(layer.id)) {
    return;
  }

  map.addLayer(layer);
}

export function getIsRouteEditorEnabled(): boolean {
  if (ENABLE_LOCAL_ROUTE_EDITOR) {
    return true;
  }

  if (typeof window === 'undefined') {
    return false;
  }

  return new URLSearchParams(window.location.search).get('routeEditor') === '1';
}

function createRouteEditorGeoJson(
  coordinates: Position[],
  anchorCoordinates: Position[] = coordinates,
): RouteEditorFeatureCollection {
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
        properties: {
          slot: 'dev_route_editor' as const,
          geometryType: 'point' as const,
          pointKind: 'generated' as const,
          index: String(index + 1),
        },
        geometry: { type: 'Point' as const, coordinates: coordinate },
      })),
      ...anchorCoordinates.map((coordinate, index) => ({
        type: 'Feature' as const,
        properties: {
          slot: 'dev_route_editor' as const,
          geometryType: 'point' as const,
          pointKind: 'anchor' as const,
          index: String(index + 1),
        },
        geometry: { type: 'Point' as const, coordinates: coordinate },
      })),
    ],
  };
}

export function addRouteEditorLayers(map: maplibregl.Map) {
  addRouteEditorGeoJsonSource(map, createRouteEditorGeoJson([]));

  addRouteEditorLayer(map, {
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

  addRouteEditorLayer(map, {
    id: ROUTE_EDITOR_POINT_LAYER_ID,
    type: 'circle',
    source: ROUTE_EDITOR_SOURCE_ID,
    filter: ['all', ['==', ['get', 'geometryType'], 'point'], ['==', ['get', 'pointKind'], 'generated']],
    paint: {
      'circle-color': '#0b7285',
      'circle-radius': 3,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 1,
    },
  });

  addRouteEditorLayer(map, {
    id: ROUTE_EDITOR_ANCHOR_POINT_LAYER_ID,
    type: 'circle',
    source: ROUTE_EDITOR_SOURCE_ID,
    filter: ['all', ['==', ['get', 'geometryType'], 'point'], ['==', ['get', 'pointKind'], 'anchor']],
    paint: {
      'circle-color': '#f97316',
      'circle-radius': 6,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
    },
  });
}

export function syncRouteEditorDraft(map: maplibregl.Map, coordinates: Position[], anchorCoordinates?: Position[]) {
  const source = map.getSource(ROUTE_EDITOR_SOURCE_ID);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(createRouteEditorGeoJson(coordinates, anchorCoordinates));
}
