import { render, screen, within } from '@testing-library/react';
import { type ComponentType } from 'react';
import { describe, expect, test } from 'vitest';

const forbiddenSourceOwnerWriteButtonName =
  /OP 생성|OP 전환|저장|수정|삭제|인수인계 저장|요약 생성|누락 확정|위험도 판단|다음 구역/;

describe('L6-T04A S8 OP comparison and handover board slots', () => {
  test('renders OP toggle and history source evidence without mutating S8 owner rows', async () => {
    const { OpHistorySlot, OpToggleSlot } = await loadS8SlotRenderers();
    const opToggleRows = deepFreeze(createOpToggleRows());
    const opHistoryRows = deepFreeze(createOpHistoryRows());
    const beforeRender = JSON.stringify({ opToggleRows, opHistoryRows });

    render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={opToggleRows}
        />
        <OpHistorySlot rows={opHistoryRows} />
      </>,
    );

    const toggleSlot = screen.getByTestId('slot-op_toggle');
    const op1Toggle = toggleSlot.querySelector('[data-source-response-id="op-precinct-001-op1"]');
    const op2Toggle = toggleSlot.querySelector('[data-source-response-id="op-precinct-001-op2"]');
    expect(op1Toggle).not.toBeNull();
    expect(op2Toggle).not.toBeNull();
    expect.soft(op1Toggle).toHaveAttribute('data-source-spec', 'S8');
    expect.soft(op1Toggle).toHaveAttribute('data-selected-op', 'true');
    expect.soft(op1Toggle).toHaveAttribute('data-current-op', 'false');
    expect.soft(op2Toggle).toHaveAttribute('data-selected-op', 'true');
    expect.soft(op2Toggle).toHaveAttribute('data-current-op', 'true');
    expect(within(toggleSlot).getByText('op-precinct-001-op1')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('op-precinct-001-op2')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('ENDED')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('ACTIVE')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('sequenceNumber=1')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('sequenceNumber=2')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('version=2')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('sourceSpec=S8')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('evt-s8-op-transition-001')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('hash-s8-op2-current')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('현재 OP')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('선택 OP')).toBeInTheDocument();
    expect(within(toggleSlot).getByText('완료 OP')).toBeInTheDocument();

    const historySlot = screen.getByTestId('slot-op_history');
    const op2History = historySlot.querySelector('[data-source-response-id="op-precinct-001-op2"]');
    expect(op2History).not.toBeNull();
    expect.soft(op2History).toHaveAttribute('data-source-spec', 'S8');
    expect(within(historySlot).getByText('board-op-history-op-precinct-001-op2')).toBeInTheDocument();
    expect(within(historySlot).getByText('OPENED')).toBeInTheDocument();
    expect(within(historySlot).getByText('OP_TRANSITIONED')).toBeInTheDocument();
    expect(within(historySlot).getByText('OP_ASSIGNMENT_CHANGED')).toBeInTheDocument();
    expect(within(historySlot).getByText('area-precinct-a1')).toBeInTheDocument();
    expect(within(historySlot).getByText('dev-precinct-car-01')).toBeInTheDocument();
    expect(within(historySlot).getByText('hash-s8-op2-current')).toBeInTheDocument();
    expect(within(historySlot).getByText('evt-s8-op-assignment-001')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenSourceOwnerWriteButtonName })).not.toBeInTheDocument();
    expect(JSON.stringify({ opToggleRows, opHistoryRows })).toBe(beforeRender);
  });

  test('renders handover memo and status source evidence while OP comparison remains usable', async () => {
    const { HandoverMemoSlot, HandoverStatusSlot, OpToggleSlot } = await loadS8SlotRenderers();
    const memoRows = deepFreeze(createHandoverMemoRows());
    const statusRow = deepFreeze(createHandoverStatusRow());
    const beforeRender = JSON.stringify({ memoRows, statusRow });

    render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <HandoverMemoSlot rows={memoRows} />
        <HandoverStatusSlot row={statusRow} />
      </>,
    );

    const memoSlot = screen.getByTestId('slot-handover_memo');
    const memoRow = memoSlot.querySelector('[data-source-response-id="memo-precinct-handover-001"]');
    expect(memoRow).not.toBeNull();
    expect.soft(memoRow).toHaveAttribute('data-source-spec', 'S8');
    expect(within(memoSlot).getByText('memo-precinct-handover-001')).toBeInTheDocument();
    expect(within(memoSlot).getByText('op-precinct-001-op1')).toBeInTheDocument();
    expect(within(memoSlot).getByText('AREA')).toBeInTheDocument();
    expect(within(memoSlot).getByText('area-precinct-a1')).toBeInTheDocument();
    expect(within(memoSlot).getByText('북측 골목 차량 수색 완료, 하천 진입로는 도보 확인 필요')).toBeInTheDocument();
    expect(within(memoSlot).getByText('createdByAccountId')).toBeInTheDocument();
    expect(within(memoSlot).getByText('acct-precinct-commander-01')).toBeInTheDocument();
    expect(within(memoSlot).getByText('APP')).toBeInTheDocument();
    expect(within(memoSlot).getByText('dev-precinct-car-01')).toBeInTheDocument();
    expect(within(memoSlot).getByText('version=1')).toBeInTheDocument();
    expect(within(memoSlot).getByText('sequence=803')).toBeInTheDocument();
    expect(within(memoSlot).getByText('hash-s8-handover-memo-current')).toBeInTheDocument();
    expect(within(memoSlot).getByRole('link', { name: 'OP1 원본 열기' })).toHaveAttribute(
      'href',
      '#op-precinct-001-op1',
    );
    expect(within(memoSlot).getByRole('link', { name: 'area-precinct-a1 원본 열기' })).toHaveAttribute(
      'href',
      '#area-precinct-a1',
    );
    expect(within(memoSlot).getByRole('link', { name: 'path-precinct-mixed-001 원본 열기' })).toHaveAttribute(
      'href',
      '#path-precinct-mixed-001',
    );

    const statusSlot = screen.getByTestId('slot-handover_status');
    expect(statusSlot).toHaveAttribute('data-source-response-id', 'handover-status-inc-precinct-first-001');
    expect(statusSlot).toHaveAttribute('data-source-spec', 'S8');
    expect(within(statusSlot).getByText('READY')).toBeInTheDocument();
    expect(within(statusSlot).getByText('readyForHandover=true')).toBeInTheDocument();
    expect(within(statusSlot).getByText('openMemoCount=1')).toBeInTheDocument();
    expect(within(statusSlot).getByText('currentOpId')).toBeInTheDocument();
    expect(within(statusSlot).getByText('op-precinct-001-op2')).toBeInTheDocument();
    expect(within(statusSlot).getByText('latestMemoAt')).toBeInTheDocument();
    expect(within(statusSlot).getByText('2026-04-28T10:35:00+09:00')).toBeInTheDocument();
    expect(within(statusSlot).getByText('evt-s8-handover-created-001')).toBeInTheDocument();
    expect(within(statusSlot).getByText('hash-s8-handover-status-current')).toBeInTheDocument();
    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenSourceOwnerWriteButtonName })).not.toBeInTheDocument();
    expect(JSON.stringify({ memoRows, statusRow })).toBe(beforeRender);
  });

  test('exposes unavailable and stale refetch states without hiding OP comparison', async () => {
    const { HandoverMemoSlot, HandoverStatusSlot, OpToggleSlot } = await loadS8SlotRenderers();
    const { rerender } = render(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <HandoverMemoSlot
          loadState={{
            kind: 'unavailable',
            reason: 's8_source_unavailable',
            latestEventId: 'evt-s8-handover-created-001',
          }}
          rows={[]}
        />
      </>,
    );

    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('s8_source_unavailable');
    expect(screen.getByRole('status')).toHaveTextContent('evt-s8-handover-created-001');

    rerender(
      <>
        <OpToggleSlot
          currentOpId="op-precinct-001-op2"
          selectedOpIds={['op-precinct-001-op1', 'op-precinct-001-op2']}
          rows={createOpToggleRows()}
        />
        <HandoverStatusSlot
          loadState={{
            kind: 'stale',
            rowId: 'board-handover-status-inc-precinct-first-001',
            reason: 'STALE_REFETCH',
            latestEventId: 'evt-s8-op-transition-001',
          }}
          row={createNeedsMemoStatusRow()}
        />
      </>,
    );

    expect(screen.getByTestId('slot-op_toggle')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('STALE_REFETCH');
    expect(screen.getByRole('status')).toHaveTextContent('board-handover-status-inc-precinct-first-001');
    expect(screen.getByRole('status')).toHaveTextContent('evt-s8-op-transition-001');
    expect(screen.getByTestId('slot-handover_status')).toHaveTextContent('NEEDS_MEMO');
    expect(screen.getByTestId('slot-handover_status')).toHaveTextContent('readyForHandover=false');
    expect(screen.queryByRole('button', { name: forbiddenSourceOwnerWriteButtonName })).not.toBeInTheDocument();
  });
});

type S8SlotLoadState =
  | {
      readonly kind: 'idle';
    }
  | {
      readonly kind: 'loading';
    }
  | {
      readonly kind: 'stale';
      readonly rowId?: string;
      readonly reason: string;
      readonly latestEventId?: string;
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

type S8BoardCursor = {
  readonly id: string;
  readonly sourceResponseId: string;
  readonly incidentId: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: 'S8';
  readonly sourceHash: string;
  readonly latestEventId: string;
};

type OpToggleRow = S8BoardCursor & {
  readonly slot: 'op_toggle';
  readonly opId: string;
  readonly sequenceNumber: number;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly reason: 'INITIAL' | 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';
};

type OpHistoryRow = S8BoardCursor & {
  readonly slot: 'op_history';
  readonly opId: string;
  readonly eventTypes: readonly ('OP_TRANSITIONED' | 'OP_ASSIGNMENT_CHANGED')[];
  readonly sequenceNumber: number;
  readonly assignmentSourceIds: readonly string[];
  readonly areaIds: readonly string[];
  readonly teamIds: readonly string[];
  readonly policePhoneIds: readonly string[];
};

type HandoverMemoRow = S8BoardCursor & {
  readonly slot: 'handover_memo';
  readonly memoId: string;
  readonly opId: string;
  readonly targetType: 'OP' | 'AREA' | 'PATH' | 'MARKER';
  readonly targetId: string;
  readonly content: string;
  readonly createdByAccountId: string;
  readonly channel: 'APP' | 'WEB';
  readonly policePhoneId: string | null;
  readonly createdAt: string;
  readonly evidenceLinks: readonly {
    readonly label: string;
    readonly href: string;
  }[];
};

type HandoverStatusRow = S8BoardCursor & {
  readonly slot: 'handover_status';
  readonly currentOpId: string;
  readonly openMemoCount: number;
  readonly latestMemoAt: string | null;
  readonly readyForHandover: boolean;
};

type S8SlotProps<Row> = {
  readonly rows: readonly Row[];
  readonly loadState?: S8SlotLoadState;
};

type S8StatusSlotProps = {
  readonly row: HandoverStatusRow | null;
  readonly loadState?: S8SlotLoadState;
};

type OpToggleSlotProps = S8SlotProps<OpToggleRow> & {
  readonly currentOpId: string;
  readonly selectedOpIds: readonly string[];
};

type S8SlotRenderers = {
  readonly OpToggleSlot: ComponentType<OpToggleSlotProps>;
  readonly OpHistorySlot: ComponentType<S8SlotProps<OpHistoryRow>>;
  readonly HandoverMemoSlot: ComponentType<S8SlotProps<HandoverMemoRow>>;
  readonly HandoverStatusSlot: ComponentType<S8StatusSlotProps>;
};

async function loadS8SlotRenderers(): Promise<S8SlotRenderers> {
  const importFutureRenderer = (path: string) => import(/* @vite-ignore */ path);
  const [opToggle, opHistory, handoverMemo, handoverStatus] = await Promise.allSettled([
    importFutureRenderer('./OpToggleSlot'),
    importFutureRenderer('./OpHistorySlot'),
    importFutureRenderer('./HandoverMemoSlot'),
    importFutureRenderer('./HandoverStatusSlot'),
  ]);

  const results = [
    ['OpToggleSlot', opToggle],
    ['OpHistorySlot', opHistory],
    ['HandoverMemoSlot', handoverMemo],
    ['HandoverStatusSlot', handoverStatus],
  ] as const;
  const missing = results.flatMap(([name, result]) => (result.status === 'rejected' ? [name] : []));

  if (missing.length > 0) {
    throw new Error(`Missing L6-T04A S8 slot renderers: ${missing.join(', ')}`);
  }

  const fulfilledOpToggle = expectFulfilled(opToggle, 'OpToggleSlot');
  const fulfilledOpHistory = expectFulfilled(opHistory, 'OpHistorySlot');
  const fulfilledHandoverMemo = expectFulfilled(handoverMemo, 'HandoverMemoSlot');
  const fulfilledHandoverStatus = expectFulfilled(handoverStatus, 'HandoverStatusSlot');

  return {
    OpToggleSlot: pickComponent(fulfilledOpToggle.value, 'OpToggleSlot'),
    OpHistorySlot: pickComponent(fulfilledOpHistory.value, 'OpHistorySlot'),
    HandoverMemoSlot: pickComponent(fulfilledHandoverMemo.value, 'HandoverMemoSlot'),
    HandoverStatusSlot: pickComponent(fulfilledHandoverStatus.value, 'HandoverStatusSlot'),
  };
}

function expectFulfilled<T>(result: PromiseSettledResult<T>, name: string): PromiseFulfilledResult<T> {
  if (result.status === 'fulfilled') {
    return result;
  }

  throw new Error(`Missing ${name} module for L6-T04A S8 slot renderer`);
}

function pickComponent<Props>(module: unknown, exportName: string): ComponentType<Props> {
  const exported = (module as Record<string, unknown>)[exportName];

  if (typeof exported !== 'function') {
    throw new Error(`Missing ${exportName} export for L6-T04A S8 slot renderer`);
  }

  return exported as ComponentType<Props>;
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

function createOpHistoryRows(): readonly OpHistoryRow[] {
  return [
    {
      slot: 'op_history',
      id: 'board-op-history-op-precinct-001-op2',
      sourceResponseId: 'op-precinct-001-op2',
      incidentId: 'inc-precinct-first-001',
      opId: 'op-precinct-001-op2',
      status: 'OPENED',
      version: 2,
      sequence: 802,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-op2-current',
      latestEventId: 'evt-s8-op-assignment-001',
      eventTypes: ['OP_TRANSITIONED', 'OP_ASSIGNMENT_CHANGED'],
      sequenceNumber: 2,
      assignmentSourceIds: ['assign-precinct-op2-a1'],
      areaIds: ['area-precinct-a1'],
      teamIds: ['team-missing-command-01'],
      policePhoneIds: ['dev-precinct-car-01'],
    },
  ] as const;
}

function createHandoverMemoRows(): readonly HandoverMemoRow[] {
  return [
    {
      slot: 'handover_memo',
      id: 'board-handover-memo-precinct-001',
      sourceResponseId: 'memo-precinct-handover-001',
      incidentId: 'inc-precinct-first-001',
      memoId: 'memo-precinct-handover-001',
      opId: 'op-precinct-001-op1',
      status: 'ACTIVE',
      version: 1,
      sequence: 803,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-handover-memo-current',
      latestEventId: 'evt-s8-handover-created-001',
      targetType: 'AREA',
      targetId: 'area-precinct-a1',
      content: '북측 골목 차량 수색 완료, 하천 진입로는 도보 확인 필요',
      createdByAccountId: 'acct-precinct-commander-01',
      channel: 'APP',
      policePhoneId: 'dev-precinct-car-01',
      createdAt: '2026-04-28T10:35:00+09:00',
      evidenceLinks: [
        { label: 'OP1 원본 열기', href: '#op-precinct-001-op1' },
        { label: 'area-precinct-a1 원본 열기', href: '#area-precinct-a1' },
        { label: 'path-precinct-mixed-001 원본 열기', href: '#path-precinct-mixed-001' },
      ],
    },
  ] as const;
}

function createHandoverStatusRow(): HandoverStatusRow {
  return {
    slot: 'handover_status',
    id: 'board-handover-status-inc-precinct-first-001',
    sourceResponseId: 'handover-status-inc-precinct-first-001',
    incidentId: 'inc-precinct-first-001',
    status: 'READY',
    version: 2,
    sequence: 804,
    sourceSpec: 'S8',
    sourceHash: 'hash-s8-handover-status-current',
    latestEventId: 'evt-s8-handover-created-001',
    currentOpId: 'op-precinct-001-op2',
    openMemoCount: 1,
    latestMemoAt: '2026-04-28T10:35:00+09:00',
    readyForHandover: true,
  };
}

function createNeedsMemoStatusRow(): HandoverStatusRow {
  return {
    ...createHandoverStatusRow(),
    status: 'NEEDS_MEMO',
    version: 3,
    sequence: 805,
    sourceHash: 'hash-s8-handover-status-needs-memo',
    latestEventId: 'evt-s8-op-transition-001',
    openMemoCount: 0,
    latestMemoAt: null,
    readyForHandover: false,
  };
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
