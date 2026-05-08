import { type ReactNode } from 'react';

export type S8SlotLoadState =
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

export type S8BoardCursor = {
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

export type OpToggleRow = S8BoardCursor & {
  readonly slot: 'op_toggle';
  readonly opId: string;
  readonly sequenceNumber: number;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly reason: 'INITIAL' | 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';
};

export type OpHistoryRow = S8BoardCursor & {
  readonly slot: 'op_history';
  readonly opId: string;
  readonly eventTypes: readonly ('OP_TRANSITIONED' | 'OP_ASSIGNMENT_CHANGED')[];
  readonly sequenceNumber: number;
  readonly assignmentSourceIds: readonly string[];
  readonly areaIds: readonly string[];
  readonly teamIds: readonly string[];
  readonly policePhoneIds: readonly string[];
};

export type HandoverMemoRow = S8BoardCursor & {
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

export type HandoverStatusRow = S8BoardCursor & {
  readonly slot: 'handover_status';
  readonly currentOpId: string;
  readonly openMemoCount: number;
  readonly latestMemoAt: string | null;
  readonly readyForHandover: boolean;
};

export type SearchHistorySummaryRow = S8BoardCursor & {
  readonly slot: 'search_history_summary';
  readonly summaryId: string;
  readonly opId: string;
  readonly status: 'GENERATING' | 'READY' | 'FAILED';
  readonly displayStatus: 'READY' | 'UNAVAILABLE';
  readonly summaryText: string | null;
  readonly sourceSnapshotHash: string;
  readonly generatedAt: string | null;
  readonly retryCta: string | null;
  readonly evidenceLinks: readonly {
    readonly label: string;
    readonly href: string;
  }[];
};

export type S8SlotProps<Row> = {
  readonly rows: readonly Row[];
  readonly loadState?: S8SlotLoadState;
};

export type S8StatusSlotProps = {
  readonly row: HandoverStatusRow | null;
  readonly loadState?: S8SlotLoadState;
};

export type SearchHistorySummarySlotProps = {
  readonly row: SearchHistorySummaryRow | null;
  readonly loadState?: S8SlotLoadState;
};

export function S8SlotState({ loadState }: { readonly loadState: S8SlotLoadState }) {
  if (loadState.kind === 'idle') {
    return null;
  }

  if (loadState.kind === 'loading') {
    return <p role="status">S8 source loading</p>;
  }

  const role = loadState.kind === 'failure' ? 'alert' : 'status';

  return (
    <p role={role}>
      <span>{loadState.reason}</span>
      {'rowId' in loadState && loadState.rowId ? <span>{loadState.rowId}</span> : null}
      {loadState.latestEventId ? <span>{loadState.latestEventId}</span> : null}
    </p>
  );
}

export function SourceEvidence({
  row,
  children,
  latestEventText = row.latestEventId,
  sourceSpecText = `sourceSpec=${row.sourceSpec}`,
}: {
  readonly row: S8BoardCursor;
  readonly children?: ReactNode;
  readonly latestEventText?: string;
  readonly sourceSpecText?: string;
}) {
  return (
    <dl>
      <dt>id</dt>
      <dd>{row.id}</dd>
      <dt>sourceResponseId</dt>
      <dd>sourceResponseId={row.sourceResponseId}</dd>
      <dt>status</dt>
      <dd>{row.status}</dd>
      <dt>version</dt>
      <dd>version={row.version}</dd>
      <dt>sequence</dt>
      <dd>sequence={row.sequence}</dd>
      <dt>sourceSpec</dt>
      <dd>{sourceSpecText}</dd>
      <dt>latestEventId</dt>
      <dd>{latestEventText}</dd>
      <dt>sourceHash</dt>
      <dd>{row.sourceHash}</dd>
      {children}
    </dl>
  );
}
