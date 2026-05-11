export type SearchAreaHierarchyLevel = 'OVERALL' | 'UNIT' | 'TEAM';
export type SearchAreaHierarchyStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export type SearchAreaHierarchyInput = {
  id: string;
  parentAreaId?: string | null;
  areaLevel?: SearchAreaHierarchyLevel;
  status: SearchAreaHierarchyStatus;
};

export type SearchAreaHierarchyNode<T extends SearchAreaHierarchyInput> = T & {
  children: SearchAreaHierarchyNode<T>[];
};

type IndexedSearchAreaHierarchyNode<T extends SearchAreaHierarchyInput> = {
  area: T;
  children: IndexedSearchAreaHierarchyNode<T>[];
  index: number;
};

export function buildSearchAreaHierarchy<T extends SearchAreaHierarchyInput>(
  areas: T[],
): SearchAreaHierarchyNode<T> | null {
  const nodesById = new Map<string, IndexedSearchAreaHierarchyNode<T>>();

  areas.forEach((area, index) => {
    nodesById.set(area.id, {
      area,
      children: [],
      index,
    });
  });

  let root: IndexedSearchAreaHierarchyNode<T> | null = null;

  for (const node of nodesById.values()) {
    if (node.area.areaLevel === 'OVERALL') {
      root ??= node;
      continue;
    }

    if (!node.area.parentAreaId) continue;

    const parent = nodesById.get(node.area.parentAreaId);
    if (!parent) continue;
    parent.children.push(node);
  }

  if (!root) return null;
  sortHierarchyChildren(root);
  return stripIndex(root);
}

function sortHierarchyChildren<T extends SearchAreaHierarchyInput>(node: IndexedSearchAreaHierarchyNode<T>) {
  node.children.sort((a, b) => a.index - b.index);
  node.children.forEach((child) => sortHierarchyChildren(child));
}

function stripIndex<T extends SearchAreaHierarchyInput>(
  node: IndexedSearchAreaHierarchyNode<T>,
): SearchAreaHierarchyNode<T> {
  return {
    ...node.area,
    children: node.children.map(stripIndex),
  };
}
