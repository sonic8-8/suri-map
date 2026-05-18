import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import {
  incidentReadApi,
  type ActiveIncidentDetailResponse,
  type IncidentListResponse,
} from '../../../incident/api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { IncidentListPage } from './IncidentListPage';

vi.mock('../../../incident/api/incidentReadApi', () => ({
  incidentReadApi: {
    list: vi.fn(),
    detail: vi.fn(),
  },
}));

vi.mock('../../../../shared/api/eventStream', () => ({
  openAssignedIncidentEventStream: vi.fn(async () => undefined),
}));

const importedIncidentId = 'inc-precinct-first-001';

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

  test('does not show manual incident import controls in the operational incident list', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([]));

    renderIncidentListPage();
    await waitFor(() => expect(incidentReadApi.list).toHaveBeenCalledTimes(1));

    expect(screen.queryByRole('button', { name: '사건 가져오기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '사건 가져오기' })).not.toBeInTheDocument();
    expect(screen.getByText('mock 112에서 배정된 사건은 자동으로 반영됩니다.')).toBeInTheDocument();
  });
});

function renderIncidentListPage() {
  return render(
    <IncidentListPage currentUserAccount={currentUserAccount()} onOpenLogin={vi.fn()} onOpenSituationBoard={vi.fn()} />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-missing-team-commander',
    name: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
    organization: '광주경찰청 여성청소년과 실종팀',
    rank: '경감',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    role: 'MISSING_TEAM_COMMANDER',
    roles: ['MISSING_TEAM_COMMANDER'],
    description: '실종팀 지휘 계정',
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
      photoUrl: '/mock-upload/missing-person/photo.jpg',
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
