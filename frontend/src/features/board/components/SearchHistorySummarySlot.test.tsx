import { render, screen, within } from '@testing-library/react';
import { type ComponentType } from 'react';
import { describe, expect, test } from 'vitest';

import { OpToggleSlot } from './OpToggleSlot';

const forbiddenSearchJudgmentText = /누락 확정|위험도 높음|위험도 판단|다음 구역|추천|자동 판단|재수색 제안/;
const forbiddenSearchJudgmentButtonName = /누락 확정|위험도 판단|다음 구역|추천|자동 판단|재수색/;
const forbiddenProviderLeakText = /provider-probe-leak|prompt-probe-leak|secret-probe-leak|model-probe-leak/;

describe('L6-T04B search_history_summary board slot rendering', () => {
  test('renders generated READY summary with source evidence links and without internal generation leaks', async () => {
    const SearchHistorySummarySlot = await loadSearchHistorySummarySlot();
    const summaryRow = deepFreeze(createReadySummaryRow());
    const beforeRender = JSON.stringify(summaryRow);

    render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <SearchHistorySummarySlot row={summaryRow} />
      </>,
    );

    const summarySlot = screen.getByTestId('slot-search_history_summary');
    expect(summarySlot).toHaveAttribute('data-source-response-id', 'summary-precinct-op2-001');
    expect(summarySlot).toHaveAttribute('data-source-spec', 'S8');
    expect(within(summarySlot).getByText('READY')).toBeInTheDocument();
    expect(within(summarySlot).getByText('displayStatus=READY')).toBeInTheDocument();
    expect(within(summarySlot).getByText('summary-precinct-op2-001')).toBeInTheDocument();
    expect(within(summarySlot).getByText('op-precinct-001-op2')).toBeInTheDocument();
    expect(within(summarySlot).getByText('version=1')).toBeInTheDocument();
    expect(within(summarySlot).getByText('sequence=805')).toBeInTheDocument();
    expect(within(summarySlot).getByText('evt-s8-ai-summary-001')).toBeInTheDocument();
    expect(within(summarySlot).getByText('hash-s8-ai-summary-current')).toBeInTheDocument();
    expect(within(summarySlot).getByText('2026-04-28T10:42:00+09:00')).toBeInTheDocument();
    expect(
      within(summarySlot).getByText(
        'OP1 순찰차 경로와 도보 경로는 북측 골목과 하천 진입로 일대를 시간순으로 확인했고, NOTE 마커와 인수인계 메모는 진입로 재확인이 필요하다는 근거를 남겼습니다.',
      ),
    ).toBeInTheDocument();
    expect(within(summarySlot).getByRole('link', { name: 'OP2 원본 열기' })).toHaveAttribute(
      'href',
      '#op-precinct-001-op2',
    );
    expect(within(summarySlot).getByRole('link', { name: 'path-precinct-car-001 원본 열기' })).toHaveAttribute(
      'href',
      '#path-precinct-car-001',
    );
    expect(within(summarySlot).getByRole('link', { name: 'mk-precinct-clue-001 원본 열기' })).toHaveAttribute(
      'href',
      '#mk-precinct-clue-001',
    );
    expect(within(summarySlot).getByRole('link', { name: 'memo-precinct-handover-001 원본 열기' })).toHaveAttribute(
      'href',
      '#memo-precinct-handover-001',
    );
    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(summarySlot).not.toHaveTextContent(forbiddenProviderLeakText);
    expect(summarySlot).not.toHaveTextContent(forbiddenSearchJudgmentText);
    expect(screen.queryByRole('button', { name: forbiddenSearchJudgmentButtonName })).not.toBeInTheDocument();
    expect(JSON.stringify(summaryRow)).toBe(beforeRender);
  });

  test('shows loading and unavailable states without hiding OP comparison or exposing internal generation details', async () => {
    const SearchHistorySummarySlot = await loadSearchHistorySummarySlot();
    const { rerender } = render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <SearchHistorySummarySlot row={null} loadState={{ kind: 'loading' }} />
      </>,
    );

    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('search_history_summary loading');

    rerender(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <SearchHistorySummarySlot
          row={createUnavailableSummaryRow()}
          loadState={{
            kind: 'unavailable',
            reason: 'provider-probe-leak timeout prompt-probe-leak secret-probe-leak model-probe-leak',
            latestEventId: 'evt-s8-ai-summary-001',
          }}
        />
      </>,
    );

    const summarySlot = screen.getByTestId('slot-search_history_summary');
    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(within(summarySlot).getByRole('status')).toHaveTextContent('summary_unavailable');
    expect(within(summarySlot).getByRole('status')).toHaveTextContent('evt-s8-ai-summary-001');
    expect(within(summarySlot).getByText('UNAVAILABLE')).toBeInTheDocument();
    expect(within(summarySlot).getByRole('button', { name: '요약 다시 생성' })).toBeDisabled();
    expect(summarySlot).not.toHaveTextContent(forbiddenProviderLeakText);
    expect(summarySlot).not.toHaveTextContent(forbiddenSearchJudgmentText);
    expect(screen.queryByRole('button', { name: forbiddenSearchJudgmentButtonName })).not.toBeInTheDocument();
  });

  test('blocks FR-23 forbidden summary content and next-area CTA even if a malformed READY row reaches the renderer', async () => {
    const SearchHistorySummarySlot = await loadSearchHistorySummarySlot();

    render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <SearchHistorySummarySlot row={createForbiddenReadySummaryRow()} />
      </>,
    );

    const summarySlot = screen.getByTestId('slot-search_history_summary');
    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(within(summarySlot).getByRole('alert')).toHaveTextContent('FR-23');
    expect(summarySlot).not.toHaveTextContent('다음 구역 추천: 북쪽으로 이동');
    expect(summarySlot).not.toHaveTextContent('위험도 높음');
    expect(summarySlot).not.toHaveTextContent('누락 확정');
    expect(summarySlot).not.toHaveTextContent(forbiddenProviderLeakText);
    expect(screen.queryByRole('button', { name: forbiddenSearchJudgmentButtonName })).not.toBeInTheDocument();
  });
});

type SearchHistorySummarySlotLoadState =
  | {
      readonly kind: 'idle';
    }
  | {
      readonly kind: 'loading';
    }
  | {
      readonly kind: 'unavailable';
      readonly reason: string;
      readonly latestEventId?: string;
    }
  | {
      readonly kind: 'failure';
      readonly reason: string;
      readonly latestEventId?: string;
    };

type SearchHistorySummaryRow = {
  readonly slot: 'search_history_summary';
  readonly id: string;
  readonly sourceResponseId: string;
  readonly incidentId: string;
  readonly summaryId: string;
  readonly opId: string;
  readonly status: 'GENERATING' | 'READY' | 'FAILED';
  readonly displayStatus: 'READY' | 'UNAVAILABLE';
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: 'S8';
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly summaryText: string | null;
  readonly sourceSnapshotHash: string;
  readonly generatedAt: string | null;
  readonly retryCta: string | null;
  readonly evidenceLinks: readonly {
    readonly label: string;
    readonly href: string;
  }[];
  readonly internalProviderProbe: string;
  readonly sourcePromptProbe: string;
  readonly modelSecretProbe: string;
};

type SearchHistorySummarySlotProps = {
  readonly row: SearchHistorySummaryRow | null;
  readonly loadState?: SearchHistorySummarySlotLoadState;
};

type OpToggleRow = {
  readonly slot: 'op_toggle';
  readonly id: string;
  readonly sourceResponseId: string;
  readonly incidentId: string;
  readonly opId: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: 'S8';
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly sequenceNumber: number;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly reason: 'INITIAL' | 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';
};

async function loadSearchHistorySummarySlot(): Promise<ComponentType<SearchHistorySummarySlotProps>> {
  const importFutureRenderer = (path: string) => import(/* @vite-ignore */ path);
  const result = await importFutureRenderer('./SearchHistorySummarySlot');
  const exported = (result as Record<string, unknown>).SearchHistorySummarySlot;

  if (typeof exported !== 'function') {
    throw new Error('Missing SearchHistorySummarySlot export for L6-T04B search_history_summary renderer');
  }

  return exported as ComponentType<SearchHistorySummarySlotProps>;
}

function createReadySummaryRow(): SearchHistorySummaryRow {
  return {
    slot: 'search_history_summary',
    id: 'board-ai-summary-op-precinct-001-op2',
    sourceResponseId: 'summary-precinct-op2-001',
    incidentId: 'inc-precinct-first-001',
    summaryId: 'summary-precinct-op2-001',
    opId: 'op-precinct-001-op2',
    status: 'READY',
    displayStatus: 'READY',
    version: 1,
    sequence: 805,
    sourceSpec: 'S8',
    sourceHash: 'hash-s8-ai-summary-current',
    latestEventId: 'evt-s8-ai-summary-001',
    summaryText:
      'OP1 순찰차 경로와 도보 경로는 북측 골목과 하천 진입로 일대를 시간순으로 확인했고, NOTE 마커와 인수인계 메모는 진입로 재확인이 필요하다는 근거를 남겼습니다.',
    sourceSnapshotHash: 'hash-s8-source-snapshot-op2-001',
    generatedAt: '2026-04-28T10:42:00+09:00',
    retryCta: null,
    evidenceLinks: [
      { label: 'OP2 원본 열기', href: '#op-precinct-001-op2' },
      { label: 'path-precinct-car-001 원본 열기', href: '#path-precinct-car-001' },
      { label: 'mk-precinct-clue-001 원본 열기', href: '#mk-precinct-clue-001' },
      { label: 'memo-precinct-handover-001 원본 열기', href: '#memo-precinct-handover-001' },
    ],
    internalProviderProbe: 'provider-probe-leak',
    sourcePromptProbe: 'prompt-probe-leak',
    modelSecretProbe: 'secret-probe-leak model-probe-leak',
  };
}

function createUnavailableSummaryRow(): SearchHistorySummaryRow {
  return {
    ...createReadySummaryRow(),
    id: 'board-ai-summary-unavailable-op-precinct-001-op2',
    sourceResponseId: 'summary-precinct-op2-unavailable-001',
    summaryId: 'summary-precinct-op2-unavailable-001',
    status: 'FAILED',
    displayStatus: 'UNAVAILABLE',
    sourceHash: 'hash-s8-ai-summary-unavailable',
    summaryText: null,
    generatedAt: null,
    retryCta: '요약 다시 생성',
  };
}

function createForbiddenReadySummaryRow(): SearchHistorySummaryRow {
  return {
    ...createReadySummaryRow(),
    id: 'board-ai-summary-forbidden-op-precinct-001-op2',
    sourceResponseId: 'summary-precinct-op2-forbidden-001',
    summaryId: 'summary-precinct-op2-forbidden-001',
    sourceHash: 'hash-s8-ai-summary-forbidden',
    summaryText: '다음 구역 추천: 북쪽으로 이동. 위험도 높음. 누락 확정.',
  };
}

function createOpToggleRows(): readonly OpToggleRow[] {
  return [
    {
      slot: 'op_toggle',
      id: 'board-op-toggle-op-precinct-001-op1',
      sourceResponseId: 'op-precinct-001-op1',
      incidentId: 'inc-precinct-first-001',
      opId: 'op-precinct-001-op1',
      status: 'ENDED',
      version: 1,
      sequence: 801,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-op1-current',
      latestEventId: 'evt-s8-op-transition-001',
      sequenceNumber: 1,
      startedAt: '2026-04-28T09:00:00+09:00',
      endedAt: '2026-04-28T10:30:00+09:00',
      reason: 'INITIAL',
    },
    {
      slot: 'op_toggle',
      id: 'board-op-toggle-op-precinct-001-op2',
      sourceResponseId: 'op-precinct-001-op2',
      incidentId: 'inc-precinct-first-001',
      opId: 'op-precinct-001-op2',
      status: 'ACTIVE',
      version: 2,
      sequence: 802,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-op2-current',
      latestEventId: 'evt-s8-op-transition-001',
      sequenceNumber: 2,
      startedAt: '2026-04-28T10:30:00+09:00',
      endedAt: null,
      reason: 'AREA_CHANGED',
    },
  ] as const;
}

function deepFreeze<T>(value: T): T {
  if (typeof value !== 'object' || value === null) {
    return value;
  }

  Object.freeze(value);

  for (const child of Object.values(value as Record<string, unknown>)) {
    deepFreeze(child);
  }

  return value;
}
