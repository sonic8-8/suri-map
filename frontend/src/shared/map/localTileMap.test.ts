import { afterEach, describe, expect, test, vi } from 'vitest';
import { getLocalTileStyleUrl, transformLocalTileRequest } from './localTileMap';

describe('local tile map contract', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    localStorage.clear();
    sessionStorage.clear();
  });

  test('uses the S7 local style URL by default', () => {
    vi.stubEnv('VITE_TILE_BASE_URL', undefined);

    expect(getLocalTileStyleUrl()).toBe('/map-style/osm-local.json');
  });

  test('does not expose direct tileserver origins to MapLibre', () => {
    vi.stubEnv('VITE_TILE_BASE_URL', 'http://localhost:8082');

    expect(getLocalTileStyleUrl()).toBe('/map-style/osm-local.json');
  });

  test('adds WEB headers to allowed local tile resources', () => {
    localStorage.setItem('accessToken', 'local-tile-token');

    expect(transformLocalTileRequest('/map-style/osm-local.json', 'Style')).toMatchObject({
      url: '/map-style/osm-local.json',
      headers: {
        Authorization: 'Bearer local-tile-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformLocalTileRequest('/tiles/styles/osm-local.json', 'Style')).toMatchObject({
      url: '/map-style/osm-local.json',
      headers: {
        Authorization: 'Bearer local-tile-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformLocalTileRequest('/tiles/osm-local/15/27935/12960.pbf', 'Tile')).toMatchObject({
      url: '/tiles/osm-local/15/27935/12960.pbf',
      headers: {
        Authorization: 'Bearer local-tile-token',
        'X-Client-Channel': 'WEB',
      },
    });
  });

  test('uses the local API access token fallback for tile requests', () => {
    vi.stubEnv('VITE_API_ACCESS_TOKEN', 'env-local-token');

    expect(transformLocalTileRequest('/map-style/osm-local.json', 'Style')).toMatchObject({
      headers: {
        Authorization: 'Bearer env-local-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformLocalTileRequest('/tiles/styles/osm-local.json', 'Style')).toMatchObject({
      url: '/map-style/osm-local.json',
      headers: {
        Authorization: 'Bearer env-local-token',
        'X-Client-Channel': 'WEB',
      },
    });
  });

  test('rejects VWorld and other external tile hosts', () => {
    expect(() =>
      transformLocalTileRequest('https://api.vworld.kr/req/wmts/1.0.0/key/Base/15/12960/27935.png', 'Tile'),
    ).toThrow(/external tile host/i);
    expect(() => transformLocalTileRequest('https://example.com/tiles/osm-local/15/1/1.pbf', 'Tile')).toThrow(
      /external tile host/i,
    );
  });
});
