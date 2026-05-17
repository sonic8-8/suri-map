import type { AreaNodeKind } from './areaDraft';
import type { SearchAreaHierarchyLevel } from './searchAreaHierarchy';

const searchAreaKindLabelByKind: Record<AreaNodeKind, string> = {
  overall: '전체 수색 구역',
  unit: '부대 구역',
  team: '팀 구역',
};

const searchAreaSummaryLabelByLevel: Record<SearchAreaHierarchyLevel, string> = {
  OVERALL: '전체',
  UNIT: '부대',
  TEAM: '팀',
};

const searchAreaFallbackNameByKind: Record<Exclude<AreaNodeKind, 'overall'>, string> = {
  unit: '부대',
  team: '팀',
};

export function formatSearchAreaKindLabel(kind: AreaNodeKind) {
  return searchAreaKindLabelByKind[kind];
}

export function formatSearchAreaSummaryLabel(level: SearchAreaHierarchyLevel) {
  return searchAreaSummaryLabelByLevel[level];
}

export function formatSearchAreaFallbackName(kind: Exclude<AreaNodeKind, 'overall'>, index: number) {
  return `${searchAreaFallbackNameByKind[kind]} ${index}`;
}
