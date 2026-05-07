import { formatS3S5Coordinate, type MarkerBoardRow, type PathMarkerSlotLoadState } from './pathMarkerSlotTypes';

export type MarkerSlotProps = {
  readonly rows: readonly MarkerBoardRow[];
  readonly loadState?: PathMarkerSlotLoadState;
};

export function MarkerSlot({ rows, loadState = { kind: 'idle' } }: MarkerSlotProps) {
  if (loadState.kind === 'loading') {
    return (
      <section data-testid="slot-marker">
        <p role="status">마커 불러오는 중</p>
      </section>
    );
  }

  if (loadState.kind === 'failure') {
    return (
      <section data-testid="slot-marker">
        <p role="alert">{loadState.reason}</p>
      </section>
    );
  }

  return (
    <section data-testid="slot-marker">
      {rows.length === 0 ? <p role="status">마커 없음</p> : null}
      {rows.map((row) => (
        <article key={row.id} data-source-id={row.sourceId} data-geometry-hash={row.geometryHash}>
          <dl>
            <dt>sourceId</dt>
            <dd>{row.sourceId}</dd>
            {row.type ? (
              <>
                <dt>type</dt>
                <dd>{row.type}</dd>
              </>
            ) : null}
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
            {row.policePhoneId ? (
              <>
                <dt>policePhoneId</dt>
                <dd>{row.policePhoneId}</dd>
              </>
            ) : null}
            <dt>geometryType</dt>
            <dd>{row.geometry.type}</dd>
            <dt>coordinates</dt>
            <dd>{formatS3S5Coordinate(row.geometry.coordinates)}</dd>
            {row.memo ? (
              <>
                <dt>memo</dt>
                <dd>{row.memo}</dd>
              </>
            ) : null}
          </dl>
        </article>
      ))}
    </section>
  );
}
