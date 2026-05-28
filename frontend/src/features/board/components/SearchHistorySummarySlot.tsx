import { SourceEvidence, type S8BoardCursor } from './s8OpHandoverSlotTypes';

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

type SearchHistorySummaryRow = S8BoardCursor & {
  readonly slot: 'search_history_summary';
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

type SearchHistorySummarySlotProps = {
  readonly row: SearchHistorySummaryRow | null;
  readonly loadState?: SearchHistorySummarySlotLoadState;
};

const forbiddenSummaryFragments = [
  '누락 확정',
  '위험도',
  '위험 판단',
  '다음 구역',
  '추천',
  '자동 판단',
  '재수색 제안',
  '?꾨씫 ?뺤젙',
  '?꾪뿕???믪쓬',
  '?꾪뿕???먮떒',
  '?ㅼ쓬 援ъ뿭',
  '異붿쿇',
  '?먮룞 ?먮떒',
  '?ъ닔???쒖븞',
];

export function SearchHistorySummarySlot({ row, loadState = { kind: 'idle' } }: SearchHistorySummarySlotProps) {
  if (!row) {
    return (
      <section data-testid="slot-search_history_summary">
        {loadState.kind === 'loading' ? <p role="status">search_history_summary loading</p> : null}
        {loadState.kind !== 'idle' && loadState.kind !== 'loading' ? (
          <p role={loadState.kind === 'failure' ? 'alert' : 'status'}>{publicSummaryStatus(loadState)}</p>
        ) : null}
      </section>
    );
  }

  const isReady = row.displayStatus === 'READY' && row.summaryText;
  const blockedForbiddenSummaryText = isReady && containsForbiddenSummaryText(row.summaryText ?? '');

  return (
    <section
      data-testid="slot-search_history_summary"
      data-board-row-id={row.id}
      data-source-response-id={row.sourceResponseId}
      data-source-spec={row.sourceSpec}
    >
      <SourceEvidence row={row}>
        <dt>summaryId</dt>
        <dd>{row.summaryId}</dd>
        <dt>opId</dt>
        <dd>{row.opId}</dd>
        <dt>displayStatus</dt>
        <dd>
          {row.displayStatus === 'UNAVAILABLE' ? (
            <>
              displayStatus=<span>{row.displayStatus}</span>
            </>
          ) : (
            `displayStatus=${row.displayStatus}`
          )}
        </dd>
        <dt>sourceSnapshotHash</dt>
        <dd>{row.sourceSnapshotHash}</dd>
        <dt>generatedAt</dt>
        <dd>{row.generatedAt ?? 'none'}</dd>
      </SourceEvidence>

      {blockedForbiddenSummaryText ? <p role="alert">FR-23 forbidden summary text blocked</p> : null}
      {isReady && !blockedForbiddenSummaryText ? <p>{row.summaryText}</p> : null}
      {!isReady ? (
        <p role="status">
          summary_unavailable
          {'latestEventId' in loadState && loadState.latestEventId ? <span> {loadState.latestEventId}</span> : null}
        </p>
      ) : null}

      {row.displayStatus === 'UNAVAILABLE' && row.retryCta ? (
        <button type="button" disabled>
          {row.retryCta}
        </button>
      ) : null}

      {row.evidenceLinks.length > 0 ? (
        <nav aria-label="summary evidence">
          {row.evidenceLinks.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
        </nav>
      ) : null}
    </section>
  );
}

function publicSummaryStatus(loadState: Exclude<SearchHistorySummarySlotLoadState, { kind: 'idle' | 'loading' }>) {
  return loadState.latestEventId ? `summary_unavailable ${loadState.latestEventId}` : 'summary_unavailable';
}

function containsForbiddenSummaryText(value: string) {
  return forbiddenSummaryFragments.some((fragment) => value.includes(fragment));
}
