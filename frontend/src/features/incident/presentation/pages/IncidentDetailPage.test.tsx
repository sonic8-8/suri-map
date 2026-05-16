import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import {
  useIncidentDetailQuery,
  type ActiveIncidentDetailResponse,
  type IncidentDetailResponse,
  type TerminalIncidentDetailResponse,
} from '../../api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { IncidentDetailPage } from './IncidentDetailPage';

vi.mock('../../api/incidentReadApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/incidentReadApi')>()),
  useIncidentDetailQuery: vi.fn(),
}));

describe('IncidentDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('renders active incident detail without exposing ids or storage keys', () => {
    const detail = activeIncidentDetail();
    vi.mocked(useIncidentDetailQuery).mockReturnValue(incidentDetailQueryResult(detail));

    renderIncidentDetailPage(detail.incidentId);

    expect(screen.getAllByText('하천변 야간 실종자 수색').length).toBeGreaterThan(0);
    expect(screen.getAllByText('정보 버전 4').length).toBeGreaterThan(0);
    expect(screen.getAllByText('홍길동').length).toBeGreaterThan(0);
    expect(screen.getByRole('img', { name: '홍길동 사진' })).toHaveAttribute(
      'src',
      '/mock-upload/missing-person/hong.jpg',
    );

    const pageText = document.body.textContent ?? '';
    expect(pageText).not.toContain(detail.incidentId);
    expect(pageText).not.toContain(detail.id);
    expect(pageText).not.toContain(detail.missingPerson?.photoObjectKey);
    expect(pageText).not.toContain(detail.assignments[0]?.accountId);
    expect(pageText).not.toContain('사건 ID');
    expect(pageText).not.toContain('내부 ID');
    expect(pageText).not.toContain('objectKey');
    expect(pageText).not.toContain('Incident Detail');
    expect(pageText).not.toContain('version');
  });

  test('renders closed incident detail as terminal-facing user text', () => {
    const detail = closedIncidentDetail();
    vi.mocked(useIncidentDetailQuery).mockReturnValue(incidentDetailQueryResult(detail));

    renderIncidentDetailPage(detail.incidentId);

    expect(screen.getAllByText('종료된 실종 사건').length).toBeGreaterThan(0);
    expect(screen.getByText('종료 정보')).toBeInTheDocument();
    expect(screen.getAllByText('사건 종료 후 수정 불가').length).toBeGreaterThan(0);

    const pageText = document.body.textContent ?? '';
    expect(pageText).not.toContain(detail.incidentId);
    expect(pageText).not.toContain(detail.id);
    expect(pageText).not.toContain(detail.terminalSnapshot.id);
    expect(pageText).not.toContain('스냅샷 ID');
    expect(pageText).not.toContain('terminalSnapshot');
    expect(pageText).not.toContain('쓰기 제한 사유');
  });
});

function renderIncidentDetailPage(incidentId: string) {
  return render(
    <IncidentDetailPage
      currentUserAccount={currentUserAccount()}
      incidentId={incidentId}
      markerNotificationIndex={0}
      markerNotifications={[]}
      onCloseMarkerNotifications={vi.fn()}
      onMoveMarkerNotification={vi.fn()}
      onOpenHandover={vi.fn()}
      onOpenIncidentList={vi.fn()}
      onOpenOfflinePackage={vi.fn()}
      onOpenSituationBoard={vi.fn()}
    />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-command-alpha',
    name: '상황실',
    organization: '광산경찰서',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: '상황판 계정',
  };
}

function incidentDetailQueryResult(data: IncidentDetailResponse) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useIncidentDetailQuery>;
}

function activeIncidentDetail(): ActiveIncidentDetailResponse {
  return {
    id: '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
    incidentId: '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
    title: '하천변 야간 실종자 수색',
    status: 'OPEN',
    openedAt: '2026-05-14T00:30:00Z',
    version: 4,
    missingPerson: {
      incidentId: '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
      displayName: '홍길동',
      photoObjectKey: 'private/missing-person/hong.jpg',
      photoUrl: '/mock-upload/missing-person/hong.jpg',
      appearanceText: '검은 상의, 회색 운동화',
      lastSeenLocationText: '하천변 산책로',
      lastSeenAt: '2026-05-14T01:00:00Z',
    },
    assignments: [
      {
        accountId: 'acct-team-hidden-001',
        accountDisplayName: null,
        accountType: 'TEAM',
        organizationType: 'MISSING_TEAM',
        incidentRole: 'FIELD_COMMANDER',
        assignedAt: '2026-05-14T00:35:00Z',
      },
    ],
  };
}

function closedIncidentDetail(): TerminalIncidentDetailResponse {
  return {
    id: '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
    incidentId: '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
    status: 'CLOSED',
    version: 5,
    closedAt: '2026-05-14T10:30:00Z',
    writeDisabledReason: 'incident_closed',
    terminalSnapshot: {
      id: 'terminal-snapshot-hidden-001',
      incidentId: '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
      status: 'CLOSED',
      version: 5,
      closedAt: '2026-05-14T10:30:00Z',
      writeDisabledReason: 'incident_closed',
    },
  };
}
