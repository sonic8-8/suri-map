import type { AreaTreeNode } from '../constants/mockAreaEdit';

export function isSavedGeometryArea(area: AreaTreeNode, savedAreaIds: Set<string>) {
  return area.geometryState === 'saved' || savedAreaIds.has(area.id);
}

export function isSearchAreaLeafNode(area: AreaTreeNode | null): area is AreaTreeNode {
  return (
    area !== null &&
    area.kind !== 'overall' &&
    area.status === 'ACTIVE' &&
    (area.children ?? []).length === 0
  );
}

export function isAssignableSearchAreaLeaf(area: AreaTreeNode | null, savedAreaIds: Set<string>) {
  return isSearchAreaLeafNode(area) && isSavedGeometryArea(area, savedAreaIds);
}
