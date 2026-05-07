import { BoardSlotState, type BoardSlotLoadState } from './BoardSlotState';

export type PolicePhoneFreshness = 'normal' | 'stale' | 'lost';

export type PolicePhoneFreshnessBoardRow = {
  readonly slot: 'police_phone_freshness';
  readonly id: string;
  readonly sourceResponseId: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: 'S1-2';
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly policePhoneId: string;
  readonly freshness: PolicePhoneFreshness;
  readonly lastHeartbeatAt: string;
  readonly lastSyncAt: string;
  readonly elapsedSeconds: number;
  readonly elapsedLabel?: string;
};

export type PolicePhoneFreshnessSlotProps = {
  readonly rows: readonly PolicePhoneFreshnessBoardRow[];
  readonly nowIso?: string;
  readonly loadState?: BoardSlotLoadState;
};

export function PolicePhoneFreshnessSlot({ rows, loadState }: PolicePhoneFreshnessSlotProps) {
  if (loadState) {
    return (
      <section data-testid="slot-police-phone-freshness">
        <BoardSlotState slot="police_phone_freshness" state={loadState} />
      </section>
    );
  }

  return (
    <section data-testid="slot-police-phone-freshness">
      {rows.length === 0 ? <p role="status">단말 최신성 없음</p> : null}
      {rows.map((row, index) => (
        <article
          key={row.id}
          data-board-row-id={row.id}
          data-source-response-id={row.sourceResponseId}
          data-source-spec={row.sourceSpec}
          data-freshness={row.freshness}
        >
          <dl>
            <dt>{fieldLabel(row, index, 'id')}</dt>
            <dd>{row.id}</dd>
            <dt>{fieldLabel(row, index, 'sourceResponseId')}</dt>
            <dd>{row.sourceResponseId}</dd>
            <dt>{fieldLabel(row, index, 'status')}</dt>
            <dd>{fieldValue(row, index, 'status', row.status)}</dd>
            <dt>{fieldLabel(row, index, 'version')}</dt>
            <dd>version={row.version}</dd>
            <dt>{fieldLabel(row, index, 'sequence')}</dt>
            <dd>sequence={row.sequence}</dd>
            <dt>{fieldLabel(row, index, 'sourceSpec')}</dt>
            <dd>{fieldValue(row, index, 'sourceSpec', `sourceSpec=${row.sourceSpec}`)}</dd>
            <dt>{fieldLabel(row, index, 'latestEventId')}</dt>
            <dd>{fieldValue(row, index, 'latestEventId', row.latestEventId)}</dd>
            <dt>{fieldLabel(row, index, 'sourceHash')}</dt>
            <dd>{row.sourceHash}</dd>
            <dt>{fieldLabel(row, index, 'policePhoneId')}</dt>
            <dd>policePhoneId={row.policePhoneId}</dd>
            <dt>{fieldLabel(row, index, 'freshness')}</dt>
            <dd>{row.freshness}</dd>
            <dt>{fieldLabel(row, index, 'lastHeartbeatAt')}</dt>
            <dd>{row.lastHeartbeatAt}</dd>
            <dt>{fieldLabel(row, index, 'lastSyncAt')}</dt>
            <dd>{row.lastSyncAt}</dd>
            <dt>{fieldLabel(row, index, 'elapsedSeconds')}</dt>
            <dd>elapsedSeconds={row.elapsedSeconds}</dd>
            {row.elapsedLabel ? (
              <>
                <dt>{fieldLabel(row, index, 'elapsedLabel')}</dt>
                <dd>{row.elapsedLabel}</dd>
              </>
            ) : null}
          </dl>
        </article>
      ))}
    </section>
  );
}

function fieldLabel(row: PolicePhoneFreshnessBoardRow, index: number, label: string) {
  return index === 0 ? label : `${label}:${row.sourceResponseId}`;
}

function fieldValue(row: PolicePhoneFreshnessBoardRow, index: number, label: string, value: string) {
  return index === 0 ? value : `${label}:${row.sourceResponseId}=${value}`;
}
