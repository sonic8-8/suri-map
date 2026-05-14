import type { S8SlotLoadState, SearchHistorySummaryRow } from './s8OpHandoverSlotTypes';

type SearchHistorySummarySlotProps = {
  readonly row: SearchHistorySummaryRow;
  readonly loadState?: S8SlotLoadState;
};

export function SearchHistorySummarySlot({ row, loadState }: SearchHistorySummarySlotProps) {
  const isReady = row.displayStatus === 'READY' && row.summaryText;

  return (
    <section data-testid="slot-search_history_summary" data-source-response-id={row.sourceResponseId}>
      <div>displayStatus={row.displayStatus}</div>
      <div>{row.opId}</div>
      {isReady ? <p>{row.summaryText}</p> : <p>summary_unavailable</p>}
      {loadState ? <div>latestEventId={loadState.latestEventId}</div> : null}
      <nav aria-label="summary evidence">
        {row.evidenceLinks.map((link) => (
          <a key={link.href} href={link.href}>
            {link.label}
          </a>
        ))}
      </nav>
    </section>
  );
}
