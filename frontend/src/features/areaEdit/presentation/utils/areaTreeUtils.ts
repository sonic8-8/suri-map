import type { AreaTreeNode } from '../constants/mockAreaEdit';

export function flattenAreaTree(root: AreaTreeNode): AreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenAreaTree)];
}

export function findAreaPath(root: AreaTreeNode, areaId: string): AreaTreeNode[] | null {
  if (root.id === areaId) return [root];

  for (const child of root.children ?? []) {
    const childPath = findAreaPath(child, areaId);
    if (childPath) return [root, ...childPath];
  }

  return null;
}

export function findParentArea(root: AreaTreeNode, areaId: string) {
  const path = findAreaPath(root, areaId);
  if (!path || path.length < 2) return null;
  return path[path.length - 2];
}

export function getAncestorAreaIds(root: AreaTreeNode, areaId: string) {
  const path = findAreaPath(root, areaId);
  if (!path) return new Set<string>();
  return new Set(path.slice(0, -1).map((area) => area.id));
}

export function getAreaAndDescendantIds(root: AreaTreeNode, areaId: string): Set<string> {
  const targetPath = findAreaPath(root, areaId);
  const targetArea = targetPath?.[targetPath.length - 1];
  if (!targetArea) return new Set([areaId]);

  return new Set(flattenAreaTree(targetArea).map((area) => area.id));
}
