import { describe, expect, test, vi } from 'vitest';
import type { GeoJSONSourceDiff, Map as MapLibreMap } from 'maplibre-gl';
import type { BoardMapFeature, BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import { syncMovementPathSource } from './movementPathSource';

const sourceId = 'operational-movement-path';
const feature = (id: string, version = '1'): BoardMapFeature => ({
  type: 'Feature',
  properties: { entityId: id, searchPathId: 'path', searchPathVersion: version },
  geometry: {
    type: 'LineString',
    coordinates: [
      [127, 37],
      [127.001, 37],
    ],
  },
});
const collection = (...features: BoardMapFeature[]): BoardMapFeatureCollection => ({
  type: 'FeatureCollection',
  features,
});

function createMap(initiallyLoaded = true) {
  const listeners = new Map<string, (event: { sourceDataType?: string; error?: Error }) => void>();
  let loaded = initiallyLoaded;
  const source = {
    type: 'geojson',
    setData: vi.fn(() => {
      loaded = false;
    }),
    updateData: vi.fn<(change: GeoJSONSourceDiff) => void>(() => {
      loaded = false;
    }),
    loaded: () => loaded,
    on: vi.fn((name: string, listener: (event: { sourceDataType?: string; error?: Error }) => void) =>
      listeners.set(name, listener),
    ),
  };
  const map = { getSource: () => source } as unknown as MapLibreMap;
  return {
    map,
    source,
    finish: () => {
      loaded = true;
      listeners.get('data')?.({ sourceDataType: 'content' });
    },
    fail: () => {
      loaded = true;
      listeners.get('error')?.({ error: new Error('worker failed') });
    },
  };
}

describe('syncMovementPathSource', () => {
  test('source가 준비되기 전에 자료가 바뀌면, 준비 후 최신 자료만 전달한다', () => {
    // given: 지도는 있지만 GeoJSON source의 첫 worker 요청이 완료되지 않았다.
    const { map, source, finish } = createMap(false);
    syncMovementPathSource(map, sourceId, collection(feature('old')), []);

    // when: source 준비 전에 더 최신 자료를 받는다.
    syncMovementPathSource(map, sourceId, collection(feature('new')), []);
    expect(source.setData).not.toHaveBeenCalled();
    finish();

    // then: 오래된 초기 자료를 뒤늦게 덮어쓰지 않는다.
    expect(source.setData).toHaveBeenCalledTimes(1);
    expect(source.setData).toHaveBeenCalledWith(
      expect.objectContaining({ features: [expect.objectContaining({ id: 'new' })] }),
    );
  });

  test('일부 도형만 바뀌면, 수정·추가·삭제한 도형만 전달한다', () => {
    // given: 도형 두 개를 worker에 반영했다.
    const { map, source, finish } = createMap();
    const a = feature('a');
    syncMovementPathSource(map, sourceId, collection(a, feature('b')), []);
    finish();

    // when: a는 유지하고 b 대신 c를 추가한다.
    syncMovementPathSource(map, sourceId, collection(a, feature('c')), []);

    // then: 전체 교체 없이 삭제 ID와 새 도형만 보낸다.
    expect(source.setData).toHaveBeenCalledTimes(1);
    expect(source.updateData).toHaveBeenCalledWith({ remove: ['b'], add: [expect.objectContaining({ id: 'c' })] });
  });

  test('worker 처리 중 여러 변경을 받으면, 완료 후 최신 자료로 한 번 더 갱신한다', () => {
    // given: 최초 도형을 처리 중이다.
    const { map, source, finish } = createMap();
    const unchanged = feature('unchanged');
    syncMovementPathSource(map, sourceId, collection(feature('a'), unchanged), []);

    // when: 반영 완료 전에 두 응답을 받는다.
    syncMovementPathSource(map, sourceId, collection(feature('a', '2'), feature('b'), unchanged), []);
    syncMovementPathSource(map, sourceId, collection(feature('a', '3'), feature('c'), unchanged), []);
    expect(source.updateData).not.toHaveBeenCalled();
    finish();

    // then: 중간 자료를 쌓지 않고 최신 버전과 최신 구간을 전송한다.
    expect(source.setData).toHaveBeenCalledTimes(1);
    expect(source.updateData).toHaveBeenCalledTimes(1);
    const sent = source.updateData.mock.calls[0][0];
    expect(sent).toMatchObject({ remove: [], add: [{ id: 'a', properties: { searchPathVersion: '3' } }, { id: 'c' }] });
    finish();
    expect(source.updateData).toHaveBeenCalledTimes(1);
  });

  test('전체 경로를 비우면, 대량의 개별 삭제 대신 전체 초기화를 보낸다', () => {
    // given: 이전 사건의 도형을 반영했다.
    const { map, source, finish } = createMap();
    syncMovementPathSource(map, sourceId, collection(feature('old')), []);
    finish();

    // when: 정상 빈 결과를 받는다.
    syncMovementPathSource(map, sourceId, collection(), []);

    // then: 초기화 후 새 자료만 추가한다.
    expect(source.updateData).toHaveBeenLastCalledWith({ removeAll: true });
    finish();
    syncMovementPathSource(map, sourceId, collection(feature('new')), []);
    expect(source.setData).toHaveBeenLastCalledWith(
      expect.objectContaining({ features: [expect.objectContaining({ id: 'new' })] }),
    );
  });

  test('worker가 실패하면, 최신 전체 자료로 한 번 복구하고 반복 실패를 무한 재시도하지 않는다', () => {
    // given: 최초 반영 중 더 최신 자료를 받는다.
    const { map, source, fail } = createMap();
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      syncMovementPathSource(map, sourceId, collection(feature('a')), []);
      syncMovementPathSource(map, sourceId, collection(feature('b')), []);

      // when: 첫 처리와 복구 처리가 모두 실패한다.
      fail();
      fail();

      // then: 최신 전체 자료로 복구를 시도하고 두 번째 실패는 보고한다.
      expect(source.setData).toHaveBeenCalledTimes(2);
      expect(source.setData).toHaveBeenLastCalledWith(
        expect.objectContaining({ features: [expect.objectContaining({ id: 'b' })] }),
      );
      expect(log).toHaveBeenCalled();
    } finally {
      log.mockRestore();
    }
  });
});
