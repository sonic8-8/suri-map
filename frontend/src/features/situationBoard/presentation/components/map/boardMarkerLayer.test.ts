import { describe, expect, it, vi } from 'vitest';
import type maplibregl from 'maplibre-gl';
import {
  createMarkerSymbolSvg,
  getNearestMarkerIdAtPoint,
  getRenderedMarkerIdAtPoint,
  resolveMarkerVisualState,
  syncMarkerElementsWhenAvailable,
} from './boardMarkerLayer';

describe('boardMarkerLayer marker visuals', () => {
  it('reads the rendered marker id from a padded map click hit area', () => {
    const queryCalls: unknown[] = [];
    const map = {
      getLayer: () => true,
      queryRenderedFeatures: (query: unknown) => {
        queryCalls.push(query);
        return [{ properties: { id: 'marker-1' } }];
      },
    } as never;

    expect(getRenderedMarkerIdAtPoint(map, [100, 120])).toBe('marker-1');
    expect(queryCalls[0]).toEqual([
      [72, 92],
      [128, 148],
    ]);
  });

  it('falls back to the nearest visible marker coordinate when symbol hit testing misses', () => {
    const map = {
      project: ([longitude, latitude]: [number, number]) => ({
        x: longitude * 10,
        y: latitude * 10,
      }),
    } as never;

    expect(
      getNearestMarkerIdAtPoint(
        map,
        [102, 118],
        [
          {
            id: 'marker-near',
            title: 'near',
            summary: 'near',
            occurredAt: '2026-05-19T00:00:00Z',
            timeLabel: '09:00',
            coordinates: [10, 12],
          },
          {
            id: 'marker-hidden',
            title: 'hidden',
            summary: 'hidden',
            occurredAt: '2026-05-19T00:00:00Z',
            timeLabel: '09:00',
            coordinates: [10.1, 12],
          },
        ],
        ['marker-near'],
      ),
    ).toBe('marker-near');
  });

  it('maps selected marker state above hover and base', () => {
    expect(resolveMarkerVisualState('marker-1', 'marker-1', null)).toBe('hover');
    expect(resolveMarkerVisualState('marker-1', 'marker-1', 'marker-1')).toBe('selected');
    expect(resolveMarkerVisualState('marker-1', 'marker-2', 'marker-3')).toBe('base');
  });

  it('keeps the tactical palette on the marker shell', () => {
    expect(createMarkerSymbolSvg('CLUE', 'base')).toContain('#f59e0b');
    expect(createMarkerSymbolSvg('PERSON_FOUND', 'base')).toContain('#ef4444');
    expect(createMarkerSymbolSvg('FIELD_CONDITION', 'base')).toContain('#22c55e');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base', 'drone')).toContain('#06b6d4');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base', 'dog')).toContain('#f472b6');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base')).toContain('#8b5cf6');
    expect(createMarkerSymbolSvg('NOTE', 'base')).toContain('#3b82f6');
    expect(createMarkerSymbolSvg('UNKNOWN', 'base')).toContain('#64748b');
  });

  it('adds the cyan selected ring and glow without changing the shell path', () => {
    const selectedMarkup = createMarkerSymbolSvg('CLUE', 'selected');

    expect(selectedMarkup).toContain('#38bdf8');
    expect(selectedMarkup).toContain('opacity="0.45"');
    expect(selectedMarkup).toContain('M20 44C16.7 39.8 4 29.9 4 18.7C4 10.4 11.1 4 20 4s16 6.4 16 14.7C36 29.9 23.3 39.8 20 44Z');
  });

  it('updates an existing marker source even while map.loaded() is false', () => {
    const source = { setData: vi.fn() };
    const map = createMarkerMap({ source, loaded: false, styleLoaded: false });

    const cleanup = syncMarkerElementsWhenAvailable(
      map as unknown as maplibregl.Map,
      [],
      [],
      { current: new Map() },
      true,
      markerHandlers(),
    );

    expect(source.setData).toHaveBeenCalledWith({ type: 'FeatureCollection', features: [] });
    expect(map.once).not.toHaveBeenCalled();
    expect(cleanup).toBeUndefined();
  });

  it('defers marker source sync until map load when the source is not registered yet', () => {
    const source = { setData: vi.fn() };
    const state: { source: typeof source | null; loadHandler?: () => void } = { source: null };
    const map = createMarkerMap({
      getSource: () => state.source,
      loaded: false,
      styleLoaded: false,
      once: vi.fn((_event: string, handler: () => void) => {
        state.loadHandler = handler;
      }),
    });

    const cleanup = syncMarkerElementsWhenAvailable(
      map as unknown as maplibregl.Map,
      [],
      [],
      { current: new Map() },
      true,
      markerHandlers(),
    );
    state.source = source;
    state.loadHandler?.();

    expect(map.once).toHaveBeenCalledWith('load', expect.any(Function));
    expect(source.setData).toHaveBeenCalledWith({ type: 'FeatureCollection', features: [] });

    cleanup?.();
    expect(map.off).toHaveBeenCalledWith('load', expect.any(Function));
  });
});

function markerHandlers() {
  return {
    onHoverMarker: vi.fn(),
    onLeaveMarker: vi.fn(),
    onSelectMarker: vi.fn(),
    onCloseSelectedMarker: vi.fn(),
  };
}

function createMarkerMap({
  source = null,
  getSource,
  loaded = true,
  styleLoaded = true,
  once = vi.fn(),
}: {
  source?: { setData: ReturnType<typeof vi.fn> } | null;
  getSource?: (sourceId: string) => unknown;
  loaded?: boolean;
  styleLoaded?: boolean;
  once?: ReturnType<typeof vi.fn>;
}) {
  return {
    getSource: getSource ?? vi.fn(() => source),
    addSource: vi.fn(),
    getLayer: vi.fn(() => false),
    addLayer: vi.fn(),
    moveLayer: vi.fn(),
    on: vi.fn(),
    once,
    off: vi.fn(),
    loaded: vi.fn(() => loaded),
    isStyleLoaded: vi.fn(() => styleLoaded),
    hasImage: vi.fn(() => false),
    addImage: vi.fn(),
  };
}
