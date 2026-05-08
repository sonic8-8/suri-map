import { CheckCircle2, Hexagon, Layers3, MousePointer2 } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { AreaColorToken } from '../../../../shared/constants/areaColorTokens';

export type AreaEditToolId = 'overall' | 'unit' | 'team' | 'complete';
export type AreaNodeKind = 'overall' | 'unit' | 'team';
export type AreaEditPageState = 'empty' | 'default' | 'permission_denied' | 'permission_partial' | 'offline' | 'incident_closed' | 'error';

export type AreaEditTool = { id: AreaEditToolId; label: string; description: string; Icon: LucideIcon };
export type AreaTreeNode = {
  id: string;
  kind: AreaNodeKind;
  colorToken: AreaColorToken;
  name: string;
  meta: string;
  state: 'active' | 'completed' | 'assigned' | 'unassigned';
  children?: AreaTreeNode[];
};
export type MapAreaShape = { id: string; kind: AreaNodeKind; label: string; meta: string; className: string };
export type AreaEditPosition = [number, number];
export type CompletedAreaDraft = { areaId: string; kind: AreaNodeKind; colorToken: AreaColorToken; label: string; coordinates: AreaEditPosition[] };

export const MOCK_PAGE_STATE: AreaEditPageState = 'default';
export const MOCK_UNASSIGNED_PHONE_COUNT = 3;

export const areaEditTools: AreaEditTool[] = [
  { id: 'overall', label: '전체 수색 구역 그리기', description: '사건 전체 범위를 OVERALL 구역으로 지정합니다.', Icon: Hexagon },
  { id: 'unit', label: 'UNIT 분할', description: 'OVERALL 안에서 부대 단위 구역을 나눕니다.', Icon: Layers3 },
  { id: 'team', label: 'TEAM 분할', description: 'UNIT 안에서 팀 또는 폴리폰 배정 구역을 나눕니다.', Icon: MousePointer2 },
  { id: 'complete', label: '완료 확인', description: '임시 저장한 수색 구역 범위를 확인합니다.', Icon: CheckCircle2 },
];

export const areaTree: AreaTreeNode = {
  id: 'overall-01',
  kind: 'overall',
  colorToken: 'areaColor001',
  name: '전체 수색 구역',
  meta: '광산구 일대 · 14:30 갱신',
  state: 'unassigned',
  children: [
    {
      id: 'unit-01',
      kind: 'unit',
      colorToken: 'areaColor002',
      name: '기동대 1부대',
      meta: '14:31 분할 · 3팀',
      state: 'unassigned',
      children: [
        { id: 'team-a', kind: 'team', colorToken: 'areaColor005', name: 'A팀', meta: '기동대 1부대 A팀 폴리폰', state: 'unassigned' },
        { id: 'team-b', kind: 'team', colorToken: 'areaColor006', name: 'B팀', meta: '기동대 1부대 B팀 폴리폰', state: 'unassigned' },
        { id: 'team-c', kind: 'team', colorToken: 'areaColor007', name: 'C팀', meta: '기동대 1부대 C팀 폴리폰', state: 'unassigned' },
      ],
    },
    {
      id: 'unit-02',
      kind: 'unit',
      colorToken: 'areaColor004',
      name: '지구대 지원',
      meta: '북측 진입로 대기 · 1팀 분할 필요',
      state: 'unassigned',
      children: [{ id: 'team-d', kind: 'team', colorToken: 'areaColor012', name: '지구대 지원팀', meta: '지구대 지원팀 폴리폰', state: 'unassigned' }],
    },
  ],
};

export const mapAreaShapes: MapAreaShape[] = [
  { id: 'overall-01', kind: 'overall', label: '전체 수색 구역', meta: '14:30 갱신', className: 'overallArea' },
  { id: 'unit-01', kind: 'unit', label: '기동대 1부대', meta: 'UNIT', className: 'unitAreaOne' },
  { id: 'team-a', kind: 'team', label: 'A팀', meta: '기동대 1부대 A팀', className: 'teamAreaA' },
  { id: 'team-b', kind: 'team', label: 'B팀', meta: '기동대 1부대 B팀', className: 'teamAreaB' },
  { id: 'team-c', kind: 'team', label: 'C팀', meta: '기동대 1부대 C팀', className: 'teamAreaC' },
  { id: 'unit-02', kind: 'unit', label: '지구대 지원', meta: 'UNIT', className: 'unitAreaTwo' },
  { id: 'team-d', kind: 'team', label: '지구대 지원팀', meta: '팀 분할 필요', className: 'teamAreaD' },
];
