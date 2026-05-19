import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../login/presentation/types/login';
import { getHandoverIncidentDetail } from '../../data/getHandoverIncidentDetail';
import { HandoverPage } from './HandoverPage';
import { useIncidentBoardQuery, type IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
import { handoverApi, useDutyShiftListQuery, useSearchHistorySummaryListQuery } from '../../../operationalPeriod/api/handoverApi';
import { operationalPeriodApi } from '../../../operationalPeriod/api/operationalPeriodApi';
import {
  useCreateOpComparisonMutation,
  type OpComparisonResponse,
} from '../../../operationalPeriod/api/opComparisonApi';

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

vi.mock('../../../operationalPeriod/api/opComparisonApi', () => ({
  useCreateOpComparisonMutation: vi.fn(),
}));

vi.mock('../components/HandoverOperationalPeriodSelector', () => ({
  HandoverOperationalPeriodSelector: ({
    onSelectedOperationalPeriodIdsChange,
    operationalPeriods,
    selectedOperationalPeriodIds = [],
  }: {
    onSelectedOperationalPeriodIdsChange?: (periodIds: string[]) => void;
    operationalPeriods: Array<{ id: string; label: string }>;
    selectedOperationalPeriodIds?: string[];
  }) => (
    <div data-testid="handover-op-selector">
      {operationalPeriods.map((period) => (
        <button
          key={period.id}
          type="button"
          aria-label={`${period.label} 표시 전환`}
          aria-pressed={selectedOperationalPeriodIds.includes(period.id)}
          onClick={() =>
            onSelectedOperationalPeriodIdsChange?.(
              selectedOperationalPeriodIds.includes(period.id)
                ? selectedOperationalPeriodIds.filter((id) => id !== period.id)
                : [...selectedOperationalPeriodIds, period.id],
            )
          }
        >
          {period.label}
        </button>
      ))}
    </div>
  ),
}));

vi.mock('../components/HandoverComparisonMap', () => ({
  HandoverComparisonMap: ({ comparisonHighlightGeometryGeojson }: { comparisonHighlightGeometryGeojson?: string | null }) => (
    <div data-testid="handover-map" data-highlight={comparisonHighlightGeometryGeojson ?? ''} />
  ),
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
    vi.mocked(useCreateOpComparisonMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn(),
    } as any);
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

  test('standalone map area owns the OP comparison analysis panel', async () => {
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
        />
      </QueryClientProvider>,
    );

    const mapArea = screen.getByLabelText('선택 OP overlay 지도');

    await waitFor(() => expect(within(mapArea).getByLabelText('OP 비교 분석')).toBeInTheDocument());
    expect(within(mapArea).getByText('2개 OP 선택')).toBeInTheDocument();
    expect(within(mapArea).getByRole('button', { name: 'OP 비교 분석 생성' })).toBeEnabled();
  });

  test('map panel creates comparison analysis, clears stale result on selection change, and forwards selected region highlight', async () => {
    const mutateAsync = vi.fn().mockResolvedValue(comparisonResponse());
    vi.mocked(useCreateOpComparisonMutation).mockReturnValue({
      isPending: false,
      mutateAsync,
    } as any);
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
        />
      </QueryClientProvider>,
    );

    const mapArea = screen.getByLabelText('선택 OP overlay 지도');
    await waitFor(() => expect(within(mapArea).getByRole('button', { name: 'OP 비교 분석 생성' })).toBeEnabled());

    fireEvent.click(within(mapArea).getByRole('button', { name: 'OP 비교 분석 생성' }));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          request: {
            incidentId: INCIDENT_ID,
            operationalPeriodIds: ['op-current', 'op-past'],
          },
        }),
      ),
    );
    await waitFor(() => expect(within(mapArea).getByText('문장 생성 생략')).toBeInTheDocument());

    fireEvent.click(within(mapArea).getByRole('button', { name: '공통 통과 영역 선택' }));

    expect(screen.getByTestId('handover-map')).toHaveAttribute('data-highlight', HIGHLIGHT_GEOMETRY);

    fireEvent.click(screen.getByRole('button', { name: 'OP 1차 표시 전환' }));

    await waitFor(() => expect(within(mapArea).queryByText('문장 생성 생략')).not.toBeInTheDocument());
    expect(within(mapArea).getByText('2개 이상 OP를 선택하면 비교 분석을 생성할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByTestId('handover-map')).toHaveAttribute('data-highlight', '');
  });
});

const INCIDENT_ID = 'incident-handover-001';
const HIGHLIGHT_GEOMETRY = '{"type":"Polygon","coordinates":[[[126.7,35.1],[126.71,35.1],[126.71,35.11],[126.7,35.1]]]}';

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

function comparisonResponse(): OpComparisonResponse {
  return {
    comparisonId: 'comparison-001',
    incidentId: INCIDENT_ID,
    operationalPeriodIds: ['op-current', 'op-past'],
    sourceHash: 'a'.repeat(64),
    status: 'READY',
    narrativeStatus: 'SKIPPED',
    metrics: [
      {
        operationalPeriodId: 'op-current',
        sequenceNumber: 2,
        startedAt: '2026-05-17T02:00:00Z',
        endedAt: null,
        pathDistanceMeters: 1700,
        walkingDistanceMeters: 900,
        drivingDistanceMeters: 800,
        walkingRatioPercent: 53,
        averageSpeedKmh: 3.1,
        stoppedSegmentCount: 0,
        stoppedDurationSeconds: 0,
        markerCount: 3,
        handoverMemoCount: 1,
      },
      {
        operationalPeriodId: 'op-past',
        sequenceNumber: 1,
        startedAt: '2026-05-17T00:00:00Z',
        endedAt: '2026-05-17T01:00:00Z',
        pathDistanceMeters: 1240,
        walkingDistanceMeters: 840,
        drivingDistanceMeters: 400,
        walkingRatioPercent: 68,
        averageSpeedKmh: 2.4,
        stoppedSegmentCount: 1,
        stoppedDurationSeconds: 180,
        markerCount: 1,
        handoverMemoCount: 1,
      },
    ],
    diffFacts: [],
    regionFacts: [
      {
        factId: 'common-region-001',
        type: 'COMMON_REGION',
        operationalPeriodIds: ['op-current', 'op-past'],
        geometryGeojson: HIGHLIGHT_GEOMETRY,
        areaSquareMeters: 82.5,
        occupancies: [
          {
            operationalPeriodId: 'op-current',
            firstObservedAt: '2026-05-17T02:10:00Z',
            lastObservedAt: '2026-05-17T02:18:00Z',
            durationSeconds: 480,
          },
        ],
      },
    ],
    observations: null,
    requestedAt: '2026-05-17T02:00:00Z',
    generatedAt: '2026-05-17T02:00:03Z',
    version: 1,
  };
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
