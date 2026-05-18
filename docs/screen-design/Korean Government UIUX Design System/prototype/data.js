// =========================================================================
// Suri-Map — mock data
// 도심 외곽 / 논밭 시나리오. 가상의 시흥-김포 외곽 농지 + 하천변.
// Coordinates are in a synthetic local frame (the SVG map's viewBox).
// All times use a fixed "now" so the prototype is reproducible.
// =========================================================================

window.SM_DATA = (function () {
  // Fixed clock for the prototype: 2026-05-04 16:42
  const NOW = new Date('2026-05-04T16:42:00+09:00');

  const minutesAgo = (m) => {
    const d = new Date(NOW.getTime() - m * 60_000);
    return d;
  };
  const fmtTime = (d) => {
    const h = d.getHours().toString().padStart(2, '0');
    const m = d.getMinutes().toString().padStart(2, '0');
    return `${h}:${m}`;
  };
  const fmtRel = (d) => {
    const diff = Math.floor((NOW - d) / 60_000);
    if (diff < 1) return '방금';
    if (diff < 60) return `${diff}분 전`;
    const h = Math.floor(diff / 60);
    if (h < 24) return `${h}시간 전`;
    return `${Math.floor(h / 24)}일 전`;
  };

  // Map viewBox: 0..1000 wide, 0..650 tall
  // The "center" is roughly a rural intersection; farmland on east, river at south, low hills west.

  const incident = {
    id: 'INC-2026-0428-031',
    title: '시흥시 매화동 외곽 실종 사건',
    importedAt: minutesAgo(7 * 60 + 32),  // 7시간 32분 전
    importedAtLabel: '오늘 09:10',
    status: 'IN_PROGRESS',
    sourceSystem: '실종프로파일링 (mock)',
    address: '경기도 시흥시 매화동 일대 농경지·매화천변',
    missingPerson: {
      name: '김OO',
      age: 73,
      sex: 'M',
      photo: null,                     // placeholder rendered as initials
      appearance: '카키색 점퍼, 검정 모자, 회색 운동화. 키 165cm 가량, 마른 체형. 치매 기왕력.',
      lastSeen: '오늘 08:40, 매화동 마을회관 앞 (가족 신고)',
      reportedBy: '가족 (장남)',
      cctvLastSeen: '08:48 매화초등학교 앞 사거리 CCTV',
    },
    commanders: ['실종팀 1팀장 박OO', '시흥경찰서 매화지구대 당직 이OO'],
  };

  // ─── Operational Periods ────────────────────────────────────────────────
  const ops = [
    {
      id: 'OP1',
      label: 'OP 1차',
      startedAt: minutesAgo(7 * 60 + 30),
      startedAtLabel: '오늘 09:12',
      endedAt: minutesAgo(2 * 60 + 50),
      endedAtLabel: '오늘 13:52',
      reason: 'AUTO',                       // 사건 가져오기 시 자동 생성
      reasonLabel: '사건 가져오기 자동 생성',
      status: 'CLOSED',
      openedBy: '시스템 (사건 import)',
      summary: '매화초 사거리 → 매화천변 농로 일대 1차 도보·차량 병행 수색. CCTV 마지막 포착 지점 중심 1km 반경.',
    },
    {
      id: 'OP2',
      label: 'OP 2차',
      startedAt: minutesAgo(2 * 60 + 45),
      startedAtLabel: '오늘 13:57',
      endedAt: null,
      endedAtLabel: '진행 중',
      reason: 'RE_SEARCH',
      reasonLabel: '재수색 (CCTV 공백 구간 재확인)',
      status: 'ACTIVE',
      openedBy: '실종팀 1팀장 박OO',
      summary: null,
    },
  ];

  // ─── Search Areas (polygons, in viewBox coordinates) ───────────────────
  // Each polygon is a list of [x, y] points. parent = OVERALL > UNIT > TEAM
  const areas = [
    {
      id: 'AREA-OVERALL',
      level: 'OVERALL',
      name: '전체 수색 범위',
      op: 'ALL',
      status: 'ACTIVE',
      polygon: [[120, 90], [880, 110], [900, 540], [110, 560]],
      assignedTo: null,
    },
    // OP1 areas
    {
      id: 'AREA-OP1-A',
      level: 'TEAM',
      op: 'OP1',
      name: 'A구역 — 매화초 사거리·마을',
      status: 'COMPLETED',
      completedAt: minutesAgo(4 * 60 + 10),
      completedAtLabel: '11:32',
      completedBy: '실종팀 1팀장 박OO',
      polygon: [[180, 150], [430, 160], [445, 320], [200, 310]],
      assignedTo: '매화지구대 1팀 (순찰차 시흥1-3호)',
      historyCount: 1,
    },
    {
      id: 'AREA-OP1-B',
      level: 'TEAM',
      op: 'OP1',
      name: 'B구역 — 매화천 북측 농로',
      status: 'COMPLETED',
      completedAt: minutesAgo(3 * 60 + 22),
      completedAtLabel: '13:20',
      completedBy: '시흥경찰서 매화지구대 이OO',
      polygon: [[450, 200], [710, 215], [700, 360], [445, 345]],
      assignedTo: '실종팀 1팀 (팀 업무폰)',
      historyCount: 1,
    },
    // OP2 (active) areas
    {
      id: 'AREA-OP2-C',
      level: 'TEAM',
      op: 'OP2',
      name: 'C구역 — 매화천 남측 둑길·갈대밭',
      status: 'ACTIVE',
      polygon: [[300, 360], [700, 380], [710, 510], [310, 500]],
      assignedTo: '기동대 3제대 (도보)',
      historyCount: 0,
      noteFlag: 'CCTV 공백, 1차 미확인 구간',
    },
    {
      id: 'AREA-OP2-D',
      level: 'TEAM',
      op: 'OP2',
      name: 'D구역 — 비닐하우스 단지',
      status: 'ACTIVE',
      polygon: [[720, 230], [870, 245], [870, 410], [720, 400]],
      assignedTo: '실종팀 2팀 (팀 업무폰)',
      historyCount: 0,
    },
  ];

  // ─── Search paths (polylines) ──────────────────────────────────────────
  // segmentType: VEHICLE / WALK
  // device: which 폴리폰; deviceClass: TEAM_PHONE | PATROL_CAR
  const paths = [
    // ── OP1 ──────────────────────────────────────────────────────────────
    {
      id: 'PATH-OP1-1',
      op: 'OP1',
      device: '시흥1-3호 순찰차',
      deviceClass: 'PATROL_CAR',
      account: '매화지구대 1팀',
      segments: [
        { type: 'VEHICLE', points: [[260,200],[300,210],[350,220],[400,230],[420,250],[410,290],[380,310],[330,300]] },
      ],
    },
    {
      id: 'PATH-OP1-2',
      op: 'OP1',
      device: '실종팀-A 업무폰',
      deviceClass: 'TEAM_PHONE',
      account: '실종팀 1팀',
      segments: [
        { type: 'VEHICLE', points: [[240,180],[300,190],[420,205],[500,215],[560,220]] },
        { type: 'WALK',    points: [[560,220],[580,240],[610,260],[640,280],[660,300],[640,320],[600,330],[560,320],[520,310]] },
      ],
    },
    // ── OP2 (active) ─────────────────────────────────────────────────────
    {
      id: 'PATH-OP2-1',
      op: 'OP2',
      device: '기동대-3 업무폰',
      deviceClass: 'TEAM_PHONE',
      account: '기동대 3제대',
      isMine: true,                 // for FR-25 demo (operator's own device highlighted)
      segments: [
        { type: 'WALK', points: [[380,400],[420,410],[460,420],[490,440],[510,460],[540,470],[580,475],[610,470]] },
      ],
      lastSync: minutesAgo(0.4),    // ≈ now
      lastSyncLabel: '방금',
      health: 'OK',
    },
    {
      id: 'PATH-OP2-2',
      op: 'OP2',
      device: '실종팀-B 업무폰',
      deviceClass: 'TEAM_PHONE',
      account: '실종팀 2팀',
      segments: [
        { type: 'VEHICLE', points: [[600,280],[680,260],[750,265],[800,280]] },
        { type: 'WALK',    points: [[800,280],[810,310],[820,340],[825,375]] },
      ],
      lastSync: minutesAgo(2),
      lastSyncLabel: '2분 전',
      health: 'OK',
    },
    {
      id: 'PATH-OP2-3',
      op: 'OP2',
      device: '시흥1-7호 순찰차',
      deviceClass: 'PATROL_CAR',
      account: '매화지구대 2팀',
      segments: [
        { type: 'VEHICLE', points: [[150,300],[180,350],[230,400],[280,440],[330,470]] },
      ],
      lastSync: minutesAgo(12),
      lastSyncLabel: '12분 전',
      health: 'STALE',              // FR-24 stale demo
    },
  ];

  // ─── Markers ───────────────────────────────────────────────────────────
  const markers = [
    {
      id: 'M-001',
      type: 'CLUE',
      typeLabel: '단서',
      origin: 'system',
      title: 'CCTV 마지막 포착',
      memo: '08:48 매화초등학교 앞 사거리 CCTV. 카키색 점퍼·검정 모자.',
      pos: [305, 215],
      createdAt: minutesAgo(7 * 60 + 30),
      createdAtLabel: '09:12',
      reportedBy: '시스템 (mock·seed)',
      photos: 1,
      op: 'OP1',
    },
    {
      id: 'M-002',
      type: 'CLUE',
      typeLabel: '단서',
      origin: 'user',
      title: '지팡이 발견',
      memo: '농로 가장자리에서 회색 지팡이 1개. 실종자 가족이 본인 것으로 확인.',
      pos: [555, 280],
      createdAt: minutesAgo(4 * 60 + 50),
      createdAtLabel: '10:52',
      reportedBy: '매화지구대 1팀 / 시흥1-3호 순찰차',
      photos: 3,
      op: 'OP1',
    },
    {
      id: 'M-003',
      type: 'NOTE',
      typeLabel: '운영 NOTE',
      origin: 'user',
      title: '갈대밭 시야 불량 — 재확인 필요',
      memo: '둑길 남측 갈대 1.5m 이상. 주간 도보 1회 확인했으나 시야 불량으로 재확인 필요.',
      pos: [510, 450],
      createdAt: minutesAgo(3 * 60 + 5),
      createdAtLabel: '13:37',
      reportedBy: '실종팀 1팀',
      photos: 0,
      op: 'OP1',
    },
    {
      id: 'M-004',
      type: 'FIELD_CONDITION',
      typeLabel: '지형 상태',
      origin: 'user',
      title: '진입 곤란 — 침수 농로',
      memo: '간밤 호우로 농로 약 30m 침수. 차량 진입 불가, 도보 우회 필요.',
      pos: [440, 380],
      createdAt: minutesAgo(60 + 50),
      createdAtLabel: '14:52',
      reportedBy: '기동대 3제대',
      photos: 2,
      op: 'OP2',
    },
    {
      id: 'M-005',
      type: 'SUPPORT_REQUEST',
      typeLabel: '지원 요청',
      origin: 'user',
      requestType: 'POLICE_DOG',
      requestTypeLabel: '경찰견',
      title: '경찰견 지원 요청',
      memo: '갈대밭 시야 불량 구간 추적 보조. 무전으로 본부 전달 완료.',
      pos: [490, 460],
      createdAt: minutesAgo(35),
      createdAtLabel: '16:07',
      reportedBy: '기동대 3제대',
      photos: 0,
      op: 'OP2',
    },
    {
      id: 'M-006',
      type: 'NOTE',
      typeLabel: '운영 NOTE',
      origin: 'user',
      title: '비닐하우스 출입문 잠김',
      memo: '비닐하우스 단지 동측 출입문 4개소 잠김 확인. 주인 연락 필요.',
      pos: [790, 320],
      createdAt: minutesAgo(15),
      createdAtLabel: '16:27',
      reportedBy: '실종팀 2팀',
      photos: 1,
      op: 'OP2',
    },
  ];

  // ─── Other incidents (for the list screen) ─────────────────────────────
  const allIncidents = [
    incident,
    {
      id: 'INC-2026-0501-009',
      title: '안산시 단원구 화랑유원지 실종 사건',
      status: 'IN_PROGRESS',
      importedAtLabel: '5월 3일 14:20',
      missingPerson: { name: '이OO', age: 8, sex: 'F', appearance: '분홍 점퍼, 흰 운동화' },
      address: '경기도 안산시 단원구 화랑유원지 일대',
      ops: 1,
      assignedAccountCount: 4,
    },
    {
      id: 'INC-2026-0429-018',
      title: '북한산 둘레길 실종 사건',
      status: 'CLOSED',
      importedAtLabel: '4월 29일 06:50',
      closedAtLabel: '4월 30일 11:14',
      missingPerson: { name: '정OO', age: 58, sex: 'M', appearance: '등산복' },
      address: '서울특별시 강북구 북한산 둘레길 5코스',
      ops: 3,
      assignedAccountCount: 7,
    },
    {
      id: 'INC-2026-0426-004',
      title: '한강공원 반포지구 실종 사건',
      status: 'CLOSED',
      importedAtLabel: '4월 26일 21:08',
      closedAtLabel: '4월 27일 02:35',
      missingPerson: { name: '최OO', age: 24, sex: 'F', appearance: '검정 코트' },
      address: '서울특별시 서초구 반포한강공원',
      ops: 1,
      assignedAccountCount: 3,
    },
  ];

  // ─── Outbox queue (offline demo) ───────────────────────────────────────
  const outbox = [
    { id: 'q-1', kind: 'GPS_BATCH', label: 'GPS 포인트', count: 47, retry: 2, status: 'PENDING' },
    { id: 'q-2', kind: 'MARKER',    label: '마커 — 비닐하우스 출입문 잠김', count: 1, retry: 0, status: 'SENDING' },
    { id: 'q-3', kind: 'PHOTO',     label: '사진 첨부 (지팡이 발견)', count: 3, retry: 1, status: 'PENDING' },
    { id: 'q-4', kind: 'MEMO',      label: '인수인계 메모', count: 1, retry: 0, status: 'PENDING' },
  ];

  // ─── Handover memos ────────────────────────────────────────────────────
  const memos = [
    {
      id: 'memo-1', op: 'OP1', scope: 'AREA', scopeLabel: 'B구역',
      author: '실종팀 1팀',
      createdAtLabel: '13:18',
      text: '북측 농로 큰길은 도보·차량 모두 1회씩 확인. 농로 옆 배수로는 시야가 닿지 않아 NOTE로 남김.',
    },
    {
      id: 'memo-2', op: 'OP1', scope: 'OP', scopeLabel: 'OP1차 마감',
      author: '매화지구대 이OO',
      createdAtLabel: '13:50',
      text: 'CCTV 마지막 포착 사거리 → 매화천 북측까지 1차 수색 완료. 남측 둑길·갈대밭은 시야 불량으로 OP2 재확인 필요.',
    },
    {
      id: 'memo-3', op: 'OP2', scope: 'AREA', scopeLabel: 'C구역',
      author: '기동대 3제대',
      createdAtLabel: '14:52',
      text: '침수 농로 인접 구간은 도보로만 진입. 갈대 1.5m 이상이라 1열 정렬로 천천히 이동 중.',
    },
  ];

  // ─── Tile package status (offline demo) ────────────────────────────────
  const offlinePackage = {
    incidentId: incident.id,
    items: [
      { id: 'meta',     label: '사건 메타 정보',       size: '12 KB',  status: 'DONE' },
      { id: 'person',   label: '실종자 기본 정보',     size: '38 KB',  status: 'DONE' },
      { id: 'op',       label: 'OP·구역 데이터',       size: '156 KB', status: 'DONE' },
      { id: 'markers',  label: '초기 단서 마커',       size: '24 KB',  status: 'DONE' },
      { id: 'tiles_z14', label: '지도 타일 (z14, 광역)', size: '4.2 MB',  status: 'DONE' },
      { id: 'tiles_z15', label: '지도 타일 (z15)',       size: '12.8 MB', status: 'DONE' },
      { id: 'tiles_z16', label: '지도 타일 (z16, 상세)', size: '38.4 MB', status: 'IN_PROGRESS', progress: 0.62 },
      { id: 'tiles_z17', label: '지도 타일 (z17, 골목)', size: '56.0 MB', status: 'PENDING' },
    ],
    totalSize: '111.8 MB',
    transferred: '69.3 MB',
  };

  return {
    NOW, fmtTime, fmtRel, minutesAgo,
    incident, allIncidents,
    ops, areas, paths, markers, outbox, memos,
    offlinePackage,
  };
})();
