import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { GeoJSONSourceDiff, Map as MapLibreMap } from 'maplibre-gl';
import type { BoardMapFeature, BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import { markBoardPathHistoryReceived, recordBoardOpenClicked } from '../../../../board/model/boardMeasurement';
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
  const idleListeners: Array<() => void> = [];
  const triggerRepaint = vi.fn();
  const map = {
    getSource: () => source,
    on: vi.fn(),
    once: (name: string, listener: () => void) => {
      if (name === 'idle') idleListeners.push(listener);
    },
    triggerRepaint,
  } as unknown as MapLibreMap;
  return {
    map,
    source,
    triggerRepaint,
    idle: () => idleListeners.splice(0).forEach((listener) => listener()),
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

describe('상황판 열기부터 전체 경로 지도 반영까지 계측', () => {
  let records: Array<Record<string, unknown>> = [];
  const collect = (event: Event) => {
    if (event instanceof CustomEvent && event.detail.stage === 'all_paths_map_idle') records.push(event.detail);
  };
  beforeEach(() => {
    records = [];
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
    window.addEventListener('suri-map:board-measurement', collect);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
    window.removeEventListener('suri-map:board-measurement', collect);
  });

  test('이력을 모두 받고 그 자료로 source를 갱신한 뒤, 첫 idle의 시각을 끝 시점으로 한 번만 기록한다', () => {
    // given: 상황판을 열었다.
    const { map, finish, idle } = createMap();
    recordBoardOpenClicked('incident-1', 10);

    // when: 모든 이력을 source에 보내고, 이력을 모두 받은 상태를 알린 뒤 source 반영이 끝나 idle이 두 번 발생한다.
    syncMovementPathSource(map, sourceId, collection(feature('a')), []);
    markBoardPathHistoryReceived(true);
    finish();
    vi.spyOn(performance, 'now').mockReturnValue(1234);
    idle();
    // 같은 상황판에서 변경분 조회가 다시 끝나고 지도가 다시 idle이 된다.
    markBoardPathHistoryReceived(false);
    markBoardPathHistoryReceived(true);
    idle();

    // then: 첫 idle 시각만 performance.now 기준 값으로 남는다.
    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({ incidentId: 'incident-1', atMs: 1234 });
  });

  test('이력을 모두 받기 전에 발생한 idle은, 일부 경로를 반영했어도 끝 시점으로 기록하지 않는다', () => {
    // given: 상황판을 열고 첫 페이지 경로를 source에 반영했다.
    const { map, finish, idle } = createMap();
    recordBoardOpenClicked('incident-1', 10);
    syncMovementPathSource(map, sourceId, collection(feature('a')), []);
    finish();

    // when: 남은 이력이 있는 동안 idle이 발생한다.
    markBoardPathHistoryReceived(false);
    idle();

    // then: 끝 시점을 남기지 않는다.
    expect(records).toHaveLength(0);
  });

  test('이력을 모두 받았지만 그 자료로 source를 갱신하기 전에 발생한 idle은, 끝 시점으로 기록하지 않는다', () => {
    // given: 상황판을 열고 첫 페이지 경로를 source에 반영했다.
    const { map, finish, idle } = createMap();
    recordBoardOpenClicked('incident-1', 10);
    syncMovementPathSource(map, sourceId, collection(feature('a')), []);
    finish();

    // when: 마지막 페이지 자료를 source에 보냈지만 반영이 끝나기 전에 idle이 발생한다.
    syncMovementPathSource(map, sourceId, collection(feature('a'), feature('b')), []);
    markBoardPathHistoryReceived(true);
    idle();

    // then: 이전 자료를 그린 idle은 남기지 않고, 반영 뒤 첫 idle만 남긴다.
    expect(records).toHaveLength(0);
    finish();
    idle();
    expect(records).toHaveLength(1);
  });

  test('source가 이미 마지막 자료를 반영한 뒤 이력을 모두 받으면, 지도를 다시 그려 그다음 idle을 끝 시점으로 기록한다', () => {
    // given: 상황판을 열고 마지막 페이지 경로까지 source에 반영했다.
    const { map, finish, idle, triggerRepaint } = createMap();
    recordBoardOpenClicked('incident-1', 10);
    syncMovementPathSource(map, sourceId, collection(feature('a')), []);
    finish();
    idle();

    // when: 같은 자료로 조회가 끝났음을 알린다.
    markBoardPathHistoryReceived(true);

    // then: 새 source 갱신이 없어도 idle을 다시 받아 기록한다.
    expect(triggerRepaint).toHaveBeenCalled();
    idle();
    expect(records).toHaveLength(1);
  });

  test('경로 데이터가 하나도 없는 차수도, 빈 자료를 source에 반영한 뒤 첫 idle을 끝 시점으로 기록한다', () => {
    // given: 상황판을 열었다.
    const { map, source, finish, idle } = createMap();
    recordBoardOpenClicked('incident-1', 10);

    // when: 빈 경로 자료를 보내고 이력을 모두 받은 뒤 반영이 끝난다.
    syncMovementPathSource(map, sourceId, collection(), []);
    markBoardPathHistoryReceived(true);
    idle();
    expect(records).toHaveLength(0);
    finish();
    idle();

    // then: 기존처럼 빈 자료를 한 번 보내고, 끝 시점을 남긴다.
    expect(source.setData).toHaveBeenCalledTimes(1);
    expect(records).toHaveLength(1);
  });

  test('상황판 계측을 끄면, 시작 시점과 끝 시점을 기록하지 않고 지도를 다시 그리지 않는다', () => {
    // given: 계측을 끈 상황판이다.
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
    const all: string[] = [];
    const collectAll = (event: Event) => {
      if (event instanceof CustomEvent) all.push(event.detail.stage);
    };
    window.addEventListener('suri-map:board-measurement', collectAll);
    try {
      const { map, finish, idle, triggerRepaint } = createMap();

      // when: 열기부터 반영 뒤 idle까지 같은 순서로 진행한다.
      recordBoardOpenClicked('incident-1', 10);
      syncMovementPathSource(map, sourceId, collection(feature('a')), []);
      markBoardPathHistoryReceived(true);
      finish();
      idle();

      // then: 두 단계 모두 남지 않는다.
      expect(all).toEqual([]);
      expect(triggerRepaint).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('suri-map:board-measurement', collectAll);
    }
  });
});
