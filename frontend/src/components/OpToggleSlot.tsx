import type { OpToggleRow } from './s8OpHandoverSlotTypes';

type OpToggleSlotProps = {
  readonly currentOpId: string;
  readonly selectedOpIds: readonly string[];
  readonly rows: readonly OpToggleRow[];
};

export function OpToggleSlot({ currentOpId, selectedOpIds, rows }: OpToggleSlotProps) {
  return (
    <section data-testid="slot-op_toggle">
      <div>currentOpId={currentOpId}</div>
      <div>selectedOpIds={selectedOpIds.join(',')}</div>
      <ul>
        {rows.map((row) => (
          <li key={row.id} data-source-response-id={row.sourceResponseId}>
            {row.opId} OP{row.sequenceNumber} {row.status}
          </li>
        ))}
      </ul>
    </section>
  );
}
