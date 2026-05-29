import { describe, expect, test, vi } from 'vitest';
import type maplibregl from 'maplibre-gl';
import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import type { MovementPath, SearchAreaTreeNode } from '../../../../../shared/model/situationBoardViewModel';
import {
  createPolicePhoneIdsByAccountId,
  canCorrectReferenceMarker,
  createManualSearchPathPoints,
  createReferenceMarkerCorrectionRequest,
  interpolateManualRouteCoordinates,
  resolveSearchAreaPolicePhoneId,
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

describe('manual search path draft', () => {
  test('uses assigned PolicePhone ID from the selected area or its children', () => {
    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [{ accountId: 'account-1', displayName: 'Team A', policePhoneId: 'phone-direct' }],
        }),
      ),
    ).toBe('phone-direct');

    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [],
          children: [
            searchAreaNode({
              id: 'child-1',
              assignedAccounts: [{ accountId: 'account-2', displayName: 'Team B', policePhoneId: 'phone-child' }],
            }),
          ],
        }),
      ),
    ).toBe('phone-child');

    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [{ accountId: 'account-3', displayName: 'Team C' }],
        }),
        new Map([['account-3', 'phone-from-movement-path']]),
      ),
    ).toBe('phone-from-movement-path');

    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [],
          children: [
            searchAreaNode({
              id: 'child-2',
              assignedAccounts: [{ accountId: 'account-4', displayName: 'Team D' }],
            }),
          ],
        }),
        new Map([['account-4', 'phone-from-child-movement-path']]),
      ),
    ).toBe('phone-from-child-movement-path');

    expect(resolveSearchAreaPolicePhoneId(searchAreaNode({ assignedAccounts: [] }))).toBeNull();
  });

  test('uses only active OP movement paths for PolicePhone ID fallback', () => {
    const policePhoneIdsByAccountId = createPolicePhoneIdsByAccountId(
      [
        movementPath({ accountId: 'account-1', opId: 'op-previous', policePhoneId: 'phone-previous' }),
        movementPath({ accountId: 'account-1', opId: 'op-current', policePhoneId: 'phone-current' }),
      ],
      'op-current',
    );

    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [{ accountId: 'account-1', displayName: 'Team A' }],
        }),
        policePhoneIdsByAccountId,
      ),
    ).toBe('phone-current');
  });

  test('does not infer PolicePhone ID from another OP movement path', () => {
    const policePhoneIdsByAccountId = createPolicePhoneIdsByAccountId(
      [movementPath({ accountId: 'account-1', opId: 'op-previous', policePhoneId: 'phone-previous' })],
      'op-current',
    );

    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          assignedAccounts: [{ accountId: 'account-1', displayName: 'Team A' }],
        }),
        policePhoneIdsByAccountId,
      ),
    ).toBeNull();
  });

  test('creates point timestamps at the Android GPS sample interval', () => {
    const points = createManualSearchPathPoints(
      [
        [126.9000004, 35.1000004],
        [126.9100004, 35.1100004],
        [126.9200004, 35.1200004],
      ],
      new Date('2026-05-11T06:00:00Z'),
    );

    expect(points).toMatchObject([
      {
        lon: 126.9,
        lat: 35.1,
        clientTs: '2026-05-11T06:00:00.000Z',
        speedMps: 0,
        horizontalAccuracyM: 5,
      },
      {
        lon: 126.91,
        lat: 35.11,
        clientTs: '2026-05-11T06:00:05.000Z',
        horizontalAccuracyM: 5,
      },
      {
        lon: 126.92,
        lat: 35.12,
        clientTs: '2026-05-11T06:00:10.000Z',
        horizontalAccuracyM: 5,
      },
    ]);
    expect(points.every((point) => countDecimalPlaces(point.lon) <= 6 && countDecimalPlaces(point.lat) <= 6)).toBe(
      true,
    );
    expect(points.slice(1).every((point) => typeof point.speedMps === 'number' && point.speedMps >= 0)).toBe(true);
    expect(points.every((point) => point.pointId.length > 0)).toBe(true);
  });

  test('interpolates long anchor segments and preserves anchor order', () => {
    const anchors: Array<[number, number]> = [
      [126.9, 35.1],
      [126.901, 35.1],
      [126.901, 35.101],
    ];

    const coordinates = interpolateManualRouteCoordinates(anchors);
    const points = createManualSearchPathPoints(coordinates, new Date('2026-05-11T06:00:00Z'));

    expect(coordinates.length).toBeGreaterThan(anchors.length);
    expect(coordinates.length).toBeLessThanOrEqual(120);
    expect(coordinates[0]).toEqual(anchors[0]);
    expect(coordinates).toContainEqual(anchors[1]);
    expect(coordinates.at(-1)).toEqual(anchors[2]);
    expect(points.at(-1)?.clientTs).toBe(
      new Date(Date.parse('2026-05-11T06:00:00Z') + (coordinates.length - 1) * 5_000).toISOString(),
    );
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

function searchAreaNode(overrides: Partial<SearchAreaTreeNode> = {}): SearchAreaTreeNode {
  return {
    id: 'area-1',
    kind: 'team',
    colorToken: 'AREA_BLUE_01',
    name: '1팀',
    meta: '',
    status: 'ACTIVE',
    geometryState: 'saved',
    children: [],
    ...overrides,
  };
}

function movementPath(overrides: Partial<MovementPath> = {}): MovementPath {
  return {
    id: 'path-1',
    policePhoneId: 'phone-1',
    accountId: 'account-1',
    freshnessStatus: 'ONLINE',
    routeColor: null,
    opId: 'op-current',
    label: 'Team A',
    movementType: 'FOOT',
    coordinates: [
      [126.9, 35.1],
      [126.91, 35.11],
    ],
    startedAt: '2026-05-11T06:00:00.000Z',
    endedAt: null,
    ...overrides,
  };
}

function emptyFeatureCollection(): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}

function countDecimalPlaces(value: number) {
  const decimalPart = value.toString().split('.')[1];
  return decimalPart?.length ?? 0;
}
