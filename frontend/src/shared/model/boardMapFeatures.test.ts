import { describe, expect, test } from 'vitest';
import {
  applyRouteColorsByAssignee,
  createMovementPathFeatureCollection,
  createRouteColorAssigneeKey,
} from './boardMapFeatures';
import type { BoardMovementPath } from './boardMapSlots';

describe('boardMapFeatures', () => {
  test('uses deterministic fallback colors when no assigned area color exists', () => {
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

  test('prefers account assigned area color over police phone color', () => {
    const paths = applyRouteColorsByAssignee(
      [createMovementPath({ id: 'path-a', policePhoneId: POLICE_PHONE_ID, accountId: ACCOUNT_ID })],
      new Map([[ACCOUNT_ID, '#123456']]),
      new Map([[POLICE_PHONE_ID, '#abcdef']]),
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
      new Map(),
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
      [createMovementPath({ id: 'path-a', policePhoneId: POLICE_PHONE_ID })],
      new Map(),
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
  });
});

function createMovementPath(overrides: Partial<BoardMovementPath> = {}): BoardMovementPath {
  return {
    id: 'path-a',
    policePhoneId: null,
    accountId: null,
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
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
