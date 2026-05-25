import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { getIncidentDetail } from '../../../incident/api/incidentReadApi';
import { useOfflinePackageManifestQuery } from '../../api/offlinePackageApi';
import type { OfflinePackageManifestResponse } from '../../api/offlinePackageApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { OfflinePackageStatusPage } from './OfflinePackageStatusPage';

vi.mock('../../../board/api/incidentBoardApi', () => ({
  useIncidentBoardQuery: vi.fn(),
}));

vi.mock('../../api/offlinePackageApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/offlinePackageApi')>()),
  useOfflinePackageManifestQuery: vi.fn(),
}));

vi.mock('../../../incident/api/incidentReadApi', () => ({
  getIncidentDetail: vi.fn(),
}));

describe('OfflinePackageStatusPage filters', () => {
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

  test('summary buttons filter device status rows', async () => {
    renderOfflinePackageStatusPage();
    await screen.findByText('광산구 실종 신고');

    const filterToolbar = screen.getByRole('toolbar', { name: '단말 상태 필터' });
    const allButton = within(filterToolbar).getByRole('button', { name: /전체 단말/ });
    const readyButton = within(filterToolbar).getByRole('button', { name: /오프라인\s*사용 가능 단말/ });
    const warningButton = within(filterToolbar).getByRole('button', { name: /재확인\s*필요 단말/ });
    const purgedButton = within(filterToolbar).getByRole('button', { name: /미설치 단말/ });

    expect(allButton).toHaveAttribute('aria-pressed', 'true');
    expect(readyButton).toHaveAttribute('aria-pressed', 'false');
    expect(warningButton).toHaveAttribute('aria-pressed', 'false');
    expect(purgedButton).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(warningButton);

    expect(warningButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByText('자동 설치 중').length).toBeGreaterThan(0);
    expect(screen.getAllByText('업데이트 필요').length).toBeGreaterThan(0);
    expect(screen.getAllByText('자동 설치 실패').length).toBeGreaterThan(0);
    expect(screen.queryAllByText('오프라인 사용 가능').length).toBe(0);
    expect(screen.queryAllByText('삭제됨').length).toBe(0);

    fireEvent.click(purgedButton);

    expect(purgedButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByText('종료 단말').length).toBeGreaterThan(0);
    expect(screen.getAllByText('삭제됨').length).toBeGreaterThan(0);
    expect(screen.queryAllByText('자동 설치 중').length).toBe(0);
    expect(screen.queryAllByText('오프라인 사용 가능').length).toBe(0);

    fireEvent.click(readyButton);

    expect(readyButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByText('수완지구대 지휘 단말').length).toBeGreaterThan(0);
    expect(screen.getAllByText('필수 자료 설치 완료').length).toBeGreaterThan(0);
    expect(screen.queryAllByText('업데이트 필요').length).toBe(0);
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
