import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../../login/presentation/types/login';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { SituationBoardHeader } from './SituationBoardHeader';

describe('SituationBoardHeader', () => {
  test('shows readable incident context without exposing the incident UUID', () => {
    const incidentId = '11111111-2222-3333-4444-555555555555';

    render(
      <SituationBoardHeader
        activeOperationalPeriodLabel="OP 9차"
        activeTab="situationBoard"
        apiBoard={null}
        currentUserAccount={currentUserAccount()}
        incidentDetail={{
          id: incidentId,
          incidentId,
          title: '광산구 실종 신고',
          status: 'OPEN',
          openedAt: '2026-05-14T00:30:00Z',
          version: 9,
          missingPerson: {
            incidentId,
            displayName: '박민수',
            photoObjectKey: '',
            photoUrl: null,
            appearanceText: '',
            lastSeenLocationText: '',
            lastSeenAt: '',
          },
          assignments: [],
        }}
        markerNotificationIndex={0}
        markerNotifications={[]}
        onCloseMarkerNotifications={vi.fn()}
        onMoveMarkerNotification={vi.fn()}
        onOpenLogin={vi.fn()}
        onOpenIncidentList={vi.fn()}
      />,
    );

    expect(screen.getByText('정보 버전 9')).toBeInTheDocument();
    expect(screen.getByText('광산구 실종 신고')).toBeInTheDocument();
    expect(screen.getByText('진행 중 • OP 9차')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(incidentId))).not.toBeInTheDocument();
  });

  test('shows a compact offline package warning badge from package_badge rows', () => {
    const onOpenOfflinePackage = vi.fn();

    render(
      <SituationBoardHeader
        activeOperationalPeriodLabel="OP 9차"
        activeTab="situationBoard"
        apiBoard={packageBadgeBoard()}
        currentUserAccount={currentUserAccount()}
        incidentDetail={null}
        markerNotificationIndex={0}
        markerNotifications={[]}
        onCloseMarkerNotifications={vi.fn()}
        onMoveMarkerNotification={vi.fn()}
        onOpenIncidentList={vi.fn()}
        onOpenOfflinePackage={onOpenOfflinePackage}
      />,
    );

    const badge = screen.getByRole('button', { name: '오프라인 패키지 확인이 필요한 폴리폰 1대' });
    expect(badge).toHaveTextContent('오프라인 패키지 확인 필요 1대');

    fireEvent.click(badge);
    expect(onOpenOfflinePackage).toHaveBeenCalledTimes(1);
  });

  test('hides the offline package warning badge when all package_badge rows are ready', () => {
    render(
      <SituationBoardHeader
        activeOperationalPeriodLabel="OP 9차"
        activeTab="situationBoard"
        apiBoard={packageBadgeBoard({ packageStatus: 'READY', warningRaised: false, readyForOfflineUse: true })}
        currentUserAccount={currentUserAccount()}
        incidentDetail={null}
        markerNotificationIndex={0}
        markerNotifications={[]}
        onCloseMarkerNotifications={vi.fn()}
        onMoveMarkerNotification={vi.fn()}
        onOpenIncidentList={vi.fn()}
        onOpenOfflinePackage={vi.fn()}
      />,
    );

    expect(screen.queryByText(/오프라인 패키지 확인 필요/)).not.toBeInTheDocument();
  });
});

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-missing-team-commander',
    name: '광산구 실종 사건 지휘',
    organization: '광산구 실종 사건 지휘',
    rank: '지휘관',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: '상황판 헤더 테스트 계정',
  };
}

function packageBadgeBoard(
  options: { packageStatus?: string; readyForOfflineUse?: boolean; warningRaised?: boolean } = {},
): SituationBoardResponseDto {
  const packageStatus = options.packageStatus ?? 'STALE';
  const readyForOfflineUse = options.readyForOfflineUse ?? false;
  const warningRaised = options.warningRaised ?? true;

  return {
    incidentId: 'inc-precinct-first-001',
    boardResponseVersion: 7,
    serverTs: '2026-05-14T02:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    geometryHash: 'hash-board',
    slots: {
      package_badge: [
        {
          id: 'pkg-ready',
          status: 'ACTIVE',
          version: 1,
          sequence: 1,
          sourceSpec: 'S7',
          sourceHash: 'hash-ready',
          latestEventId: 'evt-ready',
          policePhoneId: 'phone-ready',
          policePhoneName: '정상 단말',
          packageStatus: 'READY',
          manifestVersion: 2,
          readyForOfflineUse: true,
          localWarningInput: {
            raised: false,
            activeManifestVersion: 2,
          },
        },
        {
          id: 'pkg-target',
          status: 'ACTIVE',
          version: 1,
          sequence: 2,
          sourceSpec: 'S7',
          sourceHash: 'hash-target',
          latestEventId: 'evt-target',
          policePhoneId: 'phone-target',
          policePhoneName: '확인 필요 단말',
          packageStatus,
          manifestVersion: 1,
          readyForOfflineUse,
          localWarningInput: {
            raised: warningRaised,
            activeManifestVersion: packageStatus === 'READY' ? 1 : 2,
          },
        },
      ],
    },
    sourceVersions: {},
    sourceHashes: {},
    slotSources: {},
  };
}
