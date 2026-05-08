export const operationalPeriods: Array<{ id: string; label: string; reason: string; meta: string; state: string; startDate: string; startTime: string; endDate: string | null; endTime: string | null }> = [
  { id: 'op-8', label: '8차', reason: '수색', meta: '진행 중', state: 'current', startDate: '05.04', startTime: '20:10', endDate: null, endTime: null },
  { id: 'op-7', label: '7차', reason: '수색 범위 변경', meta: '종료', state: 'ended', startDate: '05.04', startTime: '18:45', endDate: '05.04', endTime: '20:02' },
  { id: 'op-6', label: '6차', reason: '수색', meta: '종료', state: 'ended', startDate: '05.04', startTime: '17:25', endDate: '05.04', endTime: '18:38' },
  { id: 'op-5', label: '5차', reason: '수색', meta: '종료', state: 'ended', startDate: '05.04', startTime: '17:20', endDate: '05.04', endTime: '17:23' },
  { id: 'op-4', label: '4차', reason: '수색 범위 변경', meta: '종료', state: 'ended', startDate: '05.04', startTime: '15:40', endDate: '05.04', endTime: '17:18' },
  { id: 'op-3', label: '3차', reason: '수색', meta: '종료', state: 'ended', startDate: '05.04', startTime: '14:20', endDate: '05.04', endTime: '15:35' },
  { id: 'op-2', label: '2차', reason: '수색 범위 변경', meta: '종료', state: 'ended', startDate: '05.04', startTime: '11:40', endDate: '05.04', endTime: '14:18' },
  { id: 'op-1', label: '1차', reason: '초기', meta: '종료', state: 'ended', startDate: '05.04', startTime: '09:12', endDate: '05.04', endTime: '11:35' },];

export const layerOptions: string[] = ['차량 구간', '도보 구간', '전체 수색 구역', '부대 구역', '팀 구역', '마커 레벨', '지형 마커'];

export const markerTypes = [
  { label: '단서', icon: 'clue' },
  { label: '발견', icon: 'found' },
  { label: '지형', icon: 'field' },
  { label: '운영 메모', icon: 'note' },
  { label: '지원 요청', icon: 'hand' },
] as const;

export const supportMarkerTypes = [
  { label: '드론', icon: 'drone' },
  { label: '경찰견', icon: 'dog' },
  { label: '기타', icon: 'hand' },
] as const;

export const devices = [
  { id: 'device-commander', name: '실종팀 폴리폰', meta: '실종팀 간부 · 운용 중', freshness: '방금' },
  { id: 'device-unit-1-a', name: '기동대 1부대 A팀 폴리폰', meta: '팀 폴리폰', freshness: '방금' },
] as const;

export const searchAreas = [
  { id: 'overall', name: '전체 수색 구역', meta: 'OVERALL · 14:25 갱신', colorToken: 'areaColor001' },
  { id: 'unit-1', name: '기동대 1부대', meta: 'UNIT · 팀 구역 4개', colorToken: 'areaColor002' },
  { id: 'unit-2', name: '기동대 2부대', meta: 'UNIT · 팀 구역 3개', colorToken: 'areaColor003' },
  { id: 'patrol-phone', name: '광주 북구 지구대 폴리폰', meta: '경로 기록 중 · 3분 전 동기화', colorToken: 'areaColor012' },
] as const;

export const searchAreaTree = {
  id: 'overall',
  name: '전체 수색 구역',
  meta: '실종팀 간부 · 14:25 갱신',
  state: '활성',
  colorToken: 'areaColor001',
  units: [
    {
      id: 'unit-1',
      name: '기동대 1부대',
      meta: '분할자: 실종팀 간부 · 13:50 · 4팀',
      state: '활성',
      colorToken: 'areaColor002',
      teams: [
        { id: 'unit-1-team-a', label: '팀 A', phone: '기동대 1부대 A팀 폴리폰', meta: '경로 기록 중 · 1분 전 동기화', state: '활성', colorToken: 'areaColor005' },
        { id: 'unit-1-team-b', label: '팀 B', phone: '기동대 1부대 B팀 폴리폰', meta: '경로 기록 중 · 방금 동기화', state: '활성', colorToken: 'areaColor006' },
        { id: 'unit-1-team-c', label: '팀 C', phone: '기동대 1부대 C팀 폴리폰', meta: '완료 · 14:02', state: '완료', colorToken: 'areaColor007' },
        { id: 'unit-1-team-d', label: '팀 D', phone: '기동대 1부대 D팀 폴리폰', meta: '12분 전 동기화', state: '활성', colorToken: 'areaColor008' },
      ],
    },
    {
      id: 'unit-2',
      name: '기동대 2부대',
      meta: '분할자: 실종팀 간부 · 13:55 · 3팀',
      state: '활성',
      colorToken: 'areaColor003',
      teams: [
        { id: 'unit-2-team-a', label: '팀 A', phone: '기동대 2부대 A팀 폴리폰', meta: '경로 기록 중 · 3분 전 동기화', state: '활성', colorToken: 'areaColor009' },
        { id: 'unit-2-team-b', label: '팀 B', phone: '기동대 2부대 B팀 폴리폰', meta: '경로 기록 중 · 4분 전 동기화', state: '활성', colorToken: 'areaColor010' },
        { id: 'unit-2-team-c', label: '팀 C', phone: '기동대 2부대 C팀 폴리폰', meta: '대기 · 최근 갱신 5분 전', state: '활성', colorToken: 'areaColor011' },
      ],
    },
    { id: 'patrol-phone', name: '광주 북구 지구대 폴리폰', meta: '경로 기록 중 · 3분 전 동기화', state: '활성', colorToken: 'areaColor012', teams: [] },
  ],
} as const;

const overallCoordinates = [[[126.93, 35.10], [127.02, 35.10], [127.02, 35.17], [126.93, 35.17], [126.93, 35.10]]];
const unit1Coordinates = [[[126.945, 35.115], [126.985, 35.115], [126.985, 35.15], [126.945, 35.15], [126.945, 35.115]]];
const unit2Coordinates = [[[126.985, 35.115], [127.01, 35.115], [127.01, 35.155], [126.985, 35.155], [126.985, 35.115]]];

export const searchAreaResponses = [
  { id: 'overall', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '전체 수색 구역', status: 'ACTIVE', geometry: { type: 'Polygon', coordinates: overallCoordinates }, areaLevel: 'OVERALL', colorToken: 'areaColor001', historyCount: 2 },
  { id: 'unit-1', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '기동대 1부대', status: 'ACTIVE', geometry: { type: 'Polygon', coordinates: unit1Coordinates }, areaLevel: 'UNIT', colorToken: 'areaColor002', parentSearchAreaId: 'overall', historyCount: 1 },
  { id: 'unit-2', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '기동대 2부대', status: 'ACTIVE', geometry: { type: 'Polygon', coordinates: unit2Coordinates }, areaLevel: 'UNIT', colorToken: 'areaColor003', parentSearchAreaId: 'overall', historyCount: 1 },
  { id: 'unit-1-team-a', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '기동대 1부대 A팀', status: 'ACTIVE', geometry: { type: 'Polygon', coordinates: [[[126.95, 35.12], [126.965, 35.12], [126.965, 35.14], [126.95, 35.14], [126.95, 35.12]]] }, areaLevel: 'TEAM', colorToken: 'areaColor005', parentSearchAreaId: 'unit-1', historyCount: 1 },
  { id: 'unit-1-team-b', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '기동대 1부대 B팀', status: 'ACTIVE', geometry: { type: 'Polygon', coordinates: [[[126.966, 35.12], [126.98, 35.12], [126.98, 35.14], [126.966, 35.14], [126.966, 35.12]]] }, areaLevel: 'TEAM', colorToken: 'areaColor006', parentSearchAreaId: 'unit-1', historyCount: 1 },
  { id: 'unit-1-team-c', incidentId: 'incident-gwangsan-001', opId: 'op-8', version: 1, name: '기동대 1부대 C팀', status: 'COMPLETED', geometry: { type: 'Polygon', coordinates: [[[126.95, 35.141], [126.965, 35.141], [126.965, 35.149], [126.95, 35.149], [126.95, 35.141]]] }, areaLevel: 'TEAM', colorToken: 'areaColor007', parentSearchAreaId: 'unit-1', historyCount: 2 },
] as const;

export const initialReferenceMarkerResponses = [
  { id: 'marker-last-confirmed-location', incidentId: 'incident-gwangsan-001', opId: 'op-8', type: 'CLUE', status: 'ACTIVE', version: 1, source: 'MOCK_SEED', memo: 'Mock 112 last confirmed location', location: { type: 'Point', coordinates: [126.9632, 35.1268] } },
  { id: 'marker-reporter-statement-location', incidentId: 'incident-gwangsan-001', opId: 'op-8', type: 'NOTE', status: 'ACTIVE', version: 1, source: 'MOCK_SEED', memo: 'Mock reporter statement location', location: { type: 'Point', coordinates: [126.9855, 35.1394] } },
] as const;

export const movementPathResponses = [] as const;

export const recentMarkers = [
  { id: 'marker-support', eventType: '지원 요청', title: '지원 요청 마커 추가', summary: '지구대 팀 · 드론 지원 요청', occurredAt: '2026-05-04T14:12:00+09:00', timeLabel: '14:12' },
  { id: 'marker-clue', eventType: '단서', title: '단서 마커 추가', summary: '기동대 1부대 A팀 · 북측 능선', occurredAt: '2026-05-04T14:31:00+09:00', timeLabel: '14:31' },
] as const;

export const legendItems = [
  { label: '전체 수색 구역 (OVERALL)', className: 'legend-swatch area-overall' },
  { label: '부대 구역 (UNIT) · 실종팀이 분할', className: 'legend-swatch area-unit' },
  { label: '팀 구역 (TEAM) · 부대장이 분할', className: 'legend-swatch area-team' },
  { label: '팀 구역 · 완료 처리', className: 'legend-swatch area-completed' },
  { label: '차량 구간', className: 'legend-swatch route-vehicle' },
  { label: '도보 구간', className: 'legend-swatch route-walk' },
  { label: '운용 중인 폴리폰', className: 'legend-swatch device-active' },
  { label: '비교 OP 경로', className: 'legend-swatch route-compare' },
  { label: '정상 (60초 이내 동기화)', className: 'legend-swatch device-normal' },
  { label: '1분 이상 미동기', className: 'legend-swatch device-stale' },
  { label: '5분 이상 위치 끊김', className: 'legend-swatch device-lost' },
  { label: '단서', className: 'legend-swatch marker-clue' },
  { label: '발견', className: 'legend-swatch marker-found' },
  { label: '지형', className: 'legend-swatch marker-field' },
  { label: '드론', className: 'legend-swatch marker-drone' },
  { label: '경찰견', className: 'legend-swatch marker-dog' },
  { label: '기타 지원', className: 'legend-swatch marker-support' },
  { label: '메모', className: 'legend-swatch marker-note' },
] as const;



