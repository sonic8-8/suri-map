import { S8SlotState, SourceEvidence, type S8StatusSlotProps } from './s8OpHandoverSlotTypes';

export type HandoverStatusSlotProps = S8StatusSlotProps;

export function HandoverStatusSlot({ row, loadState = { kind: 'idle' } }: HandoverStatusSlotProps) {
  return (
    <section
      data-testid="slot-handover_status"
      data-board-row-id={row?.id}
      data-source-response-id={row?.sourceResponseId}
      data-source-spec={row?.sourceSpec}
    >
      <S8SlotState loadState={loadState} />
      {row ? (
        <SourceEvidence row={row}>
          <dt>currentOpId</dt>
          <dd>{row.currentOpId}</dd>
          <dt>openMemoCount</dt>
          <dd>openMemoCount={row.openMemoCount}</dd>
          <dt>latestMemoAt</dt>
          <dd>{row.latestMemoAt ?? 'none'}</dd>
          <dt>readyForHandover</dt>
          <dd>readyForHandover={String(row.readyForHandover)}</dd>
        </SourceEvidence>
      ) : (
        <p role="status">인수인계 상태 없음</p>
      )}
    </section>
  );
}
