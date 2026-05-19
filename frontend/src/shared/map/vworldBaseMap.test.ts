import type { StyleSpecification } from 'maplibre-gl';
import { describe, expect, test } from 'vitest';

import { createVWorldBaseStyle, V_WORLD_BASE_LAYER_ID } from './vworldBaseMap';

describe('createVWorldBaseStyle', () => {
  test('falls back to the local tile style when the VWorld key is missing', () => {
    expect(createVWorldBaseStyle('')).toBe('/map-style/osm-local.json');
  });

  test('builds the VWorld raster style when the API key is present', () => {
    const style = createVWorldBaseStyle('vworld-api-key') as StyleSpecification;

    expect(style.layers?.[0]).toMatchObject({
      id: V_WORLD_BASE_LAYER_ID,
      type: 'raster',
    });
    expect(style.sources).toMatchObject({
      'vworld-base-raster': expect.objectContaining({
        tiles: ['https://api.vworld.kr/req/wmts/1.0.0/vworld-api-key/Base/{z}/{y}/{x}.png'],
      }),
    });
  });
});
