import { describe, expect, test } from 'vitest';
import {
  applyRouteColorsByAssignee,
  createMovementPathFeatureCollection,
} from './boardMapFeatures';
import type { BoardMovementPath } from './boardMapSlots';

describe('boardMapFeatures', () => {
  test('담당 구역 색상이 없어도 이동 경로 fallback 색상을 부여한다', () => {
    const paths = applyRouteColorsByAssignee(
      [
        createMovementPath({ id: 'path-a', policePhoneId: POLICE_PHONE_ID }),
        createMovementPath({ id: 'path-b', policePhoneId: POLICE_PHONE_ID }),
        createMovementPath({ id: 'path-c', policePhoneId: null, accountId: ACCOUNT_ID }),
      ],
      new Map(),
      new Map(),
    );

    expect(paths[0].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
    expect(paths[1].routeColor).toBe(paths[0].routeColor);
    expect(paths[2].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
  });

  test('매칭된 담당 구역 색상은 fallback보다 우선한다', () => {
    const paths = applyRouteColorsByAssignee(
      [createMovementPath({ id: 'path-a', policePhoneId: POLICE_PHONE_ID, accountId: ACCOUNT_ID })],
      new Map([[ACCOUNT_ID, '#123456']]),
      new Map([[POLICE_PHONE_ID, '#abcdef']]),
    );

    expect(paths[0].routeColor).toBe('#123456');
  });

  test('fallback 색상이 적용된 이동 경로 feature는 빈 deviceColor를 만들지 않는다', () => {
    const [path] = applyRouteColorsByAssignee(
      [createMovementPath({ id: 'path-a', policePhoneId: POLICE_PHONE_ID })],
      new Map(),
      new Map(),
    );

    const collection = createMovementPathFeatureCollection([path], OP_ID);

    expect(collection.features).toHaveLength(1);
    expect(collection.features[0].properties.deviceColor).toMatch(/^#[0-9a-f]{6}$/i);
    expect(collection.features[0].properties.routeCoreColor).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

function createMovementPath(overrides: Partial<BoardMovementPath> = {}): BoardMovementPath {
  return {
    id: 'path-a',
    policePhoneId: null,
    accountId: null,
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
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
