import { describe, expect, test } from 'vitest';

import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import type { BoardSearchAreaRow } from './searchAreaBoardMapper';
import { buildSearchAreaTree, toAssignmentsByAreaId, toSearchAreaRows } from './searchAreaBoardMapper';

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

  test('assigned account policePhoneId is resolved from freshness rows', () => {
    const board = createBoard({
      area: [
        {
          id: 'team-001',
          opId: 'op-001',
          areaLevel: 'TEAM',
          name: 'TEAM',
          version: 1,
          geometry: polygonGeometry(),
          assignedAccounts: [{ accountId: 'account-001', displayName: 'Team A' }],
        },
      ],
      police_phone_freshness: [
        {
          accountId: 'account-001',
          policePhoneId: 'phone-001',
          status: 'ONLINE',
        },
      ],
    });

    expect(toSearchAreaRows(board)[0]?.assignedAccounts[0]?.policePhoneId).toBe('phone-001');
    expect(toAssignmentsByAreaId(board).get('team-001')?.[0]?.policePhoneId).toBe('phone-001');
  });

  test('assigned account policePhoneId falls back to path and marker rows', () => {
    const board = createBoard({
      area: [
        createAreaPayload({
          id: 'team-001',
          assignedAccounts: [{ accountId: 'account-001', displayName: 'Team A' }],
        }),
        createAreaPayload({
          id: 'team-002',
          assignedAccounts: [{ accountId: 'account-002', displayName: 'Team B' }],
        }),
      ],
      path: [{ accountId: 'account-001', policePhoneId: 'phone-from-path' }],
      marker: [{ accountId: 'account-002', policePhoneId: 'phone-from-marker' }],
    });

    const rows = toSearchAreaRows(board);

    expect(rows[0]?.assignedAccounts[0]?.policePhoneId).toBe('phone-from-path');
    expect(rows[1]?.assignedAccounts[0]?.policePhoneId).toBe('phone-from-marker');
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

function createBoard(slots: Record<string, unknown>): SituationBoardResponseDto {
  return {
    incidentId: 'incident-001',
    boardResponseVersion: 1,
    serverTs: '2026-05-29T00:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    slots,
    sourceVersions: {},
    geometryHash: null,
    sourceHashes: {},
    slotSources: {},
  };
}

function createAreaPayload(overrides: Record<string, unknown>) {
  return {
    id: 'team-001',
    opId: 'op-001',
    areaLevel: 'TEAM',
    name: 'TEAM',
    version: 1,
    geometry: polygonGeometry(),
    assignedAccounts: [],
    ...overrides,
  };
}

function polygonGeometry() {
  return {
    type: 'Polygon',
    coordinates: [
      [
        [126.8, 35.1],
        [126.81, 35.1],
        [126.81, 35.11],
        [126.8, 35.1],
      ],
    ],
  };
}
