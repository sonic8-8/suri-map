export type BoardSlotRegistryEntry = {
  readonly slot: string;
  readonly featureOwner: string;
  readonly mountedBy: 'S3-2';
  readonly sourceContract: string;
  readonly purpose: string;
};

export const s3_2BoardSlotRegistryContract = [
  {
    slot: 'overall_search_area',
    featureOwner: 'S2',
    mountedBy: 'S3-2',
    sourceContract: 'SearchAreaQuery.overallOf',
    purpose: '지도 기준 범위 표시',
  },
  {
    slot: 'area',
    featureOwner: 'S2',
    mountedBy: 'S3-2',
    sourceContract: 'AreaQuery.byIncident, AreaQuery.byOp',
    purpose: '구역 폴리곤·상태 표시',
  },
  {
    slot: 'path',
    featureOwner: 'S3-1',
    mountedBy: 'S3-2',
    sourceContract: 'PathQuery.byIncident, PathQuery.byOp',
    purpose: 'PolicePhone 경로·구간 표시',
  },
  {
    slot: 'police_phone_freshness',
    featureOwner: 'S1-2',
    mountedBy: 'S3-2',
    sourceContract: 'PolicePhoneFreshnessQuery.byIncident',
    purpose: '위치 점 최신성 표시',
  },
  {
    slot: 'marker',
    featureOwner: 'S5',
    mountedBy: 'S3-2',
    sourceContract: 'MarkerQuery.byIncident',
    purpose: '마커 레이어',
  },
  {
    slot: 'toast',
    featureOwner: 'S5',
    mountedBy: 'S3-2',
    sourceContract: 'SUPPORT_REQUEST_CREATED, PERSON_FOUND',
    purpose: '지원 요청·발견 알림',
  },
  {
    slot: 'package_badge',
    featureOwner: 'S7',
    mountedBy: 'S3-2',
    sourceContract: 'OfflinePackageInstallationQuery.byIncident',
    purpose: '오프라인 패키지 상태',
  },
  {
    slot: 'op_toggle',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'OperationalPeriodQuery.list',
    purpose: 'OP 레이어 토글',
  },
  {
    slot: 'op_history',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'OperationalPeriodQuery.list, OP_TRANSITIONED, OP_ASSIGNMENT_CHANGED',
    purpose: 'OP 전환·배정 이력 표시',
  },
  {
    slot: 'handover_memo',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'HandoverMemoQuery.byContext',
    purpose: '인수인계 메모 표시',
  },
  {
    slot: 'handover_status',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'HandoverStatusSnapshot.byIncident, HANDOVER_MEMO_CREATED, OP_TRANSITIONED',
    purpose: '인수인계 준비·완료 상태 표시',
  },
  {
    slot: 'search_history_summary',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'SearchHistorySummaryQuery.byOp',
    purpose: 'AI 수색 이력 요약',
  },
  {
    slot: 'incident_terminal',
    featureOwner: 'S1-1 terminal close, S1-3 sanitized tombstone/delete summary',
    mountedBy: 'S3-2',
    sourceContract: 'INCIDENT_CLOSED, IncidentTerminalSnapshot.closed, IncidentTombstoneSnapshot.byIncident',
    purpose: '사건 종료·sanitized tombstone read-only summary·사용자 노출 가능한 localPurgeState 표시',
  },
] as const satisfies readonly BoardSlotRegistryEntry[];

export type BoardSlotName = (typeof s3_2BoardSlotRegistryContract)[number]['slot'];
