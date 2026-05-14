import type { AreaNodeKind } from './areaDraft';
import type { SearchAreaHierarchyStatus } from './searchAreaHierarchy';

export type SearchAreaGeometryState = 'saved' | 'pending';

export type SearchAreaDisplayState =
  | 'completed'
  | 'cancelled'
  | 'geometrySaved'
  | 'geometryPending'
  | 'assignmentPending'
  | 'assignmentDone';

export type SearchAreaDisplayStateInput = {
  kind: AreaNodeKind;
  status: SearchAreaHierarchyStatus;
  geometryState: SearchAreaGeometryState;
  hasSavedGeometry?: boolean;
  assignedAccountCount?: number;
};

export const searchAreaDisplayStateLabel: Record<SearchAreaDisplayState, string> = {
  completed: '완료',
  cancelled: '취소됨',
  geometrySaved: '범위 저장됨',
  geometryPending: '범위 지정 필요',
  assignmentPending: '담당 배정 필요',
  assignmentDone: '배정 완료',
};

export const searchAreaDisplayStateTone: Record<SearchAreaDisplayState, 'active' | 'closed' | 'neutral' | 'danger'> = {
  completed: 'closed',
  cancelled: 'closed',
  geometrySaved: 'neutral',
  geometryPending: 'danger',
  assignmentPending: 'danger',
  assignmentDone: 'active',
};

export function getSearchAreaDisplayState(input: SearchAreaDisplayStateInput): SearchAreaDisplayState {
  if (input.status === 'COMPLETED') return 'completed';
  if (input.status === 'CANCELLED') return 'cancelled';

  const hasSavedGeometry = input.hasSavedGeometry ?? input.geometryState === 'saved';
  if (!hasSavedGeometry) return 'geometryPending';
  if (input.kind !== 'team') return 'geometrySaved';
  return (input.assignedAccountCount ?? 0) > 0 ? 'assignmentDone' : 'assignmentPending';
}
