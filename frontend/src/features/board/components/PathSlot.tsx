import { formatS3S5Coordinate, type PathBoardRow, type PathMarkerSlotLoadState } from './pathMarkerSlotTypes';

export type PathSlotProps = {
  readonly rows: readonly PathBoardRow[];
  readonly loadState?: PathMarkerSlotLoadState;
};

export function PathSlot({ rows, loadState = { kind: 'idle' } }: PathSlotProps) {
  if (loadState.kind === 'loading') {
    return (
      <section data-testid="slot-path">
        <p role="status">경로 불러오는 중</p>
      </section>
    );
  }

  if (loadState.kind === 'failure') {
    return (
      <section data-testid="slot-path">
        <p role="alert">{loadState.reason}</p>
      </section>
    );
  }

  return (
    <section data-testid="slot-path">
      {rows.length === 0 ? <p role="status">경로 없음</p> : null}
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
            <dt>accountId</dt>
            <dd>{row.accountId}</dd>
            <dt>geometryType</dt>
            <dd>{row.geometry.type}</dd>
          </dl>
          <ol aria-label={`${row.sourceId} line coordinates`}>
            {row.geometry.coordinates.map((coordinate, index) => {
              const coordinateLabel = formatS3S5Coordinate(coordinate);

              return <li key={`${row.id}-${index}-${coordinateLabel}`}>{coordinateLabel}</li>;
            })}
          </ol>
          {row.segments && row.segments.length > 0 ? (
            <ul aria-label={`${row.sourceId} segments`}>
              {row.segments.map((segment) => (
                <li key={segment.id}>
                  <span>{segment.id}</span>
                  <span>{segment.movementType}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </article>
      ))}
    </section>
  );
}
