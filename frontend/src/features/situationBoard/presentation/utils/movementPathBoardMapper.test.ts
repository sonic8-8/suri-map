import { describe, expect, test } from 'vitest';
import { areaColorTokens } from '../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { resolveRouteColorByGeometry } from '../../../../shared/model/routeAreaColorMatcher';
import type { MovementPath, SearchAreaTreeNode } from '../constants/mockSituationBoard';
import { assignRouteColorsToMovementPaths } from './movementPathBoardMapper';

describe('assignRouteColorsToMovementPaths', () => {
  test('keeps route colors scoped by OP when the same account is assigned to different areas', () => {
    const paths = assignRouteColorsToMovementPaths(
      [
        createMovementPath({ id: 'path-op-a', opId: OP_ID, accountId: ACCOUNT_ID }),
        createMovementPath({ id: 'path-op-b', opId: NEXT_OP_ID, accountId: ACCOUNT_ID }),
      ],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'areaColor001',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        children: [
          createAreaNode({ id: AREA_ID, opId: OP_ID, colorToken: 'areaColor003' }),
          createAreaNode({ id: NEXT_AREA_ID, opId: NEXT_OP_ID, colorToken: 'areaColor006' }),
        ],
      },
      [
        createDraft({ areaId: AREA_ID, colorToken: 'areaColor003' }),
        createDraft({ areaId: NEXT_AREA_ID, colorToken: 'areaColor006' }),
      ],
    );

    expect(paths[0].routeColor).toBe(areaColorTokens.areaColor003.lineColor);
    expect(paths[1].routeColor).toBe(areaColorTokens.areaColor006.lineColor);
  });

  test('uses the actual route geometry area color over stale assignee mapping', () => {
    expect(
      resolveRouteColorByGeometry(
        [[126.9162, 35.1625], [126.917, 35.1625]],
        [
          {
            id: AREA_ID,
            opId: OP_ID,
            kind: 'team',
            coordinates: [
              [126.913, 35.162],
              [126.914, 35.162],
              [126.914, 35.163],
              [126.913, 35.163],
              [126.913, 35.162],
            ],
            lineColor: areaColorTokens.areaColor003.lineColor,
          },
          {
            id: NEXT_AREA_ID,
            opId: OP_ID,
            kind: 'team',
            coordinates: [
              [126.916, 35.162],
              [126.918, 35.162],
              [126.918, 35.163],
              [126.916, 35.163],
              [126.916, 35.162],
            ],
            lineColor: areaColorTokens.areaColor006.lineColor,
          },
        ],
        OP_ID,
      ),
    ).toBe(areaColorTokens.areaColor006.lineColor);

    const paths = assignRouteColorsToMovementPaths(
      [createMovementPath({ coordinates: [[126.9162, 35.1625], [126.917, 35.1625]] })],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'areaColor001',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        children: [
          createAreaNode({ id: AREA_ID, opId: OP_ID, colorToken: 'areaColor003' }),
          createAreaNode({ id: NEXT_AREA_ID, opId: OP_ID, colorToken: 'areaColor006', assignedAccounts: [] }),
        ],
      },
      [
        createDraft({
          areaId: AREA_ID,
          colorToken: 'areaColor003',
          coordinates: [
            [126.913, 35.162],
            [126.914, 35.162],
            [126.914, 35.163],
            [126.913, 35.163],
            [126.913, 35.162],
          ],
        }),
        createDraft({
          areaId: NEXT_AREA_ID,
          colorToken: 'areaColor006',
          coordinates: [
            [126.916, 35.162],
            [126.918, 35.162],
            [126.918, 35.163],
            [126.916, 35.163],
            [126.916, 35.162],
          ],
        }),
      ],
    );

    expect(paths[0].routeColor).toBe(areaColorTokens.areaColor006.lineColor);
  });

  test('keeps paths renderable for incident accounts that are not assigned to any area', () => {
    const paths = assignRouteColorsToMovementPaths(
      [createMovementPath({ id: 'path-unassigned', accountId: UNASSIGNED_ACCOUNT_ID, policePhoneId: null })],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'areaColor001',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        assignedAccounts: [],
        children: [createAreaNode({ assignedAccounts: [] })],
      },
      [],
    );

    expect(paths).toHaveLength(1);
    expect(paths[0].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

function createAreaNode(overrides: Partial<SearchAreaTreeNode>): SearchAreaTreeNode {
  return {
    id: AREA_ID,
    opId: OP_ID,
    kind: 'team',
    colorToken: 'areaColor003',
    name: 'team area',
    meta: 'TEAM',
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: [{ accountId: ACCOUNT_ID, displayName: '대원 A', policePhoneId: POLICE_PHONE_ID }],
    children: [],
    ...overrides,
  };
}

function createDraft(overrides: Partial<CompletedAreaDraft>): CompletedAreaDraft {
  return {
    areaId: AREA_ID,
    kind: 'team',
    colorToken: 'areaColor003',
    label: 'team area',
    coordinates: [
      [126.913, 35.162],
      [126.914, 35.162],
      [126.914, 35.163],
      [126.913, 35.163],
      [126.913, 35.162],
    ],
    ...overrides,
  };
}

function createMovementPath(overrides: Partial<MovementPath>): MovementPath {
  return {
    id: 'path-a',
    policePhoneId: POLICE_PHONE_ID,
    accountId: ACCOUNT_ID,
    freshnessStatus: 'UNKNOWN',
    routeColor: null,
    opId: OP_ID,
    label: 'Path A',
    movementType: 'FOOT',
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
const AREA_ID = 'cccccccc-cccc-cccc-cccc-cccccccc0001';
const NEXT_AREA_ID = 'cccccccc-cccc-cccc-cccc-cccccccc0002';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
const UNASSIGNED_ACCOUNT_ID = '10000000-0000-0000-0000-000000000002';
