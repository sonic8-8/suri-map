import { S8SlotState, SourceEvidence, type OpHistoryRow, type S8SlotProps } from './s8OpHandoverSlotTypes';

export type OpHistorySlotProps = S8SlotProps<OpHistoryRow>;

export function OpHistorySlot({ rows, loadState = { kind: 'idle' } }: OpHistorySlotProps) {
  return (
    <section data-testid="slot-op_history">
      <S8SlotState loadState={loadState} />
      {rows.length === 0 && loadState.kind === 'idle' ? <p role="status">OP 이력 없음</p> : null}
      {rows.map((row) => (
        <article
          key={row.id}
          data-board-row-id={row.id}
          data-source-response-id={row.sourceResponseId}
          data-source-spec={row.sourceSpec}
        >
          <SourceEvidence row={row}>
            <dt>opId</dt>
            <dd>{row.opId}</dd>
            <dt>sequenceNumber</dt>
            <dd>sequenceNumber={row.sequenceNumber}</dd>
          </SourceEvidence>
          <ul aria-label={`${row.opId} event types`}>
            {row.eventTypes.map((eventType) => (
              <li key={`${row.id}-${eventType}`}>{eventType}</li>
            ))}
          </ul>
          <ul aria-label={`${row.opId} assignments`}>
            {row.assignmentSourceIds.map((assignmentSourceId) => (
              <li key={`${row.id}-${assignmentSourceId}`}>{assignmentSourceId}</li>
            ))}
          </ul>
          <ul aria-label={`${row.opId} assignment areas`}>
            {row.areaIds.map((areaId) => (
              <li key={`${row.id}-${areaId}`}>{areaId}</li>
            ))}
          </ul>
          <ul aria-label={`${row.opId} teams`}>
            {row.teamIds.map((teamId) => (
              <li key={`${row.id}-${teamId}`}>{teamId}</li>
            ))}
          </ul>
          <ul aria-label={`${row.opId} police phones`}>
            {row.policePhoneIds.map((policePhoneId) => (
              <li key={`${row.id}-${policePhoneId}`}>{policePhoneId}</li>
            ))}
          </ul>
        </article>
      ))}
    </section>
  );
}
