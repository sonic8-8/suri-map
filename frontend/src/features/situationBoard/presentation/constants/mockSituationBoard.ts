import type { AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';

export type OperationalPeriod = {
  id: string;
  label: string;
  reason: string;
  meta: string;
  state: 'current' | 'ended';
  startDate: string;
  startTime: string;
  endDate: string | null;
  endTime: string | null;
};

export type MarkerTypeId = 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE';
export type SupportRequestTypeId = 'DRONE' | 'POLICE_DOG' | 'OTHER';

export type MarkerFilterOption = {
  markerType: MarkerTypeId;
  supportRequestType?: SupportRequestTypeId;
  label: string;
  icon: 'clue' | 'found' | 'field' | 'note' | 'hand' | 'drone' | 'dog';
};

export type LayerFilterId = 'vehicle_path' | 'foot_path' | 'search_area' | 'marker';

export type LayerOption = {
  id: LayerFilterId;
  label: string;
};

export type RecentMarker = {
  id: string;
  eventType?: string;
  markerType?: MarkerTypeId | 'UNKNOWN';
  supportRequestType?: SupportRequestTypeId | null;
  markerTypeLabel?: string;
  title: string;
  summary: string;
  occurredAt: string;
  timeLabel: string;
  opLabel?: string;
  reporterLabel?: string;
  sourceLabel?: string;
  coordinateLabel?: string;
  coordinates?: [number, number];
  memo?: string | null;
  photoCount?: number;
  photoThumbnailUrl?: string | null;
};

export type LegendItem = {
  label: string;
  className: string;
  color?: string;
  lineStyle?: 'solid' | 'dashed';
};

export type MovementPath = {
  id: string;
  policePhoneId: string | null;
  accountId: string | null;
  routeColor: string | null;
  opId: string;
  label: string;
  movementType: 'VEHICLE' | 'FOOT' | 'UNKNOWN';
  coordinates: Array<[number, number]>;
  startedAt: string;
  endedAt: string | null;
};

export type SearchAreaTreeNode = {
  id: string;
  opId?: string | null;
  kind: 'overall' | 'unit' | 'team';
  colorToken: AreaColorToken;
  name: string;
  meta: string;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  geometryState: 'saved' | 'pending';
  assignedAccounts?: SearchAreaAssignedAccount[];
  children?: SearchAreaTreeNode[];
};

export type SearchAreaAssignedAccount = {
  accountId: string;
  displayName: string;
  policePhoneId?: string | null;
  incidentRole?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
};

export type SituationBoardFallbackData = {
  incidentId: string;
  operationalPeriods: OperationalPeriod[];
  layerOptions: LayerOption[];
  markerTypes: MarkerFilterOption[];
  supportMarkerTypes: MarkerFilterOption[];
  searchAreaTree: SearchAreaTreeNode;
  searchAreaDrafts: CompletedAreaDraft[];
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  legendItems: LegendItem[];
};

const layerOptions: LayerOption[] = [
  { id: 'vehicle_path', label: '차량 구간' },
  { id: 'foot_path', label: '도보 구간' },
  { id: 'search_area', label: '배정 구역' },
  { id: 'marker', label: '마커' },
];

const markerTypes: MarkerFilterOption[] = [
  { markerType: 'CLUE', label: '단서', icon: 'clue' },
  { markerType: 'PERSON_FOUND', label: '발견', icon: 'found' },
  { markerType: 'FIELD_CONDITION', label: '지형', icon: 'field' },
  { markerType: 'NOTE', label: '운영 메모', icon: 'note' },
  { markerType: 'SUPPORT_REQUEST', label: '지원 요청', icon: 'hand' },
];

const supportMarkerTypes: MarkerFilterOption[] = [
  { markerType: 'SUPPORT_REQUEST', supportRequestType: 'DRONE', label: '드론', icon: 'drone' },
  { markerType: 'SUPPORT_REQUEST', supportRequestType: 'POLICE_DOG', label: '경찰견', icon: 'dog' },
  { markerType: 'SUPPORT_REQUEST', supportRequestType: 'OTHER', label: '기타', icon: 'hand' },
];

const legendItems: LegendItem[] = [
  { label: '전체 수색 구역 (OVERALL)', className: 'legend-swatch area-overall' },
  { label: '부대 구역 (UNIT)', className: 'legend-swatch area-unit' },
  { label: '팀 구역 (TEAM)', className: 'legend-swatch area-team' },
  { label: '팀 구역 완료', className: 'legend-swatch area-completed' },
  { label: '운용 중인 PolicePhone', className: 'legend-swatch device-active' },
  { label: '정상', className: 'legend-swatch device-normal' },
  { label: '미동기', className: 'legend-swatch device-stale' },
  { label: '위치 끊김', className: 'legend-swatch device-lost' },
  { label: '단서', className: 'legend-swatch marker-clue' },
  { label: '발견', className: 'legend-swatch marker-found' },
  { label: '지형', className: 'legend-swatch marker-field' },
  { label: '드론', className: 'legend-swatch marker-drone' },
  { label: '경찰견', className: 'legend-swatch marker-dog' },
  { label: '기타 지원', className: 'legend-swatch marker-support' },
  { label: '메모', className: 'legend-swatch marker-note' },
];

function hashIncidentId(incidentId: string) {
  return Array.from(incidentId).reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) % 997, 17);
}

function suffixId(incidentId: string, suffix: string) {
  return `${incidentId}:${suffix}`;
}

export function createIncidentScopedFallbackBoard(incidentId: string): SituationBoardFallbackData {
  const hash = hashIncidentId(incidentId);
  const markerMinute = String(12 + (hash % 38)).padStart(2, '0');

  return {
    incidentId,
    operationalPeriods: [
      {
        id: suffixId(incidentId, 'op-1'),
        label: 'OP 1차',
        reason: '수색',
        meta: '진행 중',
        state: 'current',
        startDate: '05.10',
        startTime: `${String(9 + (hash % 7)).padStart(2, '0')}:10`,
        endDate: null,
        endTime: null,
      },
    ],
    layerOptions,
    markerTypes,
    supportMarkerTypes,
    searchAreaTree: {
      id: suffixId(incidentId, 'overall'),
      kind: 'overall',
      colorToken: getAreaColorToken(suffixId(incidentId, 'overall')),
      name: '전체 수색 구역',
      meta: '구역 데이터 없음',
      status: 'ACTIVE',
      geometryState: 'pending',
      children: [],
    },
    searchAreaDrafts: [],
    movementPaths: [],
    recentMarkers: [
      {
        id: suffixId(incidentId, 'marker-clue'),
        markerType: 'CLUE',
        markerTypeLabel: '단서',
        eventType: '단서',
        title: '단서 마커 추가',
        summary: `${incidentId} 수색 구역 내 신고 위치 확인`,
        occurredAt: `2026-05-10T14:${markerMinute}:00+09:00`,
        timeLabel: `14:${markerMinute}`,
      },
      {
        id: suffixId(incidentId, 'marker-support'),
        markerType: 'SUPPORT_REQUEST',
        supportRequestType: 'OTHER',
        markerTypeLabel: '지원 요청',
        eventType: '지원 요청',
        title: '지원 요청 마커 추가',
        summary: '현장 지원 요청 접수',
        occurredAt: `2026-05-10T13:${markerMinute}:00+09:00`,
        timeLabel: `13:${markerMinute}`,
      },
    ],
    legendItems,
  };
}
