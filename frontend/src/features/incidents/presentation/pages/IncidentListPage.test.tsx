import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { incidentCommandApi } from '../../../incident/api/incidentCommandApi';
import { incidentReadApi, type IncidentListResponse } from '../../../incident/api/incidentReadApi';
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
  },
}));

const importedIncidentId = 'inc-precinct-first-001';

describe('IncidentListPage incident import', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('가져오기 완료가 확인되지 않으면 이미 가져온 사건으로 표시하지 않는다', async () => {
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

    await screen.findByText('사건 가져오기가 완료되지 않았습니다. 목록을 새로고침한 뒤 다시 시도해 주세요.');
    expect(within(importDialog).queryByText('이미 가져온 사건')).not.toBeInTheDocument();
    expect(within(importDialog).getByRole('button', { name: '가져오기' })).toBeEnabled();
  });

  test('가져온 사건이 목록에서 확인된 뒤에만 이미 가져온 사건으로 표시한다', async () => {
    vi.mocked(incidentReadApi.list)
      .mockResolvedValueOnce(incidentListResponse([]))
      .mockResolvedValueOnce(incidentListResponse([importedIncident()]));
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

    expect(screen.getByRole('dialog', { name: '사건 가져오기' })).toBeInTheDocument();
    expect(screen.getByText('이미 가져온 사건')).toBeInTheDocument();
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
    name: '실종팀 지휘관',
    organization: '실종팀',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    role: 'MISSING_TEAM_COMMANDER',
    roles: ['MISSING_TEAM_COMMANDER'],
    description: '사건 가져오기 권한 계정',
  };
}

function incidentListResponse(items: IncidentListResponse['items']): IncidentListResponse {
  return { items };
}

function importedIncident(): IncidentListResponse['items'][number] {
  return {
    id: importedIncidentId,
    incidentId: importedIncidentId,
    title: '북한산 실종 사건',
    status: 'OPEN',
    version: 1,
    closedAt: null,
  };
}
