import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { SearchAreaTreeNode } from '../constants/mockSituationBoard';

export function flattenSearchAreaTree(root: SearchAreaTreeNode): SearchAreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenSearchAreaTree)];
}

export function findSearchAreaById(root: SearchAreaTreeNode, searchAreaId: string | null) {
  if (!searchAreaId) {
    return null;
  }

  return flattenSearchAreaTree(root).find((area) => area.id === searchAreaId) ?? null;
}

export function isAssignmentPendingSearchArea(area: SearchAreaTreeNode | null, savedAreaDrafts: CompletedAreaDraft[]) {
  if (!area) {
    return false;
  }

  const hasSavedGeometry = area.geometryState === 'saved' || savedAreaDrafts.some((draft) => draft.areaId === area.id);
  return (
    area.kind !== 'overall' &&
    area.status === 'ACTIVE' &&
    (area.children ?? []).length === 0 &&
    hasSavedGeometry &&
    (area.assignedAccounts ?? []).length === 0
  );
}
