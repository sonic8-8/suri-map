import { CheckCircle2, Hexagon, Layers3, MousePointer2 } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import type { AreaEditPosition, AreaNodeKind, CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';

export type AreaEditToolId = 'overall' | 'unit' | 'team' | 'complete';
export type AreaEditPageState =
  | 'empty'
  | 'default'
  | 'permission_denied'
  | 'permission_partial'
  | 'offline'
  | 'op_transition'
  | 'incident_closed'
  | 'error';

export type AreaEditTool = { id: AreaEditToolId; label: string; description: string; Icon: LucideIcon };
export type SearchAreaStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type AreaGeometryState = 'saved' | 'pending';
export type AreaTreeNode = {
  id: string;
  kind: AreaNodeKind;
  colorToken: AreaColorToken;
  name: string;
  meta: string;
  status: SearchAreaStatus;
  geometryState: AreaGeometryState;
  sourceVersion?: number;
  children?: AreaTreeNode[];
};
export type MapAreaShape = { id: string; kind: AreaNodeKind; label: string; meta: string; className: string };
export type { AreaEditPosition, AreaNodeKind, CompletedAreaDraft };

export const MOCK_PAGE_STATE: AreaEditPageState = 'default';

export const areaEditTools: AreaEditTool[] = [
  {
    id: 'overall',
    label: '전체 수색 구역',
    description: '사건 전체 기준 범위를 OVERALL 구역으로 작성합니다.',
    Icon: Hexagon,
  },
  {
    id: 'unit',
    label: 'UNIT 분할',
    description: 'OVERALL 안에 부대 단위 구역을 작성합니다.',
    Icon: Layers3,
  },
  {
    id: 'team',
    label: 'TEAM 분할',
    description: 'UNIT 안에 팀 단위 구역을 작성합니다.',
    Icon: MousePointer2,
  },
  {
    id: 'complete',
    label: '완료 표시',
    description: '작성한 수색 구역을 저장합니다.',
    Icon: CheckCircle2,
  },
];

export const areaTree: AreaTreeNode = {
  id: 'overall-01',
  kind: 'overall',
  colorToken: getAreaColorToken('overall-01'),
  name: '전체 수색 구역',
  meta: '전체 수색 구역 필요',
  status: 'ACTIVE',
  geometryState: 'pending',
  children: [],
};

// TODO(area-edit): 문서 기준 구현 전까지 고정 UNIT/TEAM tree를 실제 화면에 연결하지 않는다.
// 구역 분할 페이지는 먼저 GET /api/search-areas?incidentId=...&areaLevel=OVERALL&status=ACTIVE
// 결과를 확인해야 한다. 결과가 없으면 전체 수색 구역 생성부터 시작한다.
// UNIT/TEAM은 POST /api/search-areas 또는 POST /api/search-areas/{searchAreaId}/split 성공 후
// 실제 응답으로만 표시한다. 담당 배정은 별도 POST /api/search-areas/{searchAreaId}/assignments로 처리한다.
// const fixedUiTestTree: AreaTreeNode[] = [
//   {
//     id: 'unit-01',
//     kind: 'unit',
//     colorToken: 'AREA_ORANGE_01',
//     name: '기동대 1부대',
//     meta: '14:31 분할 / 3팀',
//     state: 'unassigned',
//     children: [
//       { id: 'team-a', kind: 'team', colorToken: 'AREA_CYAN_01', name: 'A팀', meta: '기동대 1부대 A팀', state: 'unassigned' },
//       { id: 'team-b', kind: 'team', colorToken: 'AREA_ROSE_01', name: 'B팀', meta: '기동대 1부대 B팀', state: 'unassigned' },
//       { id: 'team-c', kind: 'team', colorToken: 'AREA_YELLOW_01', name: 'C팀', meta: '기동대 1부대 C팀', state: 'unassigned' },
//     ],
//   },
//   {
//     id: 'unit-02',
//     kind: 'unit',
//     colorToken: 'AREA_PURPLE_01',
//     name: '지원대',
//     meta: '지원 예정',
//     state: 'unassigned',
//   },
// ];

export const mapAreaShapes: MapAreaShape[] = [
  { id: 'overall-01', kind: 'overall', label: '전체 수색 구역', meta: '작성 필요', className: 'overallArea' },
];
