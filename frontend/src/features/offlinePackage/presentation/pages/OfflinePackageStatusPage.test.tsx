import { render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { getIncidentDetail } from '../../../situationBoard/data/getIncidentDetail';
import { useOfflinePackageManifestQuery } from '../../api/offlinePackageApi';
import type { OfflinePackageManifestResponse } from '../../api/offlinePackageApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { ApiHttpError } from '../../../../shared/api';
import { OfflinePackageStatusPage } from './OfflinePackageStatusPage';

vi.mock('../../../board/api/incidentBoardApi', () => ({
  useIncidentBoardQuery: vi.fn(),
}));

vi.mock('../../api/offlinePackageApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/offlinePackageApi')>()),
  useOfflinePackageManifestQuery: vi.fn(),
}));

vi.mock('../../../situationBoard/data/getIncidentDetail', () => ({
  getIncidentDetail: vi.fn(),
}));

describe('OfflinePackageStatusPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getIncidentDetail).mockResolvedValue({
      id: 'inc-precinct-first-001',
      incidentId: 'inc-precinct-first-001',
      title: '광산구 실종 신고',
      status: 'OPEN',
      openedAt: '2026-05-14T00:30:00Z',
      version: 3,
      missingPerson: {
        incidentId: 'inc-precinct-first-001',
        displayName: '홍길동',
        photoObjectKey: 'missing-person/photo.jpg',
        photoUrl: '/mock-upload/missing-person/photo.jpg',
        appearanceText: '검은 상의',
        lastSeenLocationText: '광산구',
        lastSeenAt: '2026-05-14T01:00:00Z',
      },
      assignments: [
        {
          accountId: 'acct-001',
          accountDisplayName: '상황실',
          accountType: 'COMMAND',
          organizationType: 'POLICE_SUBSTATION',
          incidentRole: 'INCIDENT_COMMANDER',
          assignedAt: '2026-05-14T00:30:00Z',
        },
      ],
    });
    vi.mocked(useIncidentBoardQuery).mockReturnValue(boardQueryResult());
    vi.mocked(useOfflinePackageManifestQuery).mockReturnValue(manifestQueryResult(offlinePackageManifest()));
  });

  test('renders the load gauge using the loaded police phone ratio', async () => {
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResultWithRows([
        packageBadgeRow('pkg-ready-1', 'phone-1', '단말 1', 'READY', true, false),
        packageBadgeRow('pkg-ready-2', 'phone-2', '단말 2', 'READY', true, false),
        packageBadgeRow('pkg-ready-3', 'phone-3', '단말 3', 'READY', true, false),
        packageBadgeRow('pkg-stale', 'phone-4', '단말 4', 'STALE', false, true),
      ]),
    );

    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getByText('필수 패키지 전체 적재율')).toBeInTheDocument();
    expect(screen.getByText('전 폴리폰 기준')).toBeInTheDocument();
    expect(screen.getByText('75%')).toBeInTheDocument();
    expect(screen.getByText('3 / 4대 적재')).toBeInTheDocument();
  });

  test('renders package composition with operator-facing labels and tile summary', async () => {
    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    for (const label of [
      '사건 정보',
      '실종자 정보',
      '작전 차수 정보',
      '배정 수색 구역',
      '초기 마커',
      '전체 수색 구역',
      '오프라인 지도',
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }

    expect(screen.getByText('아직 배정된 수색 구역이 없습니다')).toBeInTheDocument();

    const tileSummary = screen.getByLabelText('타일 목록 요약');
    expect(within(tileSummary).getByText('2개')).toBeInTheDocument();
    expect(within(tileSummary).getByText('3.0 KiB')).toBeInTheDocument();
    expect(within(tileSummary).getByText('osm-local')).toBeInTheDocument();

    const pageText = document.body.textContent ?? '';
    expect(pageText).not.toContain('INCIDENT_META');
    expect(pageText).not.toContain('packageItems');
    expect(pageText).not.toContain('대표 itemKey');
    expect(pageText).not.toContain('sourceHash');
    expect(pageText).not.toContain('패키지 Hash');
  });

  test('keeps package_badge device statuses visible', async () => {
    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getByText('오프라인 사용 가능')).toBeInTheDocument();
    expect(screen.getByText('필수 자료 설치 완료')).toBeInTheDocument();
    expect(screen.getByText('자동 설치 중')).toBeInTheDocument();
    expect(screen.getByText('앱에서 자동 설치 중')).toBeInTheDocument();
    expect(screen.getByText('완료될 때까지 대기')).toBeInTheDocument();
    expect(screen.getByText('업데이트 필요')).toBeInTheDocument();
    expect(screen.getByText('앱 연결 시 최신 패키지 갱신')).toBeInTheDocument();
    expect(screen.getByText('자동 설치 실패')).toBeInTheDocument();
    expect(screen.getByText('단말 네트워크 확인 또는 수동 재시도 필요')).toBeInTheDocument();
    expect(screen.getByText('실패')).toBeInTheDocument();
    expect(screen.getByText('삭제됨')).toBeInTheDocument();
  });

  test('keeps package_badge device statuses visible when the slot is a single object', async () => {
    const result = boardQueryResult();
    result.data!.slots.package_badge = packageBadgeRow('pkg-single', 'phone-single', 'single-device', 'READY', true, false);
    vi.mocked(useIncidentBoardQuery).mockReturnValue(result);

    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getAllByText('single-device')).toHaveLength(2);
  });

  test('keeps package_badge rows visible when a refetch response omits them', async () => {
    const initialBoardQuery = boardQueryResult();
    const refetchedBoardQuery = {
      ...initialBoardQuery,
      data: {
        ...initialBoardQuery.data!,
        slots: {},
      },
    };
    vi.mocked(useIncidentBoardQuery).mockReturnValue(initialBoardQuery);

    const { rerender } = renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getByText('오프라인 사용 가능')).toBeInTheDocument();
    expect(screen.getByText('필수 자료 설치 완료')).toBeInTheDocument();

    vi.mocked(useIncidentBoardQuery).mockReturnValue(refetchedBoardQuery as ReturnType<typeof useIncidentBoardQuery>);

    rerender(<OfflinePackageStatusPage {...offlinePackageStatusPageProps()} />);

    expect(screen.getByText('광산구 실종 신고')).toBeInTheDocument();
  });

  test('does not render a board snapshot from another incident', async () => {
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResultWithRows(
        [packageBadgeRow('pkg-mismatch', 'phone-mismatch', 'should-not-render', 'READY', true, false)],
        'inc-other-001',
      ),
    );

    renderOfflinePackageStatusPage();

    expect(screen.queryByText('should-not-render')).not.toBeInTheDocument();
  });

  test('keeps the header stable when incident assignments are missing', async () => {
    vi.mocked(getIncidentDetail).mockResolvedValue({
      id: 'inc-precinct-first-001',
      incidentId: 'inc-precinct-first-001',
      title: 'incident title',
      status: 'OPEN',
      openedAt: '2026-05-14T00:30:00Z',
      version: 3,
      missingPerson: null,
    } as unknown as Awaited<ReturnType<typeof getIncidentDetail>>);

    renderOfflinePackageStatusPage();
    await screen.findByText('배정 없음');

    expect(screen.getByText('배정 없음')).toBeInTheDocument();
  });

  test('renders when manifest list fields are missing', async () => {
    vi.mocked(useOfflinePackageManifestQuery).mockReturnValue(
      manifestQueryResult(
        {
          ...offlinePackageManifest(),
          operationalPeriods: undefined as unknown as OfflinePackageManifestResponse['operationalPeriods'],
          assignedAreas: undefined as unknown as OfflinePackageManifestResponse['assignedAreas'],
          initialMarkers: undefined as unknown as OfflinePackageManifestResponse['initialMarkers'],
          tileItems: undefined as unknown as OfflinePackageManifestResponse['tileItems'],
          packageItems: undefined as unknown as OfflinePackageManifestResponse['packageItems'],
        },
        { isLoading: false, isError: false, refetch: vi.fn() },
      ),
    );

    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getAllByText('0개').length).toBeGreaterThan(0);
  });

  test('shows only the manifest section failure when manifest API fails', async () => {
    vi.mocked(useOfflinePackageManifestQuery).mockReturnValue(
      manifestQueryResult(undefined, { isError: true, refetch: vi.fn() }),
    );

    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    expect(screen.getAllByText('수완지구대 지휘 단말')).toHaveLength(2);
    expect(screen.getByText('패키지 구성 목록을 불러오지 못했습니다.')).toBeInTheDocument();
    expect(screen.queryByText('단말별 적재 상태를 불러오지 못했습니다.')).not.toBeInTheDocument();
  });

  test('explains when package manifest prerequisites are not ready', async () => {
    vi.mocked(useOfflinePackageManifestQuery).mockReturnValue(
      manifestQueryResult(undefined, {
        error: new ApiHttpError(409, 'package_manifest_not_ready', { error: 'package_manifest_not_ready' }),
        isError: true,
        refetch: vi.fn(),
      }),
    );

    renderOfflinePackageStatusPage();
    await screen.findByText('오프라인 패키지를 아직 만들 수 없습니다.');

    expect(screen.getByText('현재 사건에 OP 또는 전체 수색 구역이 준비되지 않았습니다. 전체 수색 구역을 저장한 뒤 다시 조회하세요.')).toBeInTheDocument();
    expect(screen.getByText('단말별 적재 상태')).toBeInTheDocument();
    expect(screen.getAllByText('수완지구대 지휘 단말')).toHaveLength(2);
  });
});

function renderOfflinePackageStatusPage() {
  return render(
    <OfflinePackageStatusPage {...offlinePackageStatusPageProps()} />,
  );
}

function offlinePackageStatusPageProps() {
  return {
    currentUserAccount: currentUserAccount(),
    incidentId: 'inc-precinct-first-001',
    markerNotificationIndex: 0,
    markerNotifications: [],
    onBackToSituationBoard: vi.fn(),
    onCloseMarkerNotifications: vi.fn(),
    onMoveMarkerNotification: vi.fn(),
    onOpenHandover: vi.fn(),
    onOpenIncidentList: vi.fn(),
    onOpenOfflinePackage: vi.fn(),
  } satisfies Parameters<typeof OfflinePackageStatusPage>[0];
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-001',
    name: '광주광산경찰서 수완지구대 경위 김도현',
    organization: '광주광산경찰서 수완지구대',
    rank: '경위',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: '상황판 계정',
  };
}

function boardQueryResult() {
  return boardQueryResultWithRows(defaultPackageBadgeRows());
}

function boardQueryResultWithRows(
  packageBadgeRows: ReturnType<typeof packageBadgeRow>[],
  incidentId = 'inc-precinct-first-001',
) {
  return {
    data: {
      incidentId,
      boardResponseVersion: 7,
      serverTs: '2026-05-14T02:00:00Z',
      activeOpId: 'op-001',
      selectedOpIds: ['op-001'],
      geometryHash: 'hash-geometry',
      slots: {
        package_badge: packageBadgeRows,
      },
      slotSources: {},
      sourceVersions: {},
      sourceHashes: {},
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    } as unknown as ReturnType<typeof useIncidentBoardQuery>;
}

function defaultPackageBadgeRows() {
  return [
    packageBadgeRow('pkg-ready', 'phone-ready', '수완지구대 지휘 단말', 'READY', true, false),
    packageBadgeRow('pkg-downloading', 'phone-downloading', '자동설치 단말', 'DOWNLOADING', false, true),
    packageBadgeRow('pkg-stale', 'phone-stale', '수색1 단말', 'STALE', false, true),
    packageBadgeRow('pkg-failed', 'phone-failed', '수색2 단말', 'FAILED', false, true),
    packageBadgeRow('pkg-purged', 'phone-purged', '종료 단말', 'PURGED', false, false),
  ];
}

function packageBadgeRow(
  id: string,
  policePhoneId: string,
  accountName: string,
  packageStatus: string,
  readyForOfflineUse: boolean,
  warningRaised: boolean,
) {
  return {
    id,
    status: 'ACTIVE',
    version: 1,
    sequence: 1,
    sourceSpec: 'S7',
    sourceHash: `hash-${id}`,
    latestEventId: `evt-${id}`,
    incidentId: 'inc-precinct-first-001',
    policePhoneId,
    policePhoneCode: policePhoneId,
    policePhoneName: accountName,
    accountId: `acct-${policePhoneId}`,
    accountName,
    accountType: 'TEAM',
    organizationType: 'POLICE',
    incidentRole: 'MEMBER',
    packageStatus,
    manifestVersion: packageStatus === 'PURGED' ? null : 1,
    readyForOfflineUse,
    localWarningInput: {
      raised: warningRaised,
      reason: packageStatus === 'STALE' ? 'manifest_stale' : 'package_incomplete',
      activeManifestVersion: packageStatus === 'STALE' ? 2 : 1,
    },
  };
}

function manifestQueryResult(
  data: OfflinePackageManifestResponse | undefined,
  overrides: Partial<ReturnType<typeof useOfflinePackageManifestQuery>> = {},
) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useOfflinePackageManifestQuery>;
}

function offlinePackageManifest(): OfflinePackageManifestResponse {
  return {
    manifestId: 'manifest-001',
    incidentId: 'inc-precinct-first-001',
    manifestVersion: 4,
    expiresAt: '2026-05-14T12:00:00Z',
    packageHash: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    policePhoneContext: null,
    incident: {
      incidentId: 'inc-precinct-first-001',
      status: 'OPEN',
      packageContext: 'CURRENT',
      sourceFixture: null,
    },
    missingPerson: {
      incidentId: 'inc-precinct-first-001',
      displayName: '홍길동',
      photoObjectKey: null,
      appearanceText: null,
      lastSeenLocationText: '광산구',
      lastSeenAt: '2026-05-14T01:00:00Z',
    },
    operationalPeriods: [
      {
        opId: 'op-001',
        incidentId: 'inc-precinct-first-001',
        sequenceNumber: 1,
        status: 'ACTIVE',
        version: 11,
      },
    ],
    assignedAreas: [],
    initialMarkers: [
      {
        markerId: 'marker-001',
        incidentId: 'inc-precinct-first-001',
        opId: 'op-001',
        coordinate: [126.1, 35.1],
        status: 'ACTIVE',
      },
    ],
    overallSearchArea: {
      areaId: 'area-overall-001',
      incidentId: 'inc-precinct-first-001',
      areaLevel: 'OVERALL',
      status: 'ACTIVE',
      overallAreaHash: 'overall-hash',
      polygon: [],
    },
    tileItems: [
      {
        itemKey: 'tile:osm-local:15:27935:12960',
        styleId: 'osm-local',
        z: 15,
        x: 27935,
        y: 12960,
        url: 'local://tiles/inc-precinct-first-001/15/27935/12960.pbf',
        checksum: 'sha256:tile-1',
        bytes: 1024,
      },
      {
        itemKey: 'tile:osm-local:16:55870:25920',
        styleId: 'osm-local',
        z: 16,
        x: 55870,
        y: 25920,
        url: 'local://tiles/inc-precinct-first-001/16/55870/25920.pbf',
        checksum: 'sha256:tile-2',
        bytes: 2048,
      },
    ],
    packageItems: [
      packageItem('incident:inc-precinct-first-001', 'INCIDENT_META'),
      packageItem('missing-person:inc-precinct-first-001', 'MISSING_PERSON_CACHE'),
      packageItem('op-list:inc-precinct-first-001', 'OP_LIST'),
      packageItem('assigned-area:none:inc-precinct-first-001', 'ASSIGNED_AREA'),
      packageItem('initial-marker:marker-001', 'INITIAL_MARKER'),
      packageItem('overall-search-area:area-overall-001', 'OVERALL_SEARCH_AREA'),
      packageItem('tile-manifest:manifest-001', 'TILE'),
    ],
  };
}

function packageItem(
  itemKey: string,
  itemType: OfflinePackageManifestResponse['packageItems'][number]['itemType'],
) {
  return {
    itemKey,
    itemType,
    status: 'DOWNLOADED',
    sourceVersion: 1,
    sourceHash: `sha256:${itemType.toLowerCase()}`,
  };
}


