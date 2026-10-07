import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render } from '@testing-library/react';
import { createElement } from 'react';
import { describe, expect, test, vi } from 'vitest';
import maplibregl from 'maplibre-gl';
import { GeoJSONVT } from '@maplibre/geojson-vt';
import * as config from '../../../../../shared/config';
import { transformLocalTileRequest } from '../../../../../shared/map/localTileMap';
import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import type { SearchAreaTreeNode } from '../../../../../shared/model/situationBoardViewModel';
import { measureBoardMapUpdate } from '../../../../board/model/boardMeasurement';
import {
  addGeoJsonSource,
  canCorrectReferenceMarker,
  createManualSearchPathPoints,
  createReferenceMarkerCorrectionRequest,
  interpolateManualRouteCoordinates,
  resolveSearchAreaPolicePhoneId,
  SearchMapCanvas,
  syncOperationalGeoJsonSourceDataWhenAvailable,
} from './SearchMapCanvas';

test('지도를 축소해도 짧은 수색 구간은 표시용 타일에 남고 다른 도형의 단순화 설정은 유지한다', () => {
  // given: 실제 source 생성 함수와 MapLibre의 타일 변환기를 짧은 GPS 구간에 연결한다.
  const data: BoardMapFeatureCollection = {
    type: 'FeatureCollection',
    features: [{
      type: 'Feature',
      properties: { entityId: 'short-segment' },
      geometry: {
        type: 'LineString',
        coordinates: Array.from({ length: 6 }, (_, i) => [126.918 + i * 0.000025, 35.16 + i * 0.000005]),
      },
    }],
  };
  const original = JSON.stringify(data);
  const addSource = vi.fn();
  const map = { getSource: vi.fn(), addSource } as unknown as maplibregl.Map;

  // when: 수색 경로 source를 생성하고 실제 라이브러리 설정으로 표시용 타일을 만든다.
  addGeoJsonSource(map, 'operational-movement-path', data);
  const options: maplibregl.GeoJSONSourceSpecification = addSource.mock.calls[0][1];
  const source = new maplibregl.GeoJSONSource(
    'path', options,
    { getActor: vi.fn() } as unknown as ConstructorParameters<typeof maplibregl.GeoJSONSource>[2],
    new maplibregl.Evented(),
  );
  const index = new GeoJSONVT(data, source.workerOptions.geojsonVtOptions);

  // then: 기본 축척과 확대 축척 모두 같은 구간을 보존하고 원본 좌표는 바꾸지 않는다.
  for (const zoom of [10, 11, 15]) {
    const scale = 2 ** zoom;
    const latitude = 35.16 * Math.PI / 180;
    const x = Math.floor((126.918 + 180) / 360 * scale);
    const y = Math.floor((1 - Math.asinh(Math.tan(latitude)) / Math.PI) / 2 * scale);
    expect(index.getTile(zoom, x, y)?.features.map(feature => feature.tags?.entityId), `zoom ${zoom}`)
      .toEqual(['short-segment']);
  }
  expect(JSON.stringify(data)).toBe(original);
  addGeoJsonSource(map, 'operational-overall_search_area', emptyFeatureCollection());
  expect(addSource.mock.calls[1][1]).not.toHaveProperty('tolerance');
});

test('부분 갱신이 연속으로 오면, 아직 표시되지 않은 이전 변경분도 계속 계측한다', () => {
  // given: 실제 계측 함수를 연결하고 첫 변경분의 render를 보류한다.
  const listeners = new Map<string, () => void>();
  let rendered: Array<{ source: string; properties: Record<string, unknown> | null }> = [];
  const query = vi.fn(() => rendered);
  const map = {
    on: (name: string, listener: () => void) => listeners.set(name, listener),
    once: (name: string, listener: () => void) => listeners.set(name, listener),
    off: vi.fn(), getLayer: () => ({}), queryRenderedFeatures: query,
  } as unknown as maplibregl.Map;
  const sourceId = 'operational-movement-path';
  const data = (id: string): BoardMapFeatureCollection => ({ type: 'FeatureCollection', features: [{
    type: 'Feature', properties: { entityId: id }, geometry: { type: 'LineString', coordinates: [[127, 37], [127.001, 37]] },
  }] });
  const records: Array<Record<string, unknown>> = [];
  const collect = (event: Event) => { if (event instanceof CustomEvent) records.push(event.detail); };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    const first = measureBoardMapUpdate(map, sourceId, ['layer'], data('a'));

    // when: 다른 도형의 변경분을 전송한 뒤 두 도형이 한 화면에 나타난다.
    const second = measureBoardMapUpdate(map, sourceId, ['layer'], data('b'), { removedIds: [] });
    rendered = [...first.features, ...second.features].map(item => ({ source: sourceId, properties: item.properties }));
    listeners.get('render')?.();

    // then: source 조회는 한 번이고, 두 변경의 표시를 각 전송 ID로 기록한다.
    expect(query).toHaveBeenCalledTimes(1);
    expect(records.filter(record => record.stage === 'map_features_rendered').map(record => record.entityIds)).toEqual([['a'], ['b']]);
    expect(records.some(record => record.stage === 'map_update_replaced')).toBe(false);

    const third = measureBoardMapUpdate(map, sourceId, ['layer'], data('c'), { removedIds: [] });
    measureBoardMapUpdate(map, sourceId, ['layer'], { type: 'FeatureCollection', features: [] }, { removedIds: ['c'] });
    rendered = third.features.map(item => ({ source: sourceId, properties: item.properties }));
    listeners.get('render')?.();
    expect(records.filter(record => record.stage === 'map_features_rendered')).toHaveLength(2);
    expect(records.filter(record => record.stage === 'map_update_replaced').at(-1)?.entityIds).toEqual(['c']);
  } finally {
    listeners.get('remove')?.();
    window.removeEventListener('suri-map:board-measurement', collect);
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
  }
});

test('누적 경로는 표시 여부만 확인하고 이번 시험의 새 GPS와 마커만 좌표를 검사한다', async () => {
  // given: 과거 구간 1,000개와 기존 마커를 지도에 전달한 뒤 시험 대상을 등록한다.
  const listeners = new Map<string, () => void>();
  const records: Array<Record<string, unknown>> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent) records.push(event.detail);
  };
  let rendered: Array<{ source: string; properties: Record<string, unknown> | null }> = [];
  const query = vi.fn(() => rendered);
  const map = {
    on: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    once: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    off: vi.fn(),
    getLayer: vi.fn(() => ({})),
    queryRenderedFeatures: query,
    getCanvas: vi.fn(() => ({ clientWidth: 800, clientHeight: 600 })),
    project: vi.fn(() => ({ x: 400, y: 300 })),
  } as unknown as maplibregl.Map;
  const source = 'operational-movement-path';
  const coordinates: Array<[number, number]> = Array.from({ length: 6 }, (_, i) => [126 + i * 0.001, 35]);
  const data: BoardMapFeatureCollection = {
    type: 'FeatureCollection',
    features: Array.from({ length: 1000 }, (_, i) => ({
      type: 'Feature',
      properties: { entityId: `segment-${i}`, searchPathId: 'test-path', searchPathVersion: '1' },
      geometry: { type: 'LineString', coordinates },
    })),
  };
  const submit = (collection: BoardMapFeatureCollection, sourceId = source) => {
    const measured = measureBoardMapUpdate(map, sourceId, ['layer'], collection);
    rendered = measured.features.map((feature) => ({ source: sourceId, properties: feature.properties }));
    listeners.get('render')?.();
  };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    submit(data);
    expect(query).toHaveBeenCalledTimes(1);
    expect(records.some((r) => r.stage === 'map_features_rendered')).toBe(true);
    expect(records.some((r) => r.stage === 'map_coordinates_checked')).toBe(false);
    Object.assign(window, {
      __SURI_MAP_COORDINATE_CHECK_TARGETS__: {
        searchPathIds: new Set(['test-path']),
        markerIds: new Set(['new-marker']),
      },
    });
    query.mockClear();

    // when: 경로 버전만 변경된 과거 구간과 새 좌표·다른 업무폰의 구간을 함께 받는다.
    const updated: BoardMapFeatureCollection = {
      ...data,
      features: data.features.map((f) => ({
        ...f,
        properties: { ...f.properties, searchPathVersion: '2' },
      })),
    };
    updated.features[0] = {
      ...updated.features[0],
      geometry: { type: 'LineString', coordinates: coordinates.map(([x, y]) => [x + 0.1, y]) },
    };
    updated.features.push({
      ...data.features[0],
      properties: { entityId: 'other-segment', searchPathId: 'other-path' },
    });
    submit(updated);
    const markers: BoardMapFeatureCollection = {
      type: 'FeatureCollection',
      features: ['old-marker', 'new-marker'].map((id) => ({
        type: 'Feature',
        properties: { id, version: '1' },
        geometry: { type: 'Point', coordinates: [126, 35] },
      })),
    };
    submit(markers, 'operational-marker');
    await vi.waitFor(() => expect(records.filter((r) => r.stage === 'map_coordinates_checked')).toHaveLength(2));

    // then: 새 GPS 6개와 시험 마커 1개만 검사하고, 같은 응답을 다시 받아도 중복 검사하지 않는다.
    expect(query).toHaveBeenCalledTimes(9); // source 조회 2회 + 좌표 7개
    expect(
      records
        .filter((r) => r.stage === 'map_coordinates_checked')
        .map((r) => r.entityId)
        .sort(),
    ).toEqual(['new-marker', 'segment-0']);
    submit(updated);
    submit(markers, 'operational-marker');
    expect(query).toHaveBeenCalledTimes(11);
    expect(records.filter((r) => r.stage === 'map_coordinates_checked')).toHaveLength(2);
  } finally {
    listeners.get('remove')?.();
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    Reflect.deleteProperty(window, '__SURI_MAP_COORDINATE_CHECK_TARGETS__');
    window.removeEventListener('suri-map:board-measurement', collect);
  }
});

test('계측을 켜도 이전 도형을 새 갱신의 렌더링으로 기록하지 않고 미관측 갱신을 구분한다', () => {
  // given: 실제 WebGL 대신 렌더링 이벤트와 조회 결과를 제어한다.
  const listeners = new Map<string, () => void>();
  const records: Array<{ stage: string; updateId: string; entityIds: string[] }> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent) records.push(event.detail);
  };
  const queryRenderedFeatures = vi.fn(() => renderedFeatures);
  const map = {
    on: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    once: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    off: vi.fn(),
    getLayer: vi.fn(() => ({})),
    queryRenderedFeatures,
  } as unknown as maplibregl.Map;
  const data: BoardMapFeatureCollection = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: {
          entityId: 'segment-1',
          searchPathId: 'path-1',
          searchPathVersion: '7',
        },
        geometry: {
          type: 'LineString',
          coordinates: [
            [126, 35],
            [126.1, 35.1],
          ],
        },
      },
    ],
  };
  let renderedFeatures: Array<{ source: string; properties: Record<string, unknown> | null }> = [];
  const sourceId = 'operational-movement-path';
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    expect(measureBoardMapUpdate(map, sourceId, ['path-layer'], data)).toBe(data);
    expect(map.on).not.toHaveBeenCalled();
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
    const previous = measureBoardMapUpdate(map, sourceId, ['path-layer'], data);
    const current = measureBoardMapUpdate(map, sourceId, ['path-layer'], data);
    expect(records.at(-1)).toMatchObject({
      stage: 'map_data_submitted',
      entities: [{ id: 'segment-1', sourceEntityType: 'search_path', sourceEntityId: 'path-1', sourceVersion: 7 }],
    });

    // when: ID는 같지만 이전 데이터인 도형을 관측한 다음 새 데이터를 관측한다.
    renderedFeatures = [{ source: sourceId, properties: previous.features[0].properties }];
    listeners.get('render')?.();
    expect(records.filter((record) => record.stage === 'map_features_rendered')).toHaveLength(0);
    renderedFeatures = [{ source: sourceId, properties: current.features[0].properties }];
    listeners.get('render')?.();
    listeners.get('render')?.();

    // then: 새 갱신은 한 번만 기록하고 원본 도형은 바꾸지 않는다.
    expect(records.filter((record) => record.stage === 'map_features_rendered')).toEqual([
      expect.objectContaining({
        entityIds: ['segment-1'],
        updateId: current.features[0].properties?.__boardMeasurementUpdateId,
      }),
    ]);
    expect(records.filter((record) => record.stage === 'map_update_replaced')).toHaveLength(1);
    expect(data.features[0].properties).toEqual({
      entityId: 'segment-1',
      searchPathId: 'path-1',
      searchPathVersion: '7',
    });
    expect(current.features[0].geometry).toBe(data.features[0].geometry);

    measureBoardMapUpdate(map, sourceId, ['path-layer'], data);
    listeners.get('idle')?.();
    expect(records.at(-1)).toMatchObject({ stage: 'map_update_unobserved_at_idle', entityIds: ['segment-1'] });
    measureBoardMapUpdate(map, sourceId, ['path-layer'], data);
    listeners.get('remove')?.();
    expect(records.at(-1)?.stage).toBe('map_update_cancelled');
    expect(map.off).toHaveBeenCalledTimes(2);
    measureBoardMapUpdate(map, 'operational-marker', ['marker-layer'], {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          properties: { id: 'marker-1', version: 3 },
          geometry: { type: 'Point', coordinates: [126, 35] },
        },
      ],
    });
    expect(records.at(-1)).toMatchObject({
      entities: [{ id: 'marker-1', sourceEntityType: 'marker', sourceEntityId: 'marker-1', sourceVersion: 3 }],
    });
    measureBoardMapUpdate(map, sourceId, ['path-layer'], {
      ...data,
      features: [{ ...data.features[0], properties: { entityId: 'segment-unknown' } }],
    });
    expect(records.at(-1)).toMatchObject({
      entities: [{ id: 'segment-unknown', sourceEntityType: 'search_path', sourceEntityId: null, sourceVersion: null }],
    });
    listeners.get('remove')?.();
    expect(JSON.stringify(records)).not.toContain('coordinates');
  } finally {
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    window.removeEventListener('suri-map:board-measurement', collect);
  }
});

test('경로 일부가 보여도 새 좌표가 화면 밖이거나 해당 위치의 도형이 다르면 좌표 표시로 세지 않는다', async () => {
  // given: 기존 좌표 두 개 뒤에 새 좌표 여섯 개를 붙인 경로다.
  const originalCrypto = globalThis.crypto;
  const digest = vi.fn(async () => new Uint8Array(32).fill(0xab).buffer);
  vi.stubGlobal('crypto', { randomUUID: () => originalCrypto.randomUUID(), subtle: { digest } });
  const listeners = new Map<string, () => void>();
  const records: Array<Record<string, unknown>> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent) records.push(event.detail);
  };
  const coordinates: Array<[number, number]> = Array.from({ length: 8 }, (_, index) => [126 + index * 0.001, 35]);
  const source = 'operational-movement-path';
  let properties: Record<string, unknown> | null = null;
  let offscreen = false;
  let matchingPixels = true;
  const map = {
    on: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    once: vi.fn((name: string, listener: () => void) => listeners.set(name, listener)),
    off: vi.fn(),
    getLayer: vi.fn(() => ({})),
    getCanvas: vi.fn(() => ({ clientWidth: 800, clientHeight: 600 })),
    project: vi.fn(() => ({ x: offscreen ? 900 : 400, y: 300 })),
    queryRenderedFeatures: vi.fn((_point: unknown, options?: unknown) =>
      options && !matchingPixels ? [] : [{ source, properties }],
    ),
  } as unknown as maplibregl.Map;
  const data: BoardMapFeatureCollection = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { entityId: 'segment-1', searchPathId: 'path-1', searchPathVersion: '7' },
        geometry: { type: 'LineString', coordinates },
      },
    ],
  };
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.__SURI_MAP_COORDINATE_CHECK_TARGETS__ = { searchPathIds: new Set(['path-1']), markerIds: new Set() };
  window.addEventListener('suri-map:board-measurement', collect);
  try {
    // when: 정상, 화면 밖, 해당 좌표에서 도형 미관측을 같은 경로 ID로 각각 확인한다.
    for (const mode of ['visible', 'outside', 'old-part-only']) {
      offscreen = mode === 'outside';
      matchingPixels = mode !== 'old-part-only';
      const measured = measureBoardMapUpdate(map, source, ['path-layer'], {
        ...data,
        features: [
          { ...data.features[0], properties: { ...data.features[0].properties, entityId: `segment-${mode}` } },
        ],
      });
      properties = measured.features[0].properties;
      listeners.get('render')?.();
      await vi.waitFor(() =>
        expect(
          records.some(
            (record) =>
              record.stage === 'map_coordinates_checked' && record.updateId === properties?.__boardMeasurementUpdateId,
          ),
        ).toBe(true),
      );
    }
    // then: 마지막 여섯 좌표의 해시는 같아도 화면 범위와 위치별 관측은 별도로 판정한다.
    const checks = records.filter((record) => record.stage === 'map_coordinates_checked');
    expect(digest).toHaveBeenCalledWith('SHA-256', new TextEncoder().encode(JSON.stringify(coordinates.slice(-6))));
    const hash = 'ab'.repeat(32);
    expect(
      checks.map((record) => [
        record.pointCount,
        record.coordinateHash,
        record.inViewport,
        record.renderedAtCoordinates,
      ]),
    ).toEqual([
      [6, hash, true, true],
      [6, hash, false, false],
      [6, hash, true, false],
    ]);
    expect(JSON.stringify(records)).not.toContain('126.00');
    expect(data.features[0].properties.__boardMeasurementUpdateId).toBeUndefined();
    digest.mockRejectedValueOnce(new Error('synthetic hashing failure'));
    properties = measureBoardMapUpdate(map, source, ['path-layer'], data).features[0].properties;
    listeners.get('render')?.();
    await vi.waitFor(() => expect(records.some((record) => record.stage === 'map_coordinate_check_failed')).toBe(true));
    expect(window.__SURI_MAP_COORDINATE_CHECKS_PENDING__).toBe(0);
  } finally {
    listeners.get('remove')?.();
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    delete window.__SURI_MAP_COORDINATE_CHECK_TARGETS__;
    window.removeEventListener('suri-map:board-measurement', collect);
    vi.unstubAllGlobals();
  }
});

test('상황판에서 자체 타일을 사용할 때 로그인 인증 정보를 함께 보낸다', () => {
  vi.spyOn(config, 'getVWorldApiKey').mockReturnValue('');
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  const mapConstructor = vi.spyOn(maplibregl, 'Map').mockImplementation(function () {
    // 생성자에 전달하는 요청 설정만 확인하고 WebGL 초기화는 진행하지 않는다.
    throw new Error('WebGL initialization is outside this test');
  });

  try {
    render(
      createElement(
        QueryClientProvider,
        { client: new QueryClient() },
        createElement(SearchMapCanvas, {
          activeOperationalPeriodId: null,
          incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
          layerVisibility: { vehiclePath: true, footPath: true, searchArea: true, marker: true },
          movementPaths: [],
          recentMarkers: [],
          operationalPeriods: [],
          focusedMarkerId: null,
          focusedMarkerSequence: 0,
          focusedSearchAreaId: null,
          focusedSearchAreaSequence: 0,
          visibleMarkerIds: [],
          savedAreaDrafts: [],
          selectedPolicePhoneLegendFilters: [],
          selectedSearchAreaLegendFilters: [],
          searchAreaTree: searchAreaNode(),
          selectedSearchAreaId: null,
          onSelectSearchArea: vi.fn(),
          onClearSelectedSearchArea: vi.fn(),
          onOpenSearchAreaSplit: vi.fn(),
          onOpenSearchAreaAssign: vi.fn(),
        }),
      ),
    );

    expect(mapConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        style: '/map-style/osm-local.json',
        transformRequest: transformLocalTileRequest,
      }),
    );
  } finally {
    cleanup();
    vi.restoreAllMocks();
  }
});

describe('syncOperationalGeoJsonSourceDataWhenAvailable', () => {
  test('updates an existing GeoJSON source without waiting for map.loaded()', () => {
    const source = { type: 'geojson', setData: vi.fn(), loaded: () => true, on: vi.fn() };
    const map = createMap({ source });
    const data = emptyFeatureCollection();

    const cleanup = syncOperationalGeoJsonSourceDataWhenAvailable(map, 'operational-movement-path', data);

    expect(source.setData).toHaveBeenCalledWith(data);
    expect(map.once).not.toHaveBeenCalled();
    expect(cleanup).toBeUndefined();
  });

  test('defers until load when the source is not registered yet', () => {
    const source = { type: 'geojson', setData: vi.fn(), loaded: () => true, on: vi.fn() };
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

    expect(resolveSearchAreaPolicePhoneId(searchAreaNode({ assignedAccounts: [] }))).toBeNull();
  });

  test('does not infer PolicePhone ID from a child search area in another OP', () => {
    expect(
      resolveSearchAreaPolicePhoneId(
        searchAreaNode({
          opId: 'op-current',
          assignedAccounts: [],
          children: [
            searchAreaNode({
              id: 'child-previous',
              opId: 'op-previous',
              assignedAccounts: [{ accountId: 'account-1', displayName: 'Team A', policePhoneId: 'phone-previous' }],
            }),
          ],
        }),
        'op-current',
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
