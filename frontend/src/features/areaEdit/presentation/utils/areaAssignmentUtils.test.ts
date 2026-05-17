import { describe, expect, it } from 'vitest';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import type { AreaTreeNode } from '../constants/mockAreaEdit';
import { isAssignableSearchAreaLeaf, isSearchAreaLeafNode } from './areaAssignmentUtils';

function areaNode(overrides: Partial<AreaTreeNode>): AreaTreeNode {
  return {
    id: 'area-1',
    kind: 'unit',
    colorToken: getAreaColorToken('area-1'),
    name: '수색 구역',
    meta: '테스트 구역',
    status: 'ACTIVE',
    geometryState: 'saved',
    children: [],
    ...overrides,
  };
}

describe('areaAssignmentUtils', () => {
  it('treats a saved child area without children as an assignable leaf', () => {
    const leafUnit = areaNode({ kind: 'unit' });

    expect(isSearchAreaLeafNode(leafUnit)).toBe(true);
    expect(isAssignableSearchAreaLeaf(leafUnit, new Set())).toBe(true);
  });

  it('keeps a parent area with child areas unassignable', () => {
    const parentUnit = areaNode({
      children: [areaNode({ id: 'team-1', kind: 'team' })],
    });

    expect(isSearchAreaLeafNode(parentUnit)).toBe(false);
    expect(isAssignableSearchAreaLeaf(parentUnit, new Set())).toBe(false);
  });

  it('allows saved TEAM areas because they are leaves', () => {
    const team = areaNode({ kind: 'team' });

    expect(isAssignableSearchAreaLeaf(team, new Set())).toBe(true);
  });

  it('does not assign overall, pending, completed, or cancelled areas', () => {
    expect(isAssignableSearchAreaLeaf(areaNode({ kind: 'overall' }), new Set())).toBe(false);
    expect(isAssignableSearchAreaLeaf(areaNode({ geometryState: 'pending' }), new Set())).toBe(false);
    expect(isAssignableSearchAreaLeaf(areaNode({ status: 'COMPLETED' }), new Set())).toBe(false);
    expect(isAssignableSearchAreaLeaf(areaNode({ status: 'CANCELLED' }), new Set())).toBe(false);
  });
});
