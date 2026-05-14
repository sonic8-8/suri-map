import { render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { getIncidentDetail } from '../../../situationBoard/data/getIncidentDetail';
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

  test('renders all manifest item types, no assigned area state, and tile summary', async () => {
    renderOfflinePackageStatusPage();
    await screen.findByText('홍길동 실종 사건');

    for (const itemType of [
      'INCIDENT_META',
      'MISSING_PERSON_CACHE',
      'OP_LIST',
      'ASSIGNED_AREA',
      'INITIAL_MARKER',
      'OVERALL_SEARCH_AREA',
      'TILE',
    ]) {
      expect(screen.getByText(new RegExp(itemType))).toBeInTheDocument();
    }

    expect(screen.getByText(/배정 구역 없음/)).toBeInTheDocument();

    const tileSummary = screen.getByLabelText('타일 목록 요약');
    expect(within(tileSummary).getByText('2개')).toBeInTheDocument();
    expect(within(tileSummary).getByText('3.0 KiB')).toBeInTheDocument();
    expect(within(tileSummary).getByText('osm-local')).toBeInTheDocument();
    expect(within(tileSummary).getByText('15..16')).toBeInTheDocument();
    expect(within(tileSummary).getByText('전체 있음')).toBeInTheDocument();
  });

  test('keeps package_badge device statuses visible', async () => {
    renderOfflinePackageStatusPage();
    await screen.findByText('홍길동 실종 사건');

    expect(screen.getByText('팀장 단말')).toBeInTheDocument();
    expect(screen.getByText('오프라인 사용 가능')).toBeInTheDocument();
    expect(screen.getAllByText('재적재 필요')).toHaveLength(2);
    expect(screen.getByText('실패')).toBeInTheDocument();
    expect(screen.getByText('삭제됨')).toBeInTheDocument();
  });

  test('shows only the manifest section failure when manifest API fails', async () => {
    vi.mocked(useOfflinePackageManifestQuery).mockReturnValue(
      manifestQueryResult(undefined, { isError: true, refetch: vi.fn() }),
    );

    renderOfflinePackageStatusPage();
    await screen.findByText('홍길동 실종 사건');

    expect(screen.getByText('팀장 단말')).toBeInTheDocument();
    expect(screen.getByText('패키지 구성 목록을 불러오지 못했습니다.')).toBeInTheDocument();
    expect(screen.queryByText('단말별 적재 상태를 불러오지 못했습니다.')).not.toBeInTheDocument();
  });
});

function renderOfflinePackageStatusPage() {
  return render(
    <OfflinePackageStatusPage
      currentUserAccount={currentUserAccount()}
      incidentId="inc-precinct-first-001"
      markerNotificationIndex={0}
      markerNotifications={[]}
      onBackToSituationBoard={vi.fn()}
      onCloseMarkerNotifications={vi.fn()}
      onMoveMarkerNotification={vi.fn()}
      onOpenHandover={vi.fn()}
      onOpenIncidentList={vi.fn()}
      onOpenOfflinePackage={vi.fn()}
    />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-001',
    name: '상황실',
    organization: '광산경찰서',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: '상황판 계정',
  };
}

function boardQueryResult() {
  return {
    data: {
      incidentId: 'inc-precinct-first-001',
      boardResponseVersion: 7,
      serverTs: '2026-05-14T02:00:00Z',
      activeOpId: 'op-001',
      selectedOpIds: ['op-001'],
      geometryHash: 'hash-geometry',
      slots: {
        package_badge: [
          packageBadgeRow('pkg-ready', 'phone-ready', '팀장 단말', 'READY', true, false),
          packageBadgeRow('pkg-stale', 'phone-stale', '수색1 단말', 'STALE', false, true),
          packageBadgeRow('pkg-failed', 'phone-failed', '수색2 단말', 'FAILED', false, true),
          packageBadgeRow('pkg-purged', 'phone-purged', '종료 단말', 'PURGED', false, false),
        ],
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
      activeManifestVersion: 2,
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
        itemKey: 'tile:osm-local:15:1:2',
        styleId: 'osm-local',
        z: 15,
        x: 27925,
        y: 12680,
        url: 'local://tiles/15/1/2.pbf',
        checksum: 'sha256:tile-1',
        bytes: 1024,
      },
      {
        itemKey: 'tile:osm-local:16:3:4',
        styleId: 'osm-local',
        z: 16,
        x: 27960,
        y: 12720,
        url: 'local://tiles/16/3/4.pbf',
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
