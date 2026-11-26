import { render } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { useCloseIncidentMutation } from '../../../incident/api/incidentCommandApi';
import {
  useIncidentDetailQuery,
  type ActiveIncidentDetailResponse,
  type IncidentDetailResponse,
  type TerminalIncidentDetailResponse,
} from '../../../incident/api/incidentReadApi';
import { useOperationalPeriodListQuery } from '../../../operationalPeriod/api/operationalPeriodApi';
import { IncidentClosePage } from './IncidentClosePage';

vi.mock('../../../incident/api/incidentReadApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../incident/api/incidentReadApi')>()),
  useIncidentDetailQuery: vi.fn(),
}));

vi.mock('../../../operationalPeriod/api/operationalPeriodApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../operationalPeriod/api/operationalPeriodApi')>()),
  useOperationalPeriodListQuery: vi.fn(),
}));

vi.mock('../../../board/api/incidentBoardApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../board/api/incidentBoardApi')>()),
  useIncidentBoardQuery: vi.fn(),
}));

vi.mock('../../../incident/api/incidentCommandApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../incident/api/incidentCommandApi')>()),
  useCloseIncidentMutation: vi.fn(),
}));

const INCIDENT_ID = '6a28ddad-6a5b-4b2b-a676-fac5c2a35801';

describe('IncidentClosePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useOperationalPeriodListQuery).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useOperationalPeriodListQuery>);
    vi.mocked(useIncidentBoardQuery).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useIncidentBoardQuery>);
    vi.mocked(useCloseIncidentMutation).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCloseIncidentMutation>);
  });

  test('진행 중 사건의 종료 경고에는 파기 대상이 폴리폰이 아니라 현장 인원 위치로 나온다', () => {
    mockDetail(activeIncidentDetail());

    renderPage();

    const pageText = document.body.textContent ?? '';
    expect(pageText).toContain('실종자 개인정보, 사진, 현장 인원 위치, 경로 좌표는 파기 절차로 전환되며');
    expect(pageText).not.toContain('폴리폰');
  });

  test('종료 사건 요약에는 다시 노출하지 않는 정보가 폴리폰이 아니라 현장 인원 위치로 나온다', () => {
    mockDetail(closedIncidentDetail());

    renderPage();

    const pageText = document.body.textContent ?? '';
    expect(pageText).toContain('실종자 개인정보, 현장 인원 위치, 경로 좌표, 실시간');
    expect(pageText).not.toContain('폴리폰');
  });
});

function renderPage() {
  render(<IncidentClosePage incidentId={INCIDENT_ID} onBackToIncidents={vi.fn()} onOpenLogin={vi.fn()} />);
}

function mockDetail(data: IncidentDetailResponse) {
  vi.mocked(useIncidentDetailQuery).mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useIncidentDetailQuery>);
}

function activeIncidentDetail(): ActiveIncidentDetailResponse {
  return {
    id: INCIDENT_ID,
    incidentId: INCIDENT_ID,
    title: '하천변 야간 실종자 수색',
    status: 'OPEN',
    openedAt: '2026-11-25T00:30:00Z',
    version: 1,
    missingPerson: null,
    assignments: [],
  };
}

function closedIncidentDetail(): TerminalIncidentDetailResponse {
  return {
    id: INCIDENT_ID,
    incidentId: INCIDENT_ID,
    status: 'CLOSED',
    version: 2,
    closedAt: '2026-11-25T10:30:00Z',
    writeDisabledReason: 'incident_closed',
    terminalSnapshot: {
      id: 'terminal-snapshot-5801',
      incidentId: INCIDENT_ID,
      status: 'CLOSED',
      version: 2,
      closedAt: '2026-11-25T10:30:00Z',
      writeDisabledReason: 'incident_closed',
    },
  };
}
