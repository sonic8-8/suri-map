import { render, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../login/presentation/types/login';
import { getHandoverIncidentDetail } from '../../data/getHandoverIncidentDetail';
import { HandoverPage } from './HandoverPage';
import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { handoverApi, useDutyShiftListQuery, useSearchHistorySummaryListQuery } from '../../../operationalPeriod/api/handoverApi';
import { operationalPeriodApi } from '../../../operationalPeriod/api/operationalPeriodApi';

vi.mock('../../../board/api/incidentBoardApi', () => ({
  incidentBoardQueryKeys: { all: ['incidentBoard'] },
  useIncidentBoardQuery: vi.fn(),
}));

vi.mock('../../../operationalPeriod/api/handoverApi', () => ({
  handoverApi: {
    createHandoverMemo: vi.fn(),
    listHandoverMemos: vi.fn(),
  },
  useDutyShiftListQuery: vi.fn(),
  useSearchHistorySummaryListQuery: vi.fn(),
}));

vi.mock('../../../operationalPeriod/api/operationalPeriodApi', () => ({
  operationalPeriodApi: {
    create: vi.fn(),
    list: vi.fn(),
  },
}));

vi.mock('../../data/getHandoverIncidentDetail', () => ({
  getHandoverIncidentDetail: vi.fn(),
}));

vi.mock('../components/HandoverOperationalPeriodSelector', () => ({
  HandoverOperationalPeriodSelector: () => <div data-testid="handover-op-selector" />,
}));

vi.mock('../components/HandoverComparisonMap', () => ({
  HandoverComparisonMap: () => <div data-testid="handover-map" />,
}));

vi.mock('../../../../shared/hooks/useBrowserBackToIncidentList', () => ({
  useBrowserBackToIncidentList: vi.fn(),
}));

describe('HandoverPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(useIncidentBoardQuery).mockReturnValue(boardQueryResult());
    vi.mocked(useSearchHistorySummaryListQuery).mockReturnValue({
      data: undefined,
      isError: false,
      isFetching: false,
      isLoading: false,
    } as any);
    vi.mocked(useDutyShiftListQuery).mockReturnValue({
      data: { items: [] },
      isError: false,
      isFetching: false,
      isLoading: false,
    } as any);
    vi.mocked(operationalPeriodApi.list).mockReturnValue(new Promise(() => undefined));
    vi.mocked(getHandoverIncidentDetail).mockReturnValue(new Promise(() => undefined));
    vi.mocked(handoverApi.listHandoverMemos).mockReturnValue(new Promise(() => undefined));
  });

  test('shared map mode ignores the passed snapshot and uses the incident board query', async () => {
    const onSharedMapPropsChange = vi.fn();

    render(
      <QueryClientProvider client={new QueryClient()}>
        <HandoverPage
          embedded
          sharedMapMode
          boardSnapshot={boardSnapshot(999)}
          currentUserAccount={currentUserAccount()}
          incidentId={INCIDENT_ID}
          markerNotificationIndex={0}
          markerNotifications={[]}
          onCloseMarkerNotifications={vi.fn()}
          onMoveMarkerNotification={vi.fn()}
          onOpenIncidentList={vi.fn()}
          onOpenIncidentDetail={vi.fn()}
          onOpenOfflinePackage={vi.fn()}
          onOpenSituationBoard={vi.fn()}
          onSharedMapPropsChange={onSharedMapPropsChange}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(onSharedMapPropsChange).toHaveBeenCalled());

    expect(vi.mocked(useIncidentBoardQuery).mock.calls[0]?.[0]).toMatchObject({
      incidentId: INCIDENT_ID,
      opIds: undefined,
    });

    expect(onSharedMapPropsChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        board: expect.objectContaining({
          boardResponseVersion: 1,
        }),
      }),
    );
  });
});

const INCIDENT_ID = 'incident-handover-001';

function boardQueryResult() {
  return {
    data: incidentBoardResponse(1),
    isError: false,
    isFetching: false,
    isLoading: false,
    refetch: vi.fn(),
  } as any;
}

function incidentBoardResponse(boardResponseVersion: number) {
  return {
    incidentId: INCIDENT_ID,
    boardResponseVersion,
    serverTs: '2026-05-17T00:00:00Z',
    activeOpId: null,
    selectedOpIds: [],
    geometryHash: `hash-${boardResponseVersion}`,
    slots: {},
    slotSources: {},
    sourceVersions: {},
    sourceHashes: {},
  };
}

function boardSnapshot(boardResponseVersion: number) {
  return incidentBoardResponse(boardResponseVersion);
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-handover-001',
    name: 'Test Commander',
    organization: 'Test Station',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: 'Test commander account',
  };
}
