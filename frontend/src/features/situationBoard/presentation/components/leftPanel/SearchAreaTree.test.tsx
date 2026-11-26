import { render, screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';

import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { SearchAreaTree } from './SearchAreaTree';

describe('SearchAreaTree', () => {
  test('수색구역 요약에는 계정 종류 TEAM과 PATROL_CAR 계정이 배정된 구역 수가 나오고 폴리폰은 나오지 않는다', () => {
    render(
      <SearchAreaTree
        hasActiveOverallSearchArea
        savedAreaDrafts={[]}
        selectedSearchAreaId={null}
        searchAreaTree={createOverallArea([
          // 한 구역에 계정 종류가 TEAM인 계정 둘이 배정되어도 팀 배정 구역은 1개로 센다.
          createTeamArea('team-001', ['TEAM', 'TEAM']),
          createTeamArea('team-002', ['PATROL_CAR']),
          createTeamArea('team-003', ['TEAM']),
        ])}
        onOpenAreaWorkspace={() => undefined}
        onSelectSearchArea={() => undefined}
      />,
    );

    const summary = within(screen.getByLabelText('수색구역 요약'));
    expect(summary.getByText('팀 배정 구역').nextElementSibling?.textContent).toBe('2개');
    expect(summary.getByText('순찰차 배정 구역').nextElementSibling?.textContent).toBe('1개');
    expect(summary.queryByText('폴리폰')).toBeNull();
  });
});

function createOverallArea(children: SearchAreaTreeNode[]): SearchAreaTreeNode {
  return {
    id: 'overall-001',
    kind: 'overall',
    colorToken: 'AREA_BLUE_01',
    name: '전체 수색 구역',
    meta: '전체 수색 구역',
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: [],
    children,
  };
}

function createTeamArea(id: string, accountTypes: Array<'TEAM' | 'PATROL_CAR'>): SearchAreaTreeNode {
  return {
    id,
    kind: 'team',
    colorToken: 'AREA_ROSE_01',
    name: `${id} 구역`,
    meta: '',
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: accountTypes.map((accountType, index) => ({
      accountId: `${id}-account-${index + 1}`,
      displayName: `${id} 계정 ${index + 1}`,
      accountType,
    })),
    children: [],
  };
}
