import { SourceEvidence, type SearchHistorySummarySlotProps, type S8SlotLoadState } from './s8OpHandoverSlotTypes';

const forbiddenSummaryPattern = /누락\s*확정|위험도\s*(?:높음|판단)|다음\s*구역|추천|자동\s*판단|재수색\s*제안/;
const forbiddenCtaPattern = /누락\s*확정|위험도\s*판단|다음\s*구역|추천|자동\s*판단|재수색/;

export type { SearchHistorySummarySlotProps };

export function SearchHistorySummarySlot({ row, loadState = { kind: 'idle' } }: SearchHistorySummarySlotProps) {
  const hasBlockedSummary = Boolean(row?.summaryText && forbiddenSummaryPattern.test(row.summaryText));
  const safeRetryCta = row?.retryCta && !forbiddenCtaPattern.test(row.retryCta) ? row.retryCta : null;

  return (
    <section
      data-testid="slot-search_history_summary"
      data-board-row-id={row?.id}
      data-source-response-id={row?.sourceResponseId}
      data-source-spec={row?.sourceSpec}
    >
      <SearchHistorySummaryState loadState={loadState} />
      {row ? (
        <>
          <SourceEvidence row={row}>
            <dt>displayStatus</dt>
            <dd>{`displayStatus=${row.displayStatus}`}</dd>
            {row.displayStatus === 'UNAVAILABLE' ? (
              <>
                <dt>displayStatusValue</dt>
                <dd>{row.displayStatus}</dd>
              </>
            ) : null}
            <dt>summaryId</dt>
            <dd>{row.summaryId}</dd>
            <dt>opId</dt>
            <dd>{row.opId}</dd>
            <dt>sourceSnapshotHash</dt>
            <dd>{row.sourceSnapshotHash}</dd>
            <dt>generatedAt</dt>
            <dd>{row.generatedAt ?? 'none'}</dd>
          </SourceEvidence>
          {renderSummaryText(row.summaryText, hasBlockedSummary)}
          {row.evidenceLinks.length > 0 ? (
            <ul aria-label={`${row.summaryId} evidence links`}>
              {row.evidenceLinks.map((link) => (
                <li key={`${row.id}-${link.href}`}>
                  <a href={link.href}>{link.label}</a>
                </li>
              ))}
            </ul>
          ) : null}
          {safeRetryCta ? (
            <button type="button" disabled>
              {safeRetryCta}
            </button>
          ) : null}
        </>
      ) : loadState.kind === 'idle' ? (
        <p role="status">search_history_summary unavailable</p>
      ) : null}
    </section>
  );
}

function SearchHistorySummaryState({ loadState }: { readonly loadState: S8SlotLoadState }) {
  if (loadState.kind === 'idle') {
    return null;
  }

  if (loadState.kind === 'loading') {
    return <p role="status">search_history_summary loading</p>;
  }

  const role = loadState.kind === 'failure' ? 'alert' : 'status';

  return (
    <p role={role}>
      <span>{publicSummaryReason(loadState.reason)}</span>
      {'rowId' in loadState && loadState.rowId ? <span>{loadState.rowId}</span> : null}
      {loadState.latestEventId ? <span>{loadState.latestEventId}</span> : null}
    </p>
  );
}

function renderSummaryText(summaryText: string | null, hasBlockedSummary: boolean) {
  if (hasBlockedSummary) {
    return <p role="alert">FR-23 content blocked</p>;
  }

  if (summaryText) {
    return <p>{summaryText}</p>;
  }

  return <p>요약 없음</p>;
}

function publicSummaryReason(reason: string) {
  if (reason === 'STALE_REFETCH') {
    return reason;
  }

  return 'summary_unavailable';
}
