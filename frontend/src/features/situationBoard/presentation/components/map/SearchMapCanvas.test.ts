import { describe, expect, test, vi } from 'vitest';
import type maplibregl from 'maplibre-gl';
import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import {
  canCorrectReferenceMarker,
  createReferenceMarkerCorrectionRequest,
  syncOperationalGeoJsonSourceDataWhenAvailable,
} from './SearchMapCanvas';

describe('syncOperationalGeoJsonSourceDataWhenAvailable', () => {
  test('updates an existing GeoJSON source without waiting for map.loaded()', () => {
    const source = { setData: vi.fn() };
    const map = createMap({ source });
    const data = emptyFeatureCollection();

    const cleanup = syncOperationalGeoJsonSourceDataWhenAvailable(map, 'operational-movement-path', data);

    expect(source.setData).toHaveBeenCalledWith(data);
    expect(map.once).not.toHaveBeenCalled();
    expect(cleanup).toBeUndefined();
  });

  test('defers until load when the source is not registered yet', () => {
    const source = { setData: vi.fn() };
    const state: { source: typeof source | null; loadHandler?: () => void } = { source: null };
    const map = createMap({
      getSource: () => state.source,
      once: vi.fn((_event: string, handler: () => void) => {
        state.loadHandler = handler;
      }),
      off: vi.fn(),
    });
    const data = emptyFeatureCollection();

    const cleanup = syncOperationalGeoJsonSourceDataWhenAvailable(map, 'operational-movement-path', data);
    state.source = source;
    state.loadHandler?.();

    expect(map.once).toHaveBeenCalledWith('load', expect.any(Function));
    expect(source.setData).toHaveBeenCalledWith(data);

    cleanup?.();
    expect(map.off).toHaveBeenCalledWith('load', expect.any(Function));
  });
});

describe('reference marker correction', () => {
  test('allows only versioned reference marker sources', () => {
    expect(canCorrectReferenceMarker({ source: 'MOCK_SEED', version: 1 })).toBe(true);
    expect(canCorrectReferenceMarker({ source: 'SYSTEM', version: 2 })).toBe(true);
    expect(canCorrectReferenceMarker({ source: 'APP', version: 1 })).toBe(false);
    expect(canCorrectReferenceMarker({ source: 'MOCK_SEED', version: null })).toBe(false);
  });

  test('creates canonical marker update request for center point correction', () => {
    expect(createReferenceMarkerCorrectionRequest({ version: 3 }, [126.9134, 35.1631])).toEqual({
      version: 3,
      location: {
        type: 'Point',
        coordinates: [126.9134, 35.1631],
      },
    });
  });
});

function createMap(overrides: {
  source?: { setData: ReturnType<typeof vi.fn> } | null;
  getSource?: (sourceId: string) => unknown;
  once?: ReturnType<typeof vi.fn>;
  off?: ReturnType<typeof vi.fn>;
}) {
  return {
    getSource: overrides.getSource ?? vi.fn(() => overrides.source),
    once: overrides.once ?? vi.fn(),
    off: overrides.off ?? vi.fn(),
  } as unknown as maplibregl.Map;
}

function emptyFeatureCollection(): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}
