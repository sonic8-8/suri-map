import { type S2SearchAreaLoadState, type SearchAreaBoardRow, uniqueS2CoordinateLabels } from './s2SearchAreaSlotTypes';

export type SearchAreaSlotProps = {
  readonly rows: readonly SearchAreaBoardRow[];
  readonly loadState?: S2SearchAreaLoadState;
  readonly onStateChange?: (row: SearchAreaBoardRow) => void;
  readonly onSplit?: (row: SearchAreaBoardRow) => void;
};

export function SearchAreaSlot({ rows, loadState = { kind: 'idle' } }: SearchAreaSlotProps) {
  if (loadState.kind === 'loading') {
    return <div role="status">수색 구역 불러오는 중</div>;
  }

  if (loadState.kind === 'failure') {
    return <div role="alert">{loadState.reason}</div>;
  }

  return (
    <section data-testid="slot-area">
      {rows.length === 0 ? <p role="status">수색 구역 없음</p> : null}
      {rows.map((row) => (
        <article key={row.id} data-source-id={row.sourceId} data-geometry-hash={row.geometryHash}>
          <dl>
            <dt>sourceId</dt>
            <dd>{row.sourceId}</dd>
            <dt>status</dt>
            <dd>{row.status}</dd>
            <dt>version</dt>
            <dd>version={row.version}</dd>
            <dt>sequence</dt>
            <dd>sequence={row.sequence}</dd>
            <dt>sourceSpec</dt>
            <dd>sourceSpec={row.sourceSpec}</dd>
            <dt>latestEventId</dt>
            <dd>{row.latestEventId}</dd>
            <dt>sourceHash</dt>
            <dd>{row.sourceHash}</dd>
            <dt>geometryHash</dt>
            <dd>{row.geometryHash}</dd>
            <dt>opId</dt>
            <dd>{row.opId}</dd>
          </dl>
          <ol aria-label={`${row.sourceId} polygon coordinates`}>
            {uniqueS2CoordinateLabels(row.geometry).map((coordinateLabel, index) => (
              <li key={`${row.id}-${index}-${coordinateLabel}`}>{coordinateLabel}</li>
            ))}
          </ol>
        </article>
      ))}
    </section>
  );
}
