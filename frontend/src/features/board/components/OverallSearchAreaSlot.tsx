import {
  type OverallSearchAreaBoardRow,
  type S2SearchAreaLoadState,
  uniqueS2CoordinateLabels,
} from './s2SearchAreaSlotTypes';

export type OverallSearchAreaSlotProps = {
  readonly row: OverallSearchAreaBoardRow | null;
  readonly loadState?: S2SearchAreaLoadState;
  readonly onDraftSave?: (row: OverallSearchAreaBoardRow) => void;
};

export function OverallSearchAreaSlot({ row, loadState = { kind: 'idle' } }: OverallSearchAreaSlotProps) {
  if (loadState.kind === 'loading') {
    return <div role="status">전체 수색 구역 불러오는 중</div>;
  }

  if (loadState.kind === 'failure') {
    return <div role="alert">{loadState.reason}</div>;
  }

  if (!row) {
    return (
      <section data-testid="slot-overall_search_area">
        <p role="status">전체 수색 구역 없음</p>
      </section>
    );
  }

  return (
    <section data-testid="slot-overall_search_area" data-geometry-hash={row.geometryHash}>
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
      </dl>
      <ol aria-label="overall_search_area polygon coordinates">
        {uniqueS2CoordinateLabels(row.geometry).map((coordinateLabel, index) => (
          <li key={`${index}-${coordinateLabel}`}>{coordinateLabel}</li>
        ))}
      </ol>
    </section>
  );
}
