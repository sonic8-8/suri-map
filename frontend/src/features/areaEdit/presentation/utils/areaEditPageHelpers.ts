import { ApiHttpError } from '../../../../shared/api/client';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import { searchAreaApi } from '../../../searchArea/api/searchAreaApi';
import type { AreaEditPageState, AreaTreeNode } from '../constants/mockAreaEdit';

export const drawDisabledPageStates: AreaEditPageState[] = [
  'permission_denied',
  'permission_partial',
  'op_transition',
  'incident_closed',
  'error',
];

export const autoDismissValidationMessages = new Set([
  '구역 배정을 완료했습니다.',
  '필요한 모든 구역 배정을 저장했습니다.',
]);

export const splitChildMinimumMessage = '구역을 분할하려면 2개 이상의 하위 구역을 추가해 주세요.';
export const splitChildRangeMissingMessage = '추가한 모든 하위 구역의 범위를 지정해 주세요.';

export async function getActiveOverallSearchArea(incidentId: string) {
  try {
    return await searchAreaApi.fetchActiveOverall(incidentId);
  } catch (error) {
    if (error instanceof ApiHttpError && (error.code === 'overall_search_area_required' || error.status === 404)) {
      return null;
    }
    throw error;
  }
}

export function createPendingAreaNode(kind: 'unit' | 'team', index: number): AreaTreeNode {
  const id = globalThis.crypto?.randomUUID?.() ?? `${kind}-${Date.now()}-${index}`;
  const label = kind === 'unit' ? 'UNIT' : 'TEAM';

  return {
    id,
    kind,
    colorToken: getAreaColorToken(id),
    name: `${label} ${index}`,
    meta: kind === 'unit' ? '저장 전 임시 구역' : '저장 전 TEAM 구역',
    status: 'ACTIVE',
    geometryState: 'pending',
    children: [],
  };
}
