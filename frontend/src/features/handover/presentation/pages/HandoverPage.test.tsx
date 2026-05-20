import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import type { LoginAccount } from '../../../login/presentation/types/login';
import { getHandoverIncidentDetail } from '../../data/getHandoverIncidentDetail';
import { HandoverPage } from './HandoverPage';
import { SearchHistoryPage } from '../../../searchHistory/presentation/pages/SearchHistoryPage';
import { useIncidentBoardQuery, type IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
import {
  handoverApi,
  useDutyShiftListQuery,
  useSearchHistorySummaryListQuery,
} from '../../../operationalPeriod/api/handoverApi';
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
          aria-label={`${period.label} toggle`}
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
  HandoverComparisonMap: ({
    comparisonHighlightGeometryGeojson,
    highlightedSourceRecordKey = null,
    isMapExpanded = false,
    onToggleMapExpanded = () => {},
    rightPanelWidthPx = null,
    selectedOpIds = [],
  }: {
    comparisonHighlightGeometryGeojson?: string | null;
    highlightedSourceRecordKey?: string | null;
    isMapExpanded?: boolean;
    onToggleMapExpanded?: () => void;
    rightPanelWidthPx?: number | null;
    selectedOpIds?: string[];
  }) => (
    <div
      data-testid="handover-map"
      data-highlight={comparisonHighlightGeometryGeojson ?? ''}
      data-source-highlight={highlightedSourceRecordKey ?? ''}
      data-selected-op-ids={selectedOpIds.join('|')}
      data-is-map-expanded={String(isMapExpanded)}
      data-right-panel-width={rightPanelWidthPx ?? ''}
    >
      <button
        type="button"
        aria-label={isMapExpanded ? 'map fullscreen collapse' : 'map fullscreen expand'}
        onClick={onToggleMapExpanded}
      >
        {isMapExpanded ? 'collapse' : 'expand'}
      </button>
    </div>
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

  test('handover page does not expose search history summary tabs', async () => {
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 2, endedAt: null })],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <HandoverPage
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

    expect(await screen.findByRole('heading', { name: 'OP 2차 인수인계' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'OP 요약' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'OP 비교' })).not.toBeInTheDocument();
  });

  test('standalone handover starts with only the current OP selected', async () => {
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
    const currentOpButton = await screen.findByRole('button', { name: /^OP 2/ });
    expect(currentOpButton).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() =>
      expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', 'op-current'),
    );
    expect(screen.getByRole('button', { name: /^OP 2/ })).toHaveAttribute('aria-pressed', 'true');
  });

  test('OP briefing renders only OP scoped summary and keeps rail counts focused on the opened OP', async () => {
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResult(
        incidentBoardResponse(1, {
          slots: {
            path: [
              boardSlotRow({ id: 'path-current', opId: 'op-current' }),
              boardSlotRow({ id: 'path-past', opId: 'op-past' }),
            ],
            marker: [
              boardSlotRow({ id: 'marker-current', opId: 'op-current' }),
              boardSlotRow({ id: 'marker-past', opId: 'op-past' }),
            ],
            area: [
              boardSlotRow({ id: 'area-current', opId: 'op-current' }),
              boardSlotRow({ id: 'area-past', opId: 'op-past' }),
            ],
          },
        }),
      ),
    );
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [
        operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 2, endedAt: null }),
        operationalPeriod({ id: 'op-past', status: 'ENDED', sequenceNumber: 1, endedAt: '2026-05-17T01:00:00Z' }),
      ],
    });
    vi.mocked(useSearchHistorySummaryListQuery).mockReturnValue({
      data: {
        items: [
          {
            summaryId: 'summary-op-current',
            opId: 'op-current',
            scopeType: 'OP',
            scopeId: 'op-current',
            status: 'READY',
            displayStatus: 'READY',
            content: 'OP 2차 결과 브리핑입니다.',
            sourceReadiness: 'READY',
            sourceHash: 'a'.repeat(64),
            generatedAt: '2026-05-17T02:30:00Z',
            version: 1,
          },
          {
            summaryId: 'summary-duty-current',
            opId: 'op-current',
            scopeType: 'DUTY_SHIFT',
            scopeId: 'duty-current',
            dutyShiftId: 'duty-current',
            status: 'READY',
            displayStatus: 'READY',
            content: '근무 인수인계 요약입니다.',
            sourceReadiness: 'READY',
            sourceHash: 'b'.repeat(64),
            generatedAt: '2026-05-17T02:20:00Z',
            version: 1,
          },
        ],
      },
      isError: false,
      isFetching: false,
      isLoading: false,
    } as any);

    render(
      <QueryClientProvider client={new QueryClient()}>
        <SearchHistoryPage
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

    await waitFor(() =>
      expect(vi.mocked(useSearchHistorySummaryListQuery)).toHaveBeenCalledWith(
        'op-current',
        expect.objectContaining({
          incidentId: INCIDENT_ID,
          scopeType: 'OP',
          scopeId: 'op-current',
        }),
        undefined,
        expect.objectContaining({
          refetchInterval: expect.any(Function),
        }),
      ),
    );

    expect(await screen.findByText('OP 2차 결과 브리핑입니다.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'OP 요약' })).toBeInTheDocument();
    expect(screen.queryByText('근무 인수인계 요약입니다.')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^OP 1차/ }));
    await waitFor(() =>
      expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', 'op-current|op-past'),
    );
    expect(screen.getByText('경로 1건')).toBeInTheDocument();
    expect(screen.getByText('마커 1건')).toBeInTheDocument();
    expect(screen.getByText('구역 1건')).toBeInTheDocument();
  });

  test('search history page shows selected OP markers below handover memos', async () => {
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResult(
        incidentBoardResponse(1, {
          slots: {
            marker: [
              markerSlotRow({ id: 'marker-current', opId: 'op-current', title: '남문 단서' }),
              markerSlotRow({ id: 'marker-past', opId: 'op-past', title: '북문 메모' }),
            ],
          },
        }),
      ),
    );
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [
        operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 2, endedAt: null }),
        operationalPeriod({ id: 'op-past', status: 'ENDED', sequenceNumber: 1, endedAt: '2026-05-17T01:00:00Z' }),
      ],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <SearchHistoryPage
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

    const markerSection = await screen.findByRole('region', { name: '선택 OP 등록 마커' });

    expect(within(markerSection).getByRole('heading', { name: '등록 마커' })).toBeInTheDocument();
    expect(within(markerSection).getByText('남문 단서')).toBeInTheDocument();
    expect(within(markerSection).queryByText('북문 메모')).not.toBeInTheDocument();
  });

  test('source record list highlights map evidence while summary omits evidence badges', async () => {
    vi.mocked(useIncidentBoardQuery).mockReturnValue(
      boardQueryResult(
        incidentBoardResponse(1, {
          slots: {
            path: [boardSlotRow({ id: 'path-current', opId: 'op-current' })],
            marker: [boardSlotRow({ id: 'marker-current', opId: 'op-current' })],
          },
        }),
      ),
    );
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 2, endedAt: null })],
    });
    vi.mocked(useSearchHistorySummaryListQuery).mockReturnValue({
      data: {
        items: [
          {
            summaryId: 'summary-op-current',
            opId: 'op-current',
            scopeType: 'OP',
            scopeId: 'op-current',
            status: 'READY',
            displayStatus: 'READY',
            content: 'OP 2차는 남측 순찰 경로와 현장 마커를 기준으로 정리되었습니다.',
            sourceReadiness: 'READY',
            sourceHash: 'a'.repeat(64),
            generatedAt: '2026-05-17T02:30:00Z',
            version: 1,
          },
        ],
      },
      isError: false,
      isFetching: false,
      isLoading: false,
    } as any);

    render(
      <QueryClientProvider client={new QueryClient()}>
        <SearchHistoryPage
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

    await screen.findByText('OP 2차는 남측 순찰 경로와 현장 마커를 기준으로 정리되었습니다.');

    expect(screen.queryByRole('button', { name: '수색 경로 원본 강조' })).not.toBeInTheDocument();

    const pathEvidenceButton = await screen.findByRole('button', { name: '수색 경로 원본 기록 열기' });
    fireEvent.click(pathEvidenceButton);

    expect(pathEvidenceButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('handover-map')).toHaveAttribute('data-source-highlight', 'path:path-current');
  });

  test('op visibility toggle can be cleared and refuses a third visible OP', async () => {
    vi.mocked(operationalPeriodApi.list).mockResolvedValue({
      currentOpId: 'op-current',
      items: [
        operationalPeriod({ id: 'op-current', status: 'ACTIVE', sequenceNumber: 4, endedAt: null }),
        operationalPeriod({ id: 'op-second', status: 'ENDED', sequenceNumber: 3, endedAt: '2026-05-17T03:00:00Z' }),
        operationalPeriod({ id: 'op-third', status: 'ENDED', sequenceNumber: 2, endedAt: '2026-05-17T02:00:00Z' }),
        operationalPeriod({ id: 'op-fourth', status: 'ENDED', sequenceNumber: 1, endedAt: '2026-05-17T01:00:00Z' }),
      ],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <HandoverPage
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

    const currentOpButton = await screen.findByRole('button', { name: /^OP 4차/ });
    const secondOpButton = screen.getByRole('button', { name: /^OP 3차/ });
    const thirdOpButton = screen.getByRole('button', { name: /^OP 2차/ });
    const fourthOpButton = screen.getByRole('button', { name: /^OP 1차/ });

    expect(currentOpButton).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(currentOpButton);
    await waitFor(() => expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', ''));
    expect(currentOpButton).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(secondOpButton);
    fireEvent.click(thirdOpButton);
    await waitFor(() =>
      expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', 'op-second|op-third'),
    );

    fireEvent.click(fourthOpButton);

    await waitFor(() => expect(screen.getByText('OP는 최대 2개까지 동시에 표시할 수 있습니다.')).toBeInTheDocument());
    expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', 'op-second|op-third');
    expect(fourthOpButton).toHaveAttribute('aria-pressed', 'false');
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
        operationalPeriod({
          id: 'op-board-active',
          status: 'ENDED',
          sequenceNumber: 2,
          endedAt: '2026-05-17T02:00:00Z',
        }),
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
        <SearchHistoryPage
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
    await screen.findByRole('region', { name: /수색 이력 지도/ });
    fireEvent.click(await screen.findByRole('tab', { name: 'OP 비교' }));
    const comparisonPanel = screen.getByRole('tabpanel', { name: 'OP 비교' });
    expect(within(comparisonPanel).getByRole('heading', { name: 'OP 비교' })).toBeInTheDocument();
    expect(within(comparisonPanel).getByText(/OP 선택/)).toBeInTheDocument();
    expect(within(comparisonPanel).getByRole('button', { name: /비교 생성/ })).toBeDisabled();
  });
  test('fullscreen toggle collapses the handover shell panels', async () => {
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

    const currentOpButton = await screen.findByRole('button', { name: /^OP 2/ });
    expect(currentOpButton).toBeVisible();
    expect(screen.getByRole('button', { name: 'Suri-Map' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^OP 2/ })).toBeVisible();
    expect(screen.getByRole('region', { name: '범례' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'map fullscreen expand' }));

    await waitFor(() => expect(screen.getByTestId('handover-map')).toHaveAttribute('data-is-map-expanded', 'true'));
    await waitFor(() => expect(screen.getByTestId('handover-map')).toHaveAttribute('data-right-panel-width', ''));
    expect(screen.queryByRole('button', { name: 'Suri-Map' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'map fullscreen collapse' })).toBeInTheDocument();
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
        <SearchHistoryPage
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

    await screen.findByRole('region', { name: /수색 이력 지도/ });
    const firstOpButton = await screen.findByRole('button', { name: /^OP 1/ });

    fireEvent.click(firstOpButton);
    await waitFor(() => expect(firstOpButton).toHaveAttribute('aria-pressed', 'true'));
    await waitFor(() =>
      expect(screen.getByTestId('handover-map')).toHaveAttribute('data-selected-op-ids', 'op-current|op-past'),
    );
    expect(screen.queryByRole('button', { name: 'OP 비교 열기' })).not.toBeInTheDocument();
    const createComparisonButton = await screen.findByRole('button', { name: /비교 생성/ });
    const comparisonPanel = screen.getByRole('tabpanel', { name: 'OP 비교' });
    await waitFor(() => expect(createComparisonButton).toBeEnabled());

    fireEvent.click(createComparisonButton);

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

    fireEvent.click(within(comparisonPanel).getByRole('button', { name: /공통/ }));
    fireEvent.click(firstOpButton);
    await waitFor(() => expect(firstOpButton).toHaveAttribute('aria-pressed', 'false'));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'OP 비교 열기' })).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'OP 요약' })).toBeInTheDocument();
    expect(screen.getByTestId('handover-map')).toHaveAttribute('data-highlight', '');
  });
});

const INCIDENT_ID = 'incident-handover-001';
const HIGHLIGHT_GEOMETRY =
  '{"type":"Polygon","coordinates":[[[126.7,35.1],[126.71,35.1],[126.71,35.11],[126.7,35.1]]]}';

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

function boardSlotRow({ id, opId }: { id: string; opId: string }) {
  return {
    id,
    opId,
    status: 'READY',
    version: 1,
    sequence: 1,
    sourceSpec: 'S8',
    sourceHash: `${id}-hash`,
    latestEventId: `${id}-event`,
  };
}

function markerSlotRow({ id, opId, title }: { id: string; opId: string; title: string }) {
  return {
    ...boardSlotRow({ id, opId }),
    markerType: 'CLUE',
    title,
    memo: `${title} 메모`,
    occurredAt: '2026-05-17T02:10:00+09:00',
    source: 'APP',
    geometry: {
      type: 'Point',
      coordinates: [126.9416, 37.5274],
    },
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
