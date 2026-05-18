import type { StyleSpecification } from 'maplibre-gl';

export const V_WORLD_BASE_LAYER_ID = 'vworld-base-raster';
export const V_WORLD_MAX_ZOOM = 19;
export const V_WORLD_BASE_OPACITY = 1;

const V_WORLD_TILE_SIZE = 256;
const V_WORLD_BASE_SOURCE_ID = 'vworld-base-raster';
const LOCAL_TILE_STYLE_URL = '/map-style/osm-local.json';

export function createVWorldBaseStyle(apiKey: string): StyleSpecification | string {
  if (!apiKey.trim()) {
    return LOCAL_TILE_STYLE_URL;
  }

  return {
    version: 8,
    glyphs: 'https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf',
    sources: {
      [V_WORLD_BASE_SOURCE_ID]: {
        type: 'raster',
        tiles: [`https://api.vworld.kr/req/wmts/1.0.0/${apiKey}/Base/{z}/{y}/{x}.png`],
        tileSize: V_WORLD_TILE_SIZE,
        maxzoom: V_WORLD_MAX_ZOOM,
        attribution: 'VWorld',
      },
    },
    layers: [
      {
        id: V_WORLD_BASE_LAYER_ID,
        type: 'raster',
        source: V_WORLD_BASE_SOURCE_ID,
        paint: {
          'raster-opacity': V_WORLD_BASE_OPACITY,
        },
      },
    ],
  };
}
