import { describe, expect, test } from 'vitest';
import {
  applyRouteColorsByAssignee,
  createMovementCurrentPositionFeatureCollection,
  createMovementPathFeatureCollection,
  createMovementPathFeatureMapper,
  createRouteColorAssigneeKey,
} from './boardMapFeatures';
import type { BoardMovementPath } from './boardMapSlots';

describe('boardMapFeatures', () => {
  test('과거 구간을 앞에 붙여도, 그리기 순서와 다른 경로의 도형 재사용을 유지한다', () => {
    // given: 첫 경로의 두 구간과 다른 경로 한 개를 변환했다.
    const mapFeatures = createMovementPathFeatureMapper({ includeLabel: true });
    const first = createMovementPath({ id: 'a-2', searchPathId: 'path-a' });
    const second = createMovementPath({ id: 'a-3', searchPathId: 'path-a' });
    const other = createMovementPath({ id: 'b-1', searchPathId: 'path-b' });
    const previous = mapFeatures([first, second, other], OP_ID);

    // when: 첫 경로의 과거 구간을 앞에 추가한다.
    const paths = [createMovementPath({ id: 'a-1', searchPathId: 'path-a' }), first, second, other];
    const current = mapFeatures(paths, OP_ID);

    // then: worker의 삽입 순서가 바뀌어도 정렬 값으로 원래 순서를 복원할 수 있다.
    const reordered = [...current.features].reverse().sort((a, b) => Number(a.properties.drawOrder) - Number(b.properties.drawOrder));
    expect(reordered.map(item => item.properties.entityId)).toEqual(['a-1', 'a-2', 'a-3', 'b-1']);
    expect(current.features[3]).toBe(previous.features[2]);
    expect(current.features.map(item => {
      const properties = { ...item.properties };
      delete properties.drawOrder;
      return { ...item, properties };
    })).toEqual(createMovementPathFeatureCollection(paths, OP_ID, { includeLabel: true }).features);
  });

  test('차수·빈 결과가 바뀌면, 도형 속성과 재사용 자료를 초기화한다', () => {
    // given: 동일 경로 객체를 서로 다른 활성 차수에서 사용한다.
    const mapFeatures = createMovementPathFeatureMapper();
    const path = createMovementPath();
    const initial = mapFeatures([path], OP_ID);

    // when: 활성 차수를 바꾸고 정상 빈 결과를 받는다.
    const otherPeriod = mapFeatures([path], NEXT_OP_ID);
    expect(mapFeatures([], NEXT_OP_ID).features).toEqual([]);
    const restored = mapFeatures([path], NEXT_OP_ID);

    // then: 이전 차수 속성이나 비우기 전 도형을 그대로 재사용하지 않는다.
    expect(otherPeriod.features[0].properties.isActiveOp).toBe('false');
    expect(otherPeriod.features[0]).not.toBe(initial.features[0]);
    expect(restored.features[0]).not.toBe(otherPeriod.features[0]);
  });

  test('구간 도형을 만들면, 구간 ID와 원본 경로의 ID·버전을 구분해 유지한다', () => {
    // given: 원본 경로의 일부를 나타내는 구간이다.
    const path = createMovementPath({ id: 'segment-1', searchPathId: 'path-1', searchPathVersion: 7 });

    // when: 지도 도형으로 변환한다.
    const collection = createMovementPathFeatureCollection([path], OP_ID);

    // then: 원본 경로의 버전을 구간 버전이나 렌더링 ID로 바꾸지 않는다.
    expect(collection.features[0].properties).toMatchObject({
      entityId: 'segment-1', searchPathId: 'path-1', searchPathVersion: '7',
    });
    expect(collection.features[0].geometry.coordinates).toBe(path.coordinates);
    const unknown = createMovementPathFeatureCollection([createMovementPath()], OP_ID);
    expect(unknown.features[0].properties).not.toHaveProperty('searchPathVersion');
  });

  test('같은 경로에서 표시 속성이 같고 끝점이 이어지는 구간은, 첫 구간 ID의 선 하나로 잇는다', () => {
    // given: 한 경로의 세 구간이 앞 구간의 끝점에서 다음 구간이 시작한다.
    const mapFeatures = createMovementPathFeatureMapper({ includeLabel: true });
    const segments = [
      createMovementPath({ id: 'a-1', searchPathId: 'path-a', searchPathVersion: 3, coordinates: [[126.91, 35.16], [126.92, 35.17]] }),
      createMovementPath({ id: 'a-2', searchPathId: 'path-a', searchPathVersion: 3, coordinates: [[126.92, 35.17], [126.93, 35.18], [126.94, 35.19]] }),
      createMovementPath({ id: 'a-3', searchPathId: 'path-a', searchPathVersion: 3, coordinates: [[126.94, 35.19], [126.95, 35.2]] }),
    ];

    // when: 지도 도형으로 변환한다.
    const collection = mapFeatures(segments, OP_ID);

    // then: 공유 끝점을 한 번만 담은 선 하나가 되고, 첫 구간의 ID와 그리기 순서를 쓴다.
    expect(collection.features).toHaveLength(1);
    expect(collection.features[0].geometry.coordinates).toEqual([
      [126.91, 35.16], [126.92, 35.17], [126.93, 35.18], [126.94, 35.19], [126.95, 35.2],
    ]);
    expect(collection.features[0].properties).toMatchObject({
      entityId: 'a-1', searchPathId: 'path-a', searchPathVersion: '3', drawOrder: '0',
    });
  });

  test('표시 속성이 다르거나 끝점이 끊기거나 다른 경로이면, 선을 나눈다', () => {
    // given: 이동 유형이 바뀌는 구간, 끝점이 끊긴 구간, 다른 경로의 구간이 이어진다.
    const mapFeatures = createMovementPathFeatureMapper();
    const segments = [
      createMovementPath({ id: 'a-1', searchPathId: 'path-a', coordinates: [[126.91, 35.16], [126.92, 35.17]] }),
      createMovementPath({ id: 'a-2', searchPathId: 'path-a', movementType: 'FOOT', coordinates: [[126.92, 35.17], [126.93, 35.18]] }),
      createMovementPath({ id: 'a-3', searchPathId: 'path-a', movementType: 'FOOT', coordinates: [[126.94, 35.19], [126.95, 35.2]] }),
      createMovementPath({ id: 'b-1', searchPathId: 'path-b', movementType: 'FOOT', coordinates: [[126.95, 35.2], [126.96, 35.21]] }),
    ];

    // when: 지도 도형으로 변환한다.
    const collection = mapFeatures(segments, OP_ID);

    // then: 넷 모두 따로 그린다.
    expect(collection.features.map(item => item.properties.entityId)).toEqual(['a-1', 'a-2', 'a-3', 'b-1']);
  });

  test('경로 끝에 이어지는 구간이 추가되면, 그 묶음 도형만 새로 만든다', () => {
    // given: 두 경로를 변환했다.
    const mapFeatures = createMovementPathFeatureMapper();
    const first = createMovementPath({ id: 'a-1', searchPathId: 'path-a', coordinates: [[126.91, 35.16], [126.92, 35.17]] });
    const other = createMovementPath({ id: 'b-1', searchPathId: 'path-b', coordinates: [[127.01, 35.16], [127.02, 35.17]] });
    const previous = mapFeatures([first, other], OP_ID);

    // when: 첫 경로 끝에 같은 유형의 구간이 이어진다.
    const appended = createMovementPath({ id: 'a-2', searchPathId: 'path-a', coordinates: [[126.92, 35.17], [126.93, 35.18]] });
    const current = mapFeatures([first, appended, other], OP_ID);

    // then: 같은 ID의 묶음 도형이 새 좌표로 바뀌고, 다른 경로 도형은 재사용한다.
    expect(current.features[0]).not.toBe(previous.features[0]);
    expect(current.features[0].properties.entityId).toBe('a-1');
    expect(current.features[0].geometry.coordinates).toEqual([[126.91, 35.16], [126.92, 35.17], [126.93, 35.18]]);
    expect(current.features[1]).toBe(previous.features[1]);
  });

  test('경로 도형에는, 지도 표시와 증분 갱신과 측정에 쓰는 속성만 담는다', () => {
    // given: 시각, 계정, 차수, 이름이 있는 구간이다.
    const path = createMovementPath({ id: 'a-1', searchPathId: 'path-a', searchPathVersion: 2 });

    // when: 지도 도형으로 변환한다.
    const collection = createMovementPathFeatureMapper({ includeLabel: true })([path], OP_ID);

    // then: 구간 시각 등 원본 값은 경로 데이터에만 남기고 도형에는 복사하지 않는다.
    expect(Object.keys(collection.features[0].properties).sort()).toEqual([
      'deviceColor', 'drawOrder', 'entityId', 'freshnessStatus', 'isActiveOp', 'movementType', 'routeCoreColor',
      'searchPathId', 'searchPathVersion',
    ]);
  });

  test('uses deterministic fallback colors when no assigned area color exists', () => {
    const paths = applyRouteColorsByAssignee(
      [
        createMovementPath({ id: 'path-a', accountId: ACCOUNT_ID }),
        createMovementPath({ id: 'path-b', accountId: ACCOUNT_ID }),
        createMovementPath({ id: 'path-c', accountId: OTHER_ACCOUNT_ID }),
      ],
      new Map(),
    );

    expect(paths[0].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
    expect(paths[1].routeColor).toBe(paths[0].routeColor);
    expect(paths[2].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
  });

  test('uses the account assigned area color', () => {
    const paths = applyRouteColorsByAssignee(
      [createMovementPath({ id: 'path-a', accountId: ACCOUNT_ID })],
      new Map([[ACCOUNT_ID, '#123456']]),
    );

    expect(paths[0].routeColor).toBe('#123456');
  });

  test('uses OP scoped assignee color before unscoped assignee color', () => {
    const paths = applyRouteColorsByAssignee(
      [
        createMovementPath({ id: 'path-op-a', opId: OP_ID, accountId: ACCOUNT_ID }),
        createMovementPath({ id: 'path-op-b', opId: NEXT_OP_ID, accountId: ACCOUNT_ID }),
      ],
      new Map([[ACCOUNT_ID, '#999999']]),
      {
        accountId: new Map([
          [createRouteColorAssigneeKey(OP_ID, ACCOUNT_ID), '#123456'],
          [createRouteColorAssigneeKey(NEXT_OP_ID, ACCOUNT_ID), '#abcdef'],
        ]),
      },
    );

    expect(paths[0].routeColor).toBe('#123456');
    expect(paths[1].routeColor).toBe('#abcdef');
  });

  test('emits non-empty device colors for movement path features using fallback color', () => {
    const [path] = applyRouteColorsByAssignee(
      [createMovementPath({ id: 'path-a', accountId: ACCOUNT_ID })],
      new Map(),
    );

    const collection = createMovementPathFeatureCollection([path], OP_ID);

    expect(collection.features).toHaveLength(1);
    expect(collection.features[0].properties.deviceColor).toMatch(/^#[0-9a-f]{6}$/i);
    expect(collection.features[0].properties.routeCoreColor).toMatch(/^#[0-9a-f]{6}$/i);
  });

  test('keeps rendered route core color identical to the assigned area color', () => {
    const collection = createMovementPathFeatureCollection(
      [createMovementPath({ routeColor: '#12abef' })],
      OP_ID,
    );

    expect(collection.features[0].properties.deviceColor).toBe('#12abef');
    expect(collection.features[0].properties.routeCoreColor).toBe('#12abef');
  });

  test('emits police phone freshness status on movement path features', () => {
    const collection = createMovementPathFeatureCollection(
      [createMovementPath({ freshnessStatus: 'LOST' })],
      OP_ID,
    );

    expect(collection.features[0].properties.freshnessStatus).toBe('LOST');
    expect(collection.features[0].properties).not.toHaveProperty('policePhoneId');
  });

  test('colors current position points from police phone freshness status', () => {
    const collection = createMovementCurrentPositionFeatureCollection(
      [createMovementPath({ freshnessStatus: 'STALE' })],
      OP_ID,
    );

    expect(collection.features[0].properties.currentPositionColor).toBe('#f59e0b');
  });

  test('emits one current position point per account using the latest path endpoint', () => {
    const collection = createMovementCurrentPositionFeatureCollection(
      [
        createMovementPath({
          id: 'old-path',
          accountId: ACCOUNT_ID,
          endedAt: '2026-05-16T09:05:00+09:00',
          coordinates: [
            [126.91, 35.16],
            [126.92, 35.17],
          ],
        }),
        createMovementPath({
          id: 'latest-path',
          accountId: ACCOUNT_ID,
          endedAt: '2026-05-16T09:08:00+09:00',
          routeColor: '#12abef',
          coordinates: [
            [126.93, 35.18],
            [126.94, 35.19],
          ],
        }),
      ],
      OP_ID,
    );

    expect(collection.features).toHaveLength(1);
    expect(collection.features[0].geometry).toEqual({ type: 'Point', coordinates: [126.94, 35.19] });
    expect(collection.features[0].properties.pathId).toBe('latest-path');
    expect(collection.features[0].properties.routeCoreColor).toBe('#12abef');
  });

  test('keeps current positions separate for different accounts', () => {
    const collection = createMovementCurrentPositionFeatureCollection(
      [
        createMovementPath({
          id: 'phone-path',
          accountId: ACCOUNT_ID,
          coordinates: [
            [126.91, 35.16],
            [126.92, 35.17],
          ],
        }),
        createMovementPath({
          id: 'account-path',
          accountId: OTHER_ACCOUNT_ID,
          coordinates: [
            [126.93, 35.18],
            [126.94, 35.19],
          ],
        }),
      ],
      OP_ID,
    );

    expect(collection.features.map((feature) => feature.properties.pathId).sort()).toEqual([
      'account-path',
      'phone-path',
    ]);
  });
});

function createMovementPath(overrides: Partial<BoardMovementPath> = {}): BoardMovementPath {
  return {
    id: 'path-a',
    accountId: ACCOUNT_ID,
    freshnessStatus: 'UNKNOWN',
    routeColor: null,
    opId: OP_ID,
    label: 'Path A',
    movementType: 'VEHICLE',
    coordinates: [
      [126.913, 35.162],
      [126.914, 35.163],
    ],
    startedAt: '2026-05-16T09:00:00+09:00',
    endedAt: null,
    ...overrides,
  };
}

const OP_ID = '88888888-8888-8888-8888-888888880001';
const NEXT_OP_ID = '88888888-8888-8888-8888-888888880002';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
const OTHER_ACCOUNT_ID = '10000000-0000-0000-0000-000000000002';
