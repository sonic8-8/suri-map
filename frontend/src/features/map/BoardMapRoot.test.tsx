import { act, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, test, vi, type Mock } from 'vitest';
import { useBoardDisplayStore } from '../board/model/boardDisplayStore';
import { BoardMapRoot } from './BoardMapRoot';

type MapLibreTransformRequest = (url: string, resourceType?: string) => unknown;

type MockMapOptions = {
  readonly style?: unknown;
  readonly attributionControl?: unknown;
  readonly transformRequest?: MapLibreTransformRequest;
};

type MockMapInstance = {
  readonly options: MockMapOptions;
  readonly addControl: Mock;
  readonly remove: Mock;
};

type MockControlInstance = {
  readonly type: 'navigation' | 'attribution';
  readonly options: unknown;
};

const maplibreMock = vi.hoisted(() => ({
  mapInstances: [] as MockMapInstance[],
  controls: [] as MockControlInstance[],
}));

vi.mock('maplibre-gl', () => {
  class MockMap implements MockMapInstance {
    readonly addControl = vi.fn();
    readonly remove = vi.fn();

    constructor(readonly options: MockMapOptions) {
      maplibreMock.mapInstances.push(this);
    }
  }

  class MockNavigationControl implements MockControlInstance {
    readonly type = 'navigation';

    constructor(readonly options: unknown) {
      maplibreMock.controls.push(this);
    }
  }

  class MockAttributionControl implements MockControlInstance {
    readonly type = 'attribution';

    constructor(readonly options: unknown) {
      maplibreMock.controls.push(this);
    }
  }

  return {
    default: {
      Map: MockMap,
      NavigationControl: MockNavigationControl,
      AttributionControl: MockAttributionControl,
    },
    Map: MockMap,
    NavigationControl: MockNavigationControl,
    AttributionControl: MockAttributionControl,
  };
});

describe('L6-T08B BoardMapRoot local MapLibre style contract', () => {
  afterEach(() => {
    maplibreMock.mapInstances.length = 0;
    maplibreMock.controls.length = 0;
    localStorage.clear();
    sessionStorage.clear();
    act(() => {
      useBoardDisplayStore.setState({
        incidentId: 'inc-precinct-first-001',
        selectedOpIds: [],
        visibleLayers: {
          path: true,
          area: true,
          marker: true,
          op_history: true,
        },
        viewMode: 'standard',
      });
    });
  });

  test('initializes MapLibre with the S7 local style URL and local tile request guard', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();

    expect(mapOptions.style).toBe('/tiles/styles/osm-local.json');
    expect(mapOptions.attributionControl).toBe(false);
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;
    expect(transformRequest?.('/tiles/styles/osm-local.json', 'Style')).toBeTruthy();
    expect(transformRequest?.('/tiles/osm-local/15/27925/12680.pbf', 'Tile')).toBeTruthy();
    expect(transformRequest?.('/tiles/gwangju-building-labels/16/55877/25377.pbf', 'Tile')).toBeTruthy();
    expect(transformRequest?.('/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0-255.pbf', 'Glyphs')).toBeTruthy();
  });

  test('adds required WEB headers to allowed local S7 style and tile requests', async () => {
    localStorage.setItem('accessToken', 'board-map-access-token');
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;

    expect(transformRequest?.('/tiles/styles/osm-local.json', 'Style')).toMatchObject({
      url: '/tiles/styles/osm-local.json',
      headers: {
        Authorization: 'Bearer board-map-access-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformRequest?.('/tiles/osm-local/15/27925/12680.pbf', 'Tile')).toMatchObject({
      url: '/tiles/osm-local/15/27925/12680.pbf',
      headers: {
        Authorization: 'Bearer board-map-access-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformRequest?.('/tiles/gwangju-building-labels/16/55877/25377.pbf', 'Tile')).toMatchObject({
      url: '/tiles/gwangju-building-labels/16/55877/25377.pbf',
      headers: {
        Authorization: 'Bearer board-map-access-token',
        'X-Client-Channel': 'WEB',
      },
    });
    expect(transformRequest?.('/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0-255.pbf', 'Glyphs')).toMatchObject({
      url: '/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0-255.pbf',
      headers: {
        Authorization: 'Bearer board-map-access-token',
        'X-Client-Channel': 'WEB',
      },
    });
  });

  test('rejects external tile hosts before MapLibre can request them', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;
    const externalTileRequests = [
      ['https://a.tile.openstreetmap.org/15/27925/12680.pbf', 'Tile'],
      ['https://api.mapbox.com/v4/mapbox.mapbox-streets-v8/15/27925/12680.vector.pbf', 'Tile'],
      ['https://maps.googleapis.com/maps/api/tile/15/27925/12680.pbf', 'Tile'],
      ['https://example.com/tiles/osm-local/15/1/1.pbf', 'Tile'],
      ['//example.com/fonts/{fontstack}/{range}.pbf', 'Glyphs'],
      ['https://example.com/sprite.json', 'SpriteJSON'],
      ['https://example.com/sprite.png', 'SpriteImage'],
      ['https://example.com/image.png', 'Image'],
    ];

    for (const [externalTileUrl, resourceType] of externalTileRequests) {
      expect(() => transformRequest?.(externalTileUrl, resourceType)).toThrow(/external tile host/i);
    }
  });

  test('rejects non-local tile paths before MapLibre can request them', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;

    expect(() => transformRequest?.('/api/not-tiles/15/1/1.pbf', 'Tile')).toThrow(/non-local tile/i);
    expect(() => transformRequest?.('/tiles/not-osm-local/15/1/1.pbf', 'Tile')).toThrow(/non-local tile/i);
    expect(() => transformRequest?.('/tiles/osm-local/15/27925/12680.pbf', 'Source')).toThrow(
      /non-local tile resource/i,
    );
    expect(() => transformRequest?.('/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0-255.pbf', 'Source')).toThrow(
      /non-local tile resource/i,
    );
    expect(() => transformRequest?.('/sprites/local.json', 'SpriteJSON')).toThrow(/non-local tile/i);
  });

  test('rejects malformed local tile paths inside the S7 tile prefix', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;
    const malformedLocalTilePaths = [
      '/tiles/osm-local/fonts/foo.pbf',
      '/tiles/osm-local/15/27925/foo.pbf',
      '/tiles/osm-local/15/27925/12680.png',
      '/tiles/gwangju-building-labels/16/55877/25377.png',
    ];

    for (const malformedLocalTilePath of malformedLocalTilePaths) {
      expect(() => transformRequest?.(malformedLocalTilePath, 'Tile')).toThrow(/non-local tile/i);
    }
  });

  test('rejects malformed local glyph paths inside the S7 tile prefix', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    expect(mapOptions.transformRequest).toEqual(expect.any(Function));

    const transformRequest = mapOptions.transformRequest;
    const malformedLocalGlyphPaths = [
      '/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/foo.pbf',
      '/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0-255.png',
      '/tiles/fonts/Noto%20Sans%20CJK%20KR%20Regular/0/255.pbf',
    ];

    for (const malformedLocalGlyphPath of malformedLocalGlyphPaths) {
      expect(() => transformRequest?.(malformedLocalGlyphPath, 'Glyphs')).toThrow(/non-local glyph/i);
    }
  });

  test('adds compact OSM and OpenMapTiles attribution at bottom-right', async () => {
    render(<BoardMapRoot />);

    const mapOptions = await getOnlyMapOptions();
    const map = maplibreMock.mapInstances[0];
    const attributionControl = maplibreMock.controls.find((control) => control.type === 'attribution');

    expect(mapOptions.attributionControl).toBe(false);
    expect(attributionControl?.options).toMatchObject({
      compact: true,
      customAttribution: expect.stringMatching(/OpenStreetMap|OSM/),
    });
    expect(attributionControl?.options).toMatchObject({
      customAttribution: expect.stringMatching(/OpenMapTiles/),
    });
    expect(map?.addControl).toHaveBeenCalledWith(attributionControl, 'bottom-right');
  });
});

async function getOnlyMapOptions() {
  await waitFor(() => {
    expect(maplibreMock.mapInstances).toHaveLength(1);
  });

  return maplibreMock.mapInstances[0]?.options ?? {};
}
