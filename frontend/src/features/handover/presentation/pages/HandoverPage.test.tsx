import { render, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../login/presentation/types/login';
import { getHandoverIncidentDetail } from '../../data/getHandoverIncidentDetail';
import { HandoverPage } from './HandoverPage';
import { useIncidentBoardQuery, type IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
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

  test('shared map mode lets the handover overlay own the current OP layer', async () => {
    const onSharedMapPropsChange = vi.fn();
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [
        operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 2, endedAt: null }),
        operationalPeriod({ id: 'op-past', status: 'ENDED', sequenceNumber: 1, endedAt: '2026-05-17T01:00:00Z' }),
      ],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <HandoverPage
          embedded
          sharedMapMode
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

    await waitFor(() =>
      expect(onSharedMapPropsChange).toHaveBeenCalledWith(
        expect.objectContaining({
          baseMapMode: 'shared-base-map',
          selectedOpIds: ['op-current'],
        }),
      ),
    );
  });

  test('shared map mode does not exclude board active OPs from overlay props', async () => {
    const onSharedMapPropsChange = vi.fn();
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResult(
        incidentBoardResponse(1, {
          activeOpId: 'op-board-active',
        }),
      ),
    );
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-api-current',
      items: [
        operationalPeriod({ id: 'op-api-current', status: 'ACTIVE', sequenceNumber: 3, endedAt: null }),
        operationalPeriod({ id: 'op-board-active', status: 'ENDED', sequenceNumber: 2, endedAt: '2026-05-17T02:00:00Z' }),
        operationalPeriod({ id: 'op-past', status: 'ENDED', sequenceNumber: 1, endedAt: '2026-05-17T01:00:00Z' }),
      ],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <HandoverPage
          embedded
          sharedMapMode
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

    await waitFor(() =>
      expect(onSharedMapPropsChange).toHaveBeenCalledWith(
        expect.objectContaining({
          selectedOpIds: ['op-api-current'],
        }),
      ),
    );
  });
});

const INCIDENT_ID = 'incident-handover-001';

function boardQueryResult(data = incidentBoardResponse(1)) {
  return {
    data,
    isError: false,
    isFetching: false,
    isLoading: false,
    refetch: vi.fn(),
  } as any;
}

function incidentBoardResponse(
  boardResponseVersion: number,
  overrides: Partial<IncidentBoardResponse> = {},
): IncidentBoardResponse {
  return {
    ...incidentBoardResponseBase(boardResponseVersion),
    ...overrides,
  };
}

function incidentBoardResponseBase(boardResponseVersion: number): IncidentBoardResponse {
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
  return incidentBoardResponse(boardResponseVersion) as any;
}

function operationalPeriod(overrides: {
  id: string;
  status: 'ACTIVE' | 'ENDED';
  sequenceNumber: number;
  endedAt: string | null;
}) {
  return {
    id: overrides.id,
    status: overrides.status,
    reason: overrides.sequenceNumber === 1 ? 'INITIAL' : 'RE_SEARCH',
    sequenceNumber: overrides.sequenceNumber,
    openedAt: '2026-05-17T00:00:00Z',
    endedAt: overrides.endedAt,
    version: 1,
  } as const;
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-handover-001',
    name: 'Test Commander',
    organization: 'Test Station',
    rank: '경위',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: 'Test commander account',
  };
}
