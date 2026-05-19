import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import {
  useIncidentDetailQuery,
  type ActiveIncidentDetailResponse,
  type IncidentDetailResponse,
  type TerminalIncidentDetailResponse,
} from '../../api/incidentReadApi';
import { useOperationalPeriodListQuery } from '../../../operationalPeriod/api/operationalPeriodApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { IncidentDetailPage } from './IncidentDetailPage';

vi.mock('../../api/incidentReadApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/incidentReadApi')>()),
  useIncidentDetailQuery: vi.fn(),
}));

vi.mock('../../../operationalPeriod/api/operationalPeriodApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../operationalPeriod/api/operationalPeriodApi')>()),
  useOperationalPeriodListQuery: vi.fn(),
}));

describe('IncidentDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useOperationalPeriodListQuery).mockReturnValue(operationalPeriodListQueryResult(2));
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

  test('paginates assignment rows five at a time', () => {
    const detail = activeIncidentDetailWithAssignments(12);
    vi.mocked(useIncidentDetailQuery).mockReturnValue(incidentDetailQueryResult(detail));

    renderIncidentDetailPage(detail.incidentId);

    expect(screen.getByText('계정 01')).toBeInTheDocument();
    expect(screen.getByText('계정 05')).toBeInTheDocument();
    expect(screen.queryByText('계정 06')).not.toBeInTheDocument();

    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '첫 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(screen.getByText('계정 06')).toBeInTheDocument();
    expect(screen.getByText('계정 10')).toBeInTheDocument();
    expect(screen.queryByText('계정 01')).not.toBeInTheDocument();
    expect(screen.getByText('6-10 / 12')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2' })).toHaveAttribute('aria-current', 'page');
  });
});

function renderIncidentDetailPage(incidentId: string, onBrowserBackToIncidentList = vi.fn()) {
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
      onBrowserBackToIncidentList={onBrowserBackToIncidentList}
      onOpenOfflinePackage={vi.fn()}
      onOpenSituationBoard={vi.fn()}
    />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-command-alpha',
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

function incidentDetailQueryResult(data: IncidentDetailResponse) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useIncidentDetailQuery>;
}

function operationalPeriodListQueryResult(sequenceNumber: number) {
  const currentOpId = '88888888-8888-8888-8888-888888880002';
  return {
    data: {
      currentOpId,
      items: [
        {
          id: currentOpId,
          status: 'ACTIVE',
          reason: 'RE_SEARCH',
          sequenceNumber,
          openedAt: '2026-05-14T02:00:00Z',
          endedAt: null,
          version: 1,
        },
      ],
    },
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useOperationalPeriodListQuery>;
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

function activeIncidentDetailWithAssignments(count: number): ActiveIncidentDetailResponse {
  const base = activeIncidentDetail();
  return {
    ...base,
    assignments: Array.from({ length: count }, (_, index) => ({
      accountId: `acct-team-hidden-${String(index + 1).padStart(3, '0')}`,
      accountDisplayName: `계정 ${String(index + 1).padStart(2, '0')}`,
      accountType: index % 2 === 0 ? 'TEAM' : 'PATROL_CAR',
      organizationType: index % 3 === 0 ? 'MISSING_TEAM' : 'SUPPORT_UNIT',
      incidentRole: index % 4 === 0 ? 'FIELD_COMMANDER' : index % 4 === 1 ? 'INCIDENT_COMMANDER' : 'MEMBER',
      assignedAt: `2026-05-14T0${Math.min(9, index % 10)}:35:00Z`,
    })),
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

test('IncidentDetailPage returns to the incident list when the browser back button is used', () => {
  vi.clearAllMocks();
  const detail = activeIncidentDetail();
  const onBrowserBackToIncidentList = vi.fn();
  vi.mocked(useIncidentDetailQuery).mockReturnValue(incidentDetailQueryResult(detail));

  renderIncidentDetailPage(detail.incidentId, onBrowserBackToIncidentList);

  window.dispatchEvent(new PopStateEvent('popstate'));

  expect(onBrowserBackToIncidentList).toHaveBeenCalledTimes(1);
});
