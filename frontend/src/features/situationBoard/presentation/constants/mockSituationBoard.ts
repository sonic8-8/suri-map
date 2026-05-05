export const incidentSummary = {
  code: '[사건 ID]',
  missingPerson: '[실종자 이름] · 60대 여',
  lastSeen: '광주 북구 ○○ · 13:20',
  commanders: '실종팀 간부 · 기동대장 · 지구대 팀장',
};

export const headerActions = ['인수인계 메모', '수색 이력 요약', '구역 편집', '사건 목록'];

export const operationalPeriods = [
  { id: 'op-3', label: 'OP 3차', meta: '현재 OP · 진행 중', time: '14:20-' },
  { id: 'op-2', label: 'OP 2차', meta: '비교 선택됨', time: '11:40-14:18' },
  { id: 'op-1', label: 'OP 1차', meta: '종료', time: '09:12-11:35' },
];

export const layerOptions = ['차량 구간', '도보 구간', '전체 수색 구역', '부대 구역', '팀 구역', '마커 라벨', '지형 마커'];

export const markerTypes = [
  { label: '단서', icon: 'clue' },
  { label: '발견', icon: 'found' },
  { label: '지형', icon: 'field' },
  { label: '운영 메모', icon: 'note' },
] as const;

export const supportMarkerTypes = [
  { label: '드론', icon: 'drone' },
  { label: '경찰견', icon: 'dog' },
  { label: '기타 지원', icon: 'support' },
] as const;

export const navigationLinks = ['구역 편집 / 분할 / 할당', 'OP 비교 / 인수인계', '차량·도보 보정', '오프라인 패키지 상태'];

export const devices = [
  { id: 'device-commander', name: '실종팀 폴리폰', meta: '실종팀 간부 · 운용 중', freshness: '방금' },
  { id: 'device-unit-1-a', name: '기동대 1부대 A팀 폴리폰', meta: '팀 폴리폰', freshness: '방금' },
  { id: 'device-unit-1-d', name: '기동대 1부대 D팀 폴리폰', meta: '12분 전 동기화', freshness: '12분 전' },
  { id: 'device-patrol', name: '지구대 순찰차 폴리폰', meta: '광주 북구 지구대', freshness: '3분 전' },
];

export const searchAreas = [
  { id: 'overall', name: '전체 수색 구역', meta: 'OVERALL · 14:25 갱신' },
  { id: 'unit-1', name: '기동대 1부대', meta: 'UNIT · 팀 구역 4개' },
  { id: 'unit-2', name: '기동대 2부대', meta: 'UNIT · 팀 구역 3개' },
  { id: 'patrol', name: '광주 북구 지구대', meta: 'UNIT · 단일 운용' },
];

export const recentMarkers = [
  { id: 'marker-clue', type: '단서', summary: '기동대 1부대 A팀 · 14:31' },
  { id: 'marker-found', type: '발견', summary: '실종팀 폴리폰 · 14:20' },
  { id: 'marker-support', type: '지원 요청', summary: '지구대 팀 · 14:12' },
];

export const visibleLayers = ['팀 경로', '구역', '마커', 'OP 이력'];

export const legendItems = [
  { label: '전체 수색 구역', className: 'legend-swatch overall' },
  { label: '팀 경로', className: 'legend-swatch route' },
  { label: '운용 중인 폴리폰', className: 'legend-swatch device' },
  { label: '마커', className: 'legend-swatch marker' },
];
