import { describe, expect, test } from 'vitest';

import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import type { BoardSearchAreaRow } from './searchAreaBoardMapper';
import { buildSearchAreaTree } from './searchAreaBoardMapper';

describe('searchAreaBoardMapper', () => {
  test('generic UNIT names are replaced with the common parent organization from child teams', () => {
    const tree = buildSearchAreaTree(
      fallbackSearchAreaTree(),
      [
        createAreaRow({ id: 'overall-001', areaLevel: 'OVERALL', name: '전체' }),
        createAreaRow({ id: 'unit-001', parentAreaId: 'overall-001', areaLevel: 'UNIT', name: 'UNIT' }),
        createAreaRow({
          id: 'team-001',
          parentAreaId: 'unit-001',
          areaLevel: 'TEAM',
          name: 'TEAM',
          assignedAccounts: [
            {
              accountId: 'account-001',
              displayName: '광주경찰청 실종팀 현장1팀',
              accountType: 'TEAM',
              organizationType: 'MISSING_TEAM',
            },
          ],
        }),
        createAreaRow({
          id: 'team-002',
          parentAreaId: 'unit-001',
          areaLevel: 'TEAM',
          name: 'TEAM',
          assignedAccounts: [
            {
              accountId: 'account-002',
              displayName: '광주경찰청 실종팀 현장2팀',
              accountType: 'TEAM',
              organizationType: 'MISSING_TEAM',
            },
          ],
        }),
      ],
      [],
    );

    expect(tree.children?.[0]?.name).toBe('광주경찰청 실종팀');
    expect(tree.children?.[0]?.children?.map((area) => area.name)).toEqual(['현장1팀', '현장2팀']);
  });

  test('single child team names keep only the suffix after the inferred unit organization', () => {
    const tree = buildSearchAreaTree(
      fallbackSearchAreaTree(),
      [
        createAreaRow({ id: 'overall-001', areaLevel: 'OVERALL', name: '전체' }),
        createAreaRow({ id: 'unit-001', parentAreaId: 'overall-001', areaLevel: 'UNIT', name: 'UNIT' }),
        createAreaRow({
          id: 'team-001',
          parentAreaId: 'unit-001',
          areaLevel: 'TEAM',
          name: 'TEAM',
          assignedAccounts: [
            {
              accountId: 'account-001',
              displayName: '광주경찰청 기동대 1제대',
              accountType: 'TEAM',
              organizationType: 'SUPPORT_UNIT',
            },
          ],
        }),
      ],
      [],
    );

    expect(tree.children?.[0]?.name).toBe('광주경찰청 기동대');
    expect(tree.children?.[0]?.children?.[0]?.name).toBe('1제대');
  });

  test('server colorToken is used before local registry fallback', () => {
    const tree = buildSearchAreaTree(
      fallbackSearchAreaTree(),
      [createAreaRow({ id: 'team-001', colorToken: 'AREA_ROSE_01' })],
      [],
    );

    expect(tree.children?.[0]?.colorToken).toBe('AREA_ROSE_01');
  });
});

function createAreaRow(overrides: Partial<BoardSearchAreaRow>): BoardSearchAreaRow {
  return {
    id: 'area-001',
    opId: 'op-001',
    parentAreaId: null,
    areaLevel: 'TEAM',
    status: 'ACTIVE',
    name: 'TEAM',
    version: 1,
    coordinates: [
      [126.8, 35.1],
      [126.81, 35.1],
      [126.81, 35.11],
      [126.8, 35.1],
    ],
    assignedAccounts: [],
    ...overrides,
  };
}

function fallbackSearchAreaTree(): SearchAreaTreeNode {
  return {
    id: 'fallback-overall',
    kind: 'overall',
    colorToken: 'AREA_BLUE_01',
    name: '전체 수색 구역',
    meta: '전체 수색 구역',
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: [],
    children: [],
  };
}
