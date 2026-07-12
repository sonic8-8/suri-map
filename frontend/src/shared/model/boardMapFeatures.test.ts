import { describe, expect, test } from 'vitest';
import {
  applyRouteColorsByAssignee,
  createMovementCurrentPositionFeatureCollection,
  createMovementPathFeatureCollection,
  createRouteColorAssigneeKey,
} from './boardMapFeatures';
import type { BoardMovementPath } from './boardMapSlots';

describe('boardMapFeatures', () => {
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
