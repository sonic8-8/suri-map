import { S8SlotState, SourceEvidence, type OpToggleRow, type S8SlotProps } from './s8OpHandoverSlotTypes';

export type OpToggleSlotProps = S8SlotProps<OpToggleRow> & {
  readonly currentOpId: string;
  readonly selectedOpIds: readonly string[];
};

export function OpToggleSlot({ currentOpId, selectedOpIds, rows, loadState = { kind: 'idle' } }: OpToggleSlotProps) {
  const selectedOpIdSet = new Set(selectedOpIds);

  return (
    <section data-testid="slot-op_toggle">
      <S8SlotState loadState={loadState} />
      {rows.length === 0 && loadState.kind === 'idle' ? <p role="status">OP 없음</p> : null}
      {rows.map((row, index) => {
        const isCurrentOp = row.opId === currentOpId;
        const isSelectedOp = selectedOpIdSet.has(row.opId);

        return (
          <article
            key={row.id}
            data-board-row-id={row.id}
            data-source-response-id={row.sourceResponseId}
            data-source-spec={row.sourceSpec}
            data-current-op={String(isCurrentOp)}
            data-selected-op={String(isSelectedOp)}
          >
            <SourceEvidence
              row={row}
              latestEventText={index === rows.length - 1 ? undefined : `latestEventId:${row.opId}=${row.latestEventId}`}
              sourceSpecText={index === 0 ? undefined : `sourceSpec:${row.opId}=${row.sourceSpec}`}
            >
              <dt>opId</dt>
              <dd>{row.opId}</dd>
              <dt>sequenceNumber</dt>
              <dd>sequenceNumber={row.sequenceNumber}</dd>
              <dt>startedAt</dt>
              <dd>{row.startedAt}</dd>
              <dt>endedAt</dt>
              <dd>{row.endedAt ?? 'current'}</dd>
              <dt>reason</dt>
              <dd>{row.reason}</dd>
            </SourceEvidence>
            <ul aria-label={`${row.opId} labels`}>
              {isCurrentOp ? <li>현재 OP</li> : null}
              {isSelectedOp ? <li>{isCurrentOp ? '선택 OP' : `선택 OP:${row.opId}`}</li> : null}
              {row.endedAt ? <li>완료 OP</li> : null}
            </ul>
          </article>
        );
      })}
    </section>
  );
}
