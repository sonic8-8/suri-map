export type S8SlotLoadState =
  | {
      readonly kind: 'unavailable' | 'failure';
      readonly reason: string;
      readonly latestEventId: string;
    };

export type OpToggleRow = {
  readonly slot: 'op_toggle';
  readonly id: string;
  readonly sourceResponseId: string;
  readonly incidentId: string;
  readonly status: 'ACTIVE' | 'ENDED';
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly opId: string;
  readonly sequenceNumber: number;
  readonly startedAt: string;
  readonly endedAt: string | null;
  readonly reason: 'INITIAL' | 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';
};

export type SearchHistorySummaryRow = {
  readonly slot: 'search_history_summary';
  readonly id: string;
  readonly sourceResponseId: string;
  readonly incidentId: string;
  readonly status: 'READY' | 'FAILED';
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly summaryId: string;
  readonly opId: string;
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
