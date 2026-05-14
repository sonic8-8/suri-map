import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { incidentCommandApi } from '../../../incident/api/incidentCommandApi';
import {
  incidentReadApi,
  type ActiveIncidentDetailResponse,
  type IncidentListResponse,
} from '../../../incident/api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { IncidentListPage } from './IncidentListPage';

vi.mock('../../../incident/api/incidentCommandApi', () => ({
  incidentCommandApi: {
    importIncident: vi.fn(),
  },
}));

vi.mock('../../../incident/api/incidentReadApi', () => ({
  incidentReadApi: {
    list: vi.fn(),
    detail: vi.fn(),
  },
}));

const importedIncidentId = 'inc-precinct-first-001';
const defaultSourceIncidentId = '00000000-0000-0000-0000-000000000001';

describe('IncidentListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('uses incident detail fields to enrich list cards', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockResolvedValueOnce(activeIncidentDetail());

    renderIncidentListPage();

    await screen.findByText('광산구 실종 신고');
    expect(incidentReadApi.detail).toHaveBeenCalledWith(importedIncidentId);
    expect(screen.getByText('마지막 확인 장소')).toBeInTheDocument();
    expect(screen.getByText('광산구 월곡동')).toBeInTheDocument();
    expect(screen.getByText('마지막 확인 시각')).toBeInTheDocument();
    expect(screen.getByText('2026-05-14 10:00 KST')).toBeInTheDocument();
    expect(screen.getByText('실종자 홍길동')).toBeInTheDocument();
    expect(screen.getByText('파출소 1')).toBeInTheDocument();
    expect(screen.getByText('광산 수색팀')).toBeInTheDocument();
    expect(screen.getByText('2026-05-14 09:30 KST')).toBeInTheDocument();
  });

  test('keeps list cards visible when detail enrichment fails', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockRejectedValueOnce(new Error('detail failed'));

    renderIncidentListPage();

    await screen.findByText('기존 목록 제목');
    expect(screen.getByText('마지막 확인 장소')).toBeInTheDocument();
    expect(screen.getByText(`사건 ID ${importedIncidentId}`)).toBeInTheDocument();
    expect(screen.getByText('마지막 확인 시각')).toBeInTheDocument();
    expect(screen.getByText('v1')).toBeInTheDocument();
  });

  test('keeps the import dialog open when imported incident is not visible after reload', async () => {
    vi.mocked(incidentReadApi.list)
      .mockResolvedValueOnce(incidentListResponse([]))
      .mockResolvedValueOnce(incidentListResponse([]));
    vi.mocked(incidentCommandApi.importIncident).mockResolvedValueOnce({
      id: importedIncidentId,
      incidentId: importedIncidentId,
      status: 'OPEN',
      version: 1,
      assignmentAccountIds: ['acct-missing-team-commander'],
    });

    renderIncidentListPage();
    await waitFor(() => expect(incidentReadApi.list).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: '사건 가져오기' }));
    const importDialog = screen.getByRole('dialog', { name: '사건 가져오기' });
    fireEvent.click(within(importDialog).getByRole('button', { name: '가져오기' }));

    await screen.findByText('사건 가져오기는 완료됐지만 목록에서 확인되지 않습니다. 목록을 새로고침한 뒤 다시 확인해 주세요.');
    expect(screen.getByRole('dialog', { name: '사건 가져오기' })).toBeInTheDocument();
    expect(within(importDialog).getByRole('button', { name: '가져오기' })).toBeEnabled();
  });

  test('marks the default source incident as imported after successful import', async () => {
    vi.mocked(incidentReadApi.list)
      .mockResolvedValueOnce(incidentListResponse([]))
      .mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockResolvedValueOnce(activeIncidentDetail());
    vi.mocked(incidentCommandApi.importIncident).mockResolvedValueOnce({
      id: importedIncidentId,
      incidentId: importedIncidentId,
      status: 'OPEN',
      version: 1,
      assignmentAccountIds: ['acct-missing-team-commander'],
    });

    renderIncidentListPage();
    await waitFor(() => expect(incidentReadApi.list).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: '사건 가져오기' }));
    fireEvent.click(within(screen.getByRole('dialog', { name: '사건 가져오기' })).getByRole('button', { name: '가져오기' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '사건 가져오기' })).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    fireEvent.click(screen.getByRole('button', { name: '사건 가져오기' }));

    const importDialog = screen.getByRole('dialog', { name: '사건 가져오기' });
    expect(within(importDialog).getByDisplayValue(defaultSourceIncidentId)).toBeInTheDocument();
    expect(within(importDialog).getByText('이미 가져온 사건')).toBeInTheDocument();
  });
});

function renderIncidentListPage() {
  return render(
    <IncidentListPage
      currentUserAccount={currentUserAccount()}
      onOpenLogin={vi.fn()}
      onOpenSituationBoard={vi.fn()}
    />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-missing-team-commander',
    name: '광산팀 지휘관',
    organization: '광산팀',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    role: 'MISSING_TEAM_COMMANDER',
    roles: ['MISSING_TEAM_COMMANDER'],
    description: '사건 가져오기 가능 계정',
  };
}

function incidentListResponse(items: IncidentListResponse['items']): IncidentListResponse {
  return { items };
}

function importedIncident(): IncidentListResponse['items'][number] {
  return {
    id: importedIncidentId,
    incidentId: importedIncidentId,
    title: '기존 목록 제목',
    status: 'OPEN',
    version: 1,
    closedAt: null,
  };
}

function activeIncidentDetail(): ActiveIncidentDetailResponse {
  return {
    id: importedIncidentId,
    incidentId: importedIncidentId,
    title: '광산구 실종 신고',
    status: 'OPEN',
    openedAt: '2026-05-14T00:30:00Z',
    version: 3,
    missingPerson: {
      incidentId: importedIncidentId,
      displayName: '홍길동',
      photoObjectKey: 'missing-person/photo.jpg',
      appearanceText: '검은 상의',
      lastSeenLocationText: '광산구 월곡동',
      lastSeenAt: '2026-05-14T01:00:00Z',
    },
    assignments: [
      {
        accountId: 'acct-team-001',
        accountDisplayName: '광산 수색팀',
        accountType: 'TEAM',
        organizationType: 'POLICE_SUBSTATION',
        incidentRole: 'MEMBER',
        assignedAt: '2026-05-14T00:35:00Z',
      },
    ],
  };
}
