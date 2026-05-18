import { render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../../login/presentation/types/login';
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
