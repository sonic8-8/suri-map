import type { ApiClient, ApiQuery, ApiRequestOptions } from './client';

type MockAccount = {
  id: string;
  accountCode: string;
  accountType: 'TEAM' | 'PATROL_CAR' | 'COMMAND';
  organizationType: 'MISSING_TEAM' | 'SUPPORT_UNIT' | 'POLICE_SUBSTATION';
  authorities: Array<'MISSING_TEAM_COMMANDER' | 'FIELD_COMMANDER' | 'MEMBER'>;
};

const INCIDENT_ID = 'inc-precinct-first-001';
const OP_ID = 'op-precinct-001-op1';
const SERVER_TS = '2026-05-14T09:00:00+09:00';

const accounts: MockAccount[] = [
  {
    id: '11111111-1111-1111-1111-111111110001',
    accountCode: 'acct-precinct-cmd',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    authorities: ['FIELD_COMMANDER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110004',
    accountCode: 'acct-cmd-alpha',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    authorities: ['MISSING_TEAM_COMMANDER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110005',
    accountCode: 'acct-team-alpha',
    accountType: 'TEAM',
    organizationType: 'MISSING_TEAM',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110007',
    accountCode: 'acct-support-car',
    accountType: 'PATROL_CAR',
    organizationType: 'SUPPORT_UNIT',
    authorities: ['MEMBER'],
  },
];

const defaultAccount = accounts[1];

const overallArea = {
  id: 'area-overall-001',
  incidentId: INCIDENT_ID,
  opId: OP_ID,
  areaLevel: 'OVERALL',
  status: 'ACTIVE',
  name: '인왕산 전체 수색 구역',
  version: 1,
  sequence: 1,
  sourceSpec: 'S2',
  sourceHash: 'mock-overall-area-v1',
  latestEventId: 'mock-event-area-overall-001',
  geometry: {
    type: 'Polygon',
    coordinates: [
      [
        [126.948, 37.565],
        [126.968, 37.565],
        [126.968, 37.579],
        [126.948, 37.579],
        [126.948, 37.565],
      ],
    ],
  },
};

const unitArea = {
  id: 'area-unit-west-001',
  incidentId: INCIDENT_ID,
  opId: OP_ID,
  parentAreaId: overallArea.id,
  areaLevel: 'UNIT',
  status: 'ACTIVE',
  name: '인왕산 서측 단위 구역',
  version: 1,
  sequence: 2,
  sourceSpec: 'S2',
  sourceHash: 'mock-unit-area-v1',
  latestEventId: 'mock-event-area-unit-001',
  geometry: {
    type: 'Polygon',
    coordinates: [
      [
        [126.952, 37.568],
        [126.961, 37.568],
        [126.961, 37.575],
        [126.952, 37.575],
        [126.952, 37.568],
      ],
    ],
  },
};

const teamAreaNorth = {
  id: 'area-team-north-001',
  incidentId: INCIDENT_ID,
  opId: OP_ID,
  parentAreaId: unitArea.id,
  areaLevel: 'TEAM',
  status: 'ACTIVE',
  name: '인왕산 북측 팀 구역',
  version: 1,
  sequence: 3,
  sourceSpec: 'S2',
  sourceHash: 'mock-team-north-v1',
  latestEventId: 'mock-event-area-team-north-001',
  assignedAccounts: [
    {
      accountId: '11111111-1111-1111-1111-111111110005',
      displayName: '실종팀 알파 팀',
      policePhoneId: '00000000-0000-0000-0000-000000000102',
    },
  ],
  geometry: {
    type: 'Polygon',
    coordinates: [
      [
        [126.952, 37.5715],
        [126.961, 37.5715],
        [126.961, 37.575],
        [126.952, 37.575],
        [126.952, 37.5715],
      ],
    ],
  },
};

const teamAreaSouth = {
  id: 'area-team-south-001',
  incidentId: INCIDENT_ID,
  opId: OP_ID,
  parentAreaId: unitArea.id,
  areaLevel: 'TEAM',
  status: 'ACTIVE',
  name: '인왕산 남측 팀 구역',
  version: 1,
  sequence: 4,
  sourceSpec: 'S2',
  sourceHash: 'mock-team-south-v1',
  latestEventId: 'mock-event-area-team-south-001',
  assignedAccounts: [
    {
      accountId: '11111111-1111-1111-1111-111111110007',
      displayName: '지원 브라보 순찰차',
      policePhoneId: '50000000-0000-0000-0000-000000000002',
    },
  ],
  geometry: {
    type: 'Polygon',
    coordinates: [
      [
        [126.952, 37.568],
        [126.961, 37.568],
        [126.961, 37.5715],
        [126.952, 37.5715],
        [126.952, 37.568],
      ],
    ],
  },
};

const areaRows = [unitArea, teamAreaNorth, teamAreaSouth];

const pathRows = [
  {
    id: 'path-precinct-car-001',
    incidentId: INCIDENT_ID,
    opId: OP_ID,
    policePhoneId: '50000000-0000-0000-0000-000000000002',
    accountId: '11111111-1111-1111-1111-111111110007',
    status: 'ACTIVE',
    version: 1,
    sequence: 5,
    sourceSpec: 'S3-1',
    sourceHash: 'mock-path-car-v1',
    latestEventId: 'mock-event-path-car-001',
    label: '지원 브라보 순찰차',
    movementType: 'VEHICLE',
    startedAt: '2026-05-14T09:10:00+09:00',
    endedAt: null,
    geometry: {
      type: 'LineString',
      coordinates: [
        [126.953, 37.5684],
        [126.9542, 37.569],
        [126.956, 37.5697],
        [126.9582, 37.5704],
      ],
    },
  },
  {
    id: 'path-alpha-foot-001',
    incidentId: INCIDENT_ID,
    opId: OP_ID,
    policePhoneId: '00000000-0000-0000-0000-000000000102',
    accountId: '11111111-1111-1111-1111-111111110005',
    status: 'ACTIVE',
    version: 1,
    sequence: 6,
    sourceSpec: 'S3-1',
    sourceHash: 'mock-path-foot-v1',
    latestEventId: 'mock-event-path-foot-001',
    label: '실종팀 알파 도보',
    movementType: 'FOOT',
    startedAt: '2026-05-14T09:18:00+09:00',
    endedAt: null,
    geometry: {
      type: 'LineString',
      coordinates: [
        [126.955, 37.572],
        [126.956, 37.5726],
        [126.9571, 37.5732],
        [126.9584, 37.574],
      ],
    },
  },
];

const markerRows = [
  {
    id: 'mk-precinct-clue-001',
    incidentId: INCIDENT_ID,
    opId: OP_ID,
    markerType: 'CLUE',
    status: 'ACTIVE',
    version: 1,
    sequence: 7,
    sourceSpec: 'S5',
    sourceHash: 'mock-marker-clue-v1',
    latestEventId: 'mock-event-marker-clue-001',
    title: '신고자 진술 위치',
    memo: '인왕산 북측 산책로 입구에서 마지막 목격',
    occurredAt: '2026-05-14T09:20:00+09:00',
    createdByAccountDisplayName: '종로 지구대 지휘',
    photoCount: 1,
    geometry: {
      type: 'Point',
      coordinates: [126.9565, 37.5712],
    },
  },
  {
    id: 'mk-support-request-001',
    incidentId: INCIDENT_ID,
    opId: OP_ID,
    markerType: 'SUPPORT_REQUEST',
    supportRequestType: 'DRONE',
    status: 'ACTIVE',
    version: 1,
    sequence: 8,
    sourceSpec: 'S5',
    sourceHash: 'mock-marker-support-v1',
    latestEventId: 'mock-event-marker-support-001',
    title: '드론 지원 요청',
    memo: '북측 능선 위쪽 시야 확보 요청',
    occurredAt: '2026-05-14T09:35:00+09:00',
    createdByAccountDisplayName: '실종팀 알파 지휘',
    photoCount: 0,
    geometry: {
      type: 'Point',
      coordinates: [126.959, 37.5738],
    },
  },
];

const packageBadgeRows = [
  {
    id: 'pkg-status-precinct-ready-001',
    incidentId: INCIDENT_ID,
    policePhoneId: '00000000-0000-0000-0000-000000000102',
    policePhoneCode: 'dev-alpha-phone-01',
    policePhoneName: '실종팀 알파 폴리폰',
    accountId: '11111111-1111-1111-1111-111111110005',
    accountName: '실종팀 알파 팀',
    accountType: 'TEAM',
    organizationType: 'MISSING_TEAM',
    incidentRole: 'MEMBER',
    packageStatus: 'READY',
    status: 'READY',
    manifestVersion: 3,
    readyForOfflineUse: true,
    localWarningInput: {
      raised: false,
      activeManifestVersion: 3,
      reason: '',
    },
    version: 3,
    sequence: 9,
    sourceSpec: 'S7',
    sourceHash: 'mock-package-ready-v3',
    latestEventId: 'mock-event-package-ready-001',
  },
  {
    id: 'pkg-status-support-stale-001',
    incidentId: INCIDENT_ID,
    policePhoneId: '50000000-0000-0000-0000-000000000002',
    policePhoneCode: 'dev-support-car-01',
    policePhoneName: '지원 브라보 순찰차 폴리폰',
    accountId: '11111111-1111-1111-1111-111111110007',
    accountName: '지원 브라보 순찰차',
    accountType: 'PATROL_CAR',
    organizationType: 'SUPPORT_UNIT',
    incidentRole: 'MEMBER',
    packageStatus: 'STALE',
    status: 'STALE',
    manifestVersion: 2,
    readyForOfflineUse: false,
    localWarningInput: {
      raised: true,
      activeManifestVersion: 3,
      reason: '최신 구역 변경 반영 필요',
    },
    version: 2,
    sequence: 10,
    sourceSpec: 'S7',
    sourceHash: 'mock-package-stale-v2',
    latestEventId: 'mock-event-package-stale-001',
  },
];

export const mockApiClient: ApiClient = {
  request: mockRequest,
  get: (path, options) => mockRequest(path, { ...options, method: 'GET' }),
  post: (path, body, options) => mockRequest(path, { ...options, method: 'POST', body }),
  patch: (path, body, options) => mockRequest(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => mockRequest(path, { ...options, method: 'DELETE' }),
};

async function mockRequest<TResponse, TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody> = {},
): Promise<TResponse> {
  const method = options.method ?? 'GET';
  const normalizedPath = normalizePath(path);
  const response = routeMockRequest(normalizedPath, method, options.body, options.query);
  return clone(response) as TResponse;
}

function routeMockRequest(path: string, method: string, body: unknown, query?: ApiQuery): unknown {
  if (path === '/auth/login' && method === 'POST') return loginResponse(body);
  if (path === '/auth/logout' && method === 'POST') return { status: 'LOGGED_OUT' };
  if (path === '/incidents' && method === 'GET') return incidentListResponse();
  if (path === '/incidents/import' && method === 'POST') return importIncidentResponse();
  if (path === '/search-areas' && method === 'GET') return searchAreasResponse(query);
  if (path === '/search-areas' && method === 'POST') return createSearchAreaResponse(body);
  if (path === '/operational-periods' && method === 'POST') return createOperationalPeriodResponse(body);
  if (path === '/duty-shifts' && method === 'GET') return { items: [] };
  if (path === '/handover-memos' && method === 'GET') return handoverMemoListResponse();
  if (path === '/handover-memos' && method === 'POST') return createHandoverMemoResponse(body);
  if (path === '/search-paths' && method === 'GET') return { paths: pathRows };

  const incidentMatch = path.match(/^\/incidents\/([^/]+)$/);
  if (incidentMatch && method === 'GET') return incidentDetailResponse(decodeURIComponent(incidentMatch[1]));

  const boardMatch = path.match(/^\/incidents\/([^/]+)\/board$/);
  if (boardMatch && method === 'GET') return boardResponse(decodeURIComponent(boardMatch[1]));

  const opListMatch = path.match(/^\/incidents\/([^/]+)\/operational-periods$/);
  if (opListMatch && method === 'GET') return operationalPeriodListResponse();

  const searchAreaIdMatch = path.match(/^\/search-areas\/([^/]+)$/);
  if (searchAreaIdMatch && method === 'PATCH') return updateSearchAreaResponse(searchAreaIdMatch[1], body);

  const splitMatch = path.match(/^\/search-areas\/([^/]+)\/split$/);
  if (splitMatch && method === 'POST') return splitSearchAreaResponse(splitMatch[1]);

  const assignmentMatch = path.match(/^\/search-areas\/([^/]+)\/assignments$/);
  if (assignmentMatch && method === 'POST') return assignSearchAreaResponse(assignmentMatch[1], body);

  const summaryMatch = path.match(/^\/operational-periods\/([^/]+)\/search-history-summaries$/);
  if (summaryMatch && method === 'GET') return searchHistorySummaryResponse(summaryMatch[1]);

  const markerMatch = path.match(/^\/markers\/([^/]+)$/);
  if (markerMatch && (method === 'PATCH' || method === 'DELETE')) {
    return { id: decodeURIComponent(markerMatch[1]), status: method === 'DELETE' ? 'DELETED' : 'ACTIVE', version: 2 };
  }

  const segmentMatch = path.match(/^\/search-path-segments\/([^/]+)$/);
  if (segmentMatch && method === 'PATCH') {
    return {
      id: decodeURIComponent(segmentMatch[1]),
      movementType: readBodyString(body, 'movementType') ?? 'FOOT',
      movementTypeSource: 'MANUAL',
      opId: OP_ID,
      policePhoneId: '00000000-0000-0000-0000-000000000102',
      correctedByAccountId: defaultAccount.id,
      correctedAt: SERVER_TS,
      version: 2,
    };
  }

  return {};
}

function loginResponse(body: unknown) {
  const accountCode = readBodyString(body, 'accountCode');
  const account = accounts.find((item) => item.accountCode === accountCode || item.id === accountCode) ?? defaultAccount;
  return {
    sessionId: `mock-session-${account.accountCode}`,
    accessToken: `mock-access-token-${account.accountCode}`,
    securityContext: {
      accountId: account.id,
      accountType: account.accountType,
      organizationType: account.organizationType,
      channel: 'WEB',
      policePhoneId: null,
      authorities: account.authorities,
    },
  };
}

function incidentListResponse() {
  return {
    items: [
      {
        id: INCIDENT_ID,
        incidentId: INCIDENT_ID,
        title: '종로구 인왕산 실종 신고',
        status: 'OPEN',
        version: 1,
        closedAt: null,
      },
    ],
  };
}

function importIncidentResponse() {
  return {
    id: INCIDENT_ID,
    incidentId: INCIDENT_ID,
    status: 'OPEN',
    version: 1,
    assignmentAccountIds: accounts.map((account) => account.id),
  };
}

function incidentDetailResponse(incidentId: string) {
  return {
    id: incidentId,
    incidentId,
    status: 'OPEN',
    version: 1,
    missingPerson: {
      incidentId,
      displayName: '가상 실종자 001',
      photoObjectKey: 'mock-112/missing-person/mock-112-incident-001.jpg',
      appearanceText: '남색 점퍼, 회색 등산화',
      lastSeenLocationText: '인왕산 북측 산책로 입구',
      lastSeenAt: '2026-04-28T08:30:00+09:00',
    },
    assignments: accounts.map((account) => ({
      accountId: account.id,
      incidentRole: account.authorities.includes('MISSING_TEAM_COMMANDER') ? 'INCIDENT_COMMANDER' : 'MEMBER',
    })),
  };
}

function boardResponse(incidentId: string) {
  const slots = {
    overall_search_area: { ...overallArea, incidentId },
    area: areaRows.map((row) => ({ ...row, incidentId })),
    path: pathRows.map((row) => ({ ...row, incidentId })),
    marker: markerRows.map((row) => ({ ...row, incidentId })),
    police_phone_freshness: [],
    package_badge: packageBadgeRows.map((row) => ({ ...row, incidentId })),
    op_toggle: [
      {
        id: OP_ID,
        status: 'ACTIVE',
        version: 1,
        sequence: 9,
        sourceSpec: 'S8',
        sourceHash: 'mock-op-v1',
        latestEventId: 'mock-event-op-001',
        opId: OP_ID,
        sequenceNumber: 1,
        reason: 'INITIAL',
        startedAt: '2026-05-14T09:00:00+09:00',
      },
    ],
    handover_memo: [],
    search_history_summary: [],
    incident_terminal: null,
  };
  return {
    incidentId,
    boardResponseVersion: 1,
    serverTs: SERVER_TS,
    activeOpId: OP_ID,
    selectedOpIds: [OP_ID],
    geometryHash: 'mock-board-geometry-v1',
    slots,
    slotSources: {},
    sourceVersions: {},
    sourceHashes: {},
  };
}

function searchAreasResponse(query?: ApiQuery) {
  const incidentId = stringQuery(query, 'incidentId') ?? INCIDENT_ID;
  const areas = [overallArea, ...areaRows].map((row) => ({ ...row, incidentId }));
  if (stringQuery(query, 'areaLevel') === 'OVERALL' && stringQuery(query, 'status') === 'ACTIVE') {
    return { ...overallArea, incidentId };
  }
  return {
    incidentId,
    sourceVersion: 1,
    areas,
  };
}

function createSearchAreaResponse(body: unknown) {
  return {
    id: `mock-area-${Date.now()}`,
    incidentId: readBodyString(body, 'incidentId') ?? INCIDENT_ID,
    opId: readBodyString(body, 'opId') ?? OP_ID,
    areaLevel: readBodyString(body, 'areaLevel') ?? 'TEAM',
    status: 'ACTIVE',
    historyCount: 1,
    version: 1,
    geometry: readBodyValue(body, 'geometry') ?? teamAreaNorth.geometry,
  };
}

function updateSearchAreaResponse(searchAreaId: string, body: unknown) {
  return {
    ...teamAreaNorth,
    id: decodeURIComponent(searchAreaId),
    status: readBodyString(body, 'nextStatus') ?? 'ACTIVE',
    version: 2,
    historyCount: 2,
    geometry: readBodyValue(body, 'geometry') ?? teamAreaNorth.geometry,
  };
}

function splitSearchAreaResponse(searchAreaId: string) {
  return {
    parentAreaId: decodeURIComponent(searchAreaId),
    parent: { ...unitArea, id: decodeURIComponent(searchAreaId), status: 'CANCELLED', version: 2 },
    createdAreaIds: [teamAreaNorth.id, teamAreaSouth.id],
    children: [teamAreaNorth, teamAreaSouth],
  };
}

function assignSearchAreaResponse(searchAreaId: string, body: unknown) {
  return {
    searchAreaId: decodeURIComponent(searchAreaId),
    opId: readBodyString(body, 'opId') ?? OP_ID,
    assignmentIds: ['mock-search-area-assignment-001'],
    version: 2,
  };
}

function operationalPeriodListResponse() {
  return {
    currentOpId: OP_ID,
    items: [
      {
        id: OP_ID,
        status: 'ACTIVE',
        reason: 'INITIAL',
        sequenceNumber: 1,
      },
    ],
  };
}

function createOperationalPeriodResponse(body: unknown) {
  return {
    id: 'op-precinct-001-op2',
    incidentId: readBodyString(body, 'incidentId') ?? INCIDENT_ID,
    status: 'ACTIVE',
    reason: readBodyString(body, 'reason') ?? 'RE_SEARCH',
    version: 1,
    sequenceNumber: 2,
  };
}

function handoverMemoListResponse() {
  return {
    items: [
      {
        id: 'memo-precinct-handover-001',
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        memoTargetType: 'OPERATIONAL_PERIOD',
        memoTargetId: OP_ID,
        content: '북측 능선과 남측 산책로를 나누어 수색 진행 중',
        createdByAccountId: defaultAccount.id,
        createdAt: SERVER_TS,
        version: 1,
      },
    ],
  };
}

function createHandoverMemoResponse(body: unknown) {
  return {
    id: `mock-memo-${Date.now()}`,
    opId: readBodyString(body, 'opId') ?? OP_ID,
    version: 1,
    memoTargetType: readBodyString(body, 'memoTargetType') ?? 'OPERATIONAL_PERIOD',
    memoTargetId: readBodyString(body, 'memoTargetId') ?? OP_ID,
  };
}

function searchHistorySummaryResponse(operationalPeriodId: string) {
  return {
    items: [
      {
        summaryId: 'summary-precinct-op1-001',
        opId: decodeURIComponent(operationalPeriodId),
        scopeType: 'OP',
        scopeId: decodeURIComponent(operationalPeriodId),
        status: 'READY',
        displayStatus: 'READY',
        content: 'OP1 수색 구역 2개 팀 배정, 경로 2건, 마커 2건 확인',
        sourceReadiness: 'READY',
        sourceHash: 'mock-summary-v1',
        generatedAt: SERVER_TS,
        version: 1,
      },
    ],
  };
}

function normalizePath(path: string) {
  const rawPath = path.startsWith('http') ? new URL(path).pathname : path;
  const withoutApiPrefix = rawPath.startsWith('/api/') ? rawPath.slice('/api'.length) : rawPath;
  return withoutApiPrefix.replace(/\/+$/, '') || '/';
}

function clone(value: unknown) {
  return JSON.parse(JSON.stringify(value)) as unknown;
}

function readBodyString(body: unknown, key: string) {
  const value = readBodyValue(body, key);
  return typeof value === 'string' ? value : null;
}

function readBodyValue(body: unknown, key: string) {
  if (typeof body !== 'object' || body === null || !(key in body)) return null;
  return (body as Record<string, unknown>)[key];
}

function stringQuery(query: ApiQuery | undefined, key: string) {
  const value = query?.[key];
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : undefined;
  return typeof value === 'string' ? value : undefined;
}
