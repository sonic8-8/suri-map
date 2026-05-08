import { S8SlotState, SourceEvidence, type HandoverMemoRow, type S8SlotProps } from './s8OpHandoverSlotTypes';

export type HandoverMemoSlotProps = S8SlotProps<HandoverMemoRow>;

export function HandoverMemoSlot({ rows, loadState = { kind: 'idle' } }: HandoverMemoSlotProps) {
  return (
    <section data-testid="slot-handover_memo">
      <S8SlotState loadState={loadState} />
      {rows.length === 0 && loadState.kind === 'idle' ? <p role="status">인수인계 메모 없음</p> : null}
      {rows.map((row) => (
        <article
          key={row.id}
          data-board-row-id={row.id}
          data-source-response-id={row.sourceResponseId}
          data-source-spec={row.sourceSpec}
        >
          <SourceEvidence row={row}>
            <dt>memoId</dt>
            <dd>{row.memoId}</dd>
            <dt>opId</dt>
            <dd>{row.opId}</dd>
            <dt>targetType</dt>
            <dd>{row.targetType}</dd>
            <dt>targetId</dt>
            <dd>{row.targetId}</dd>
            <dt>content</dt>
            <dd>{row.content}</dd>
            <dt>createdByAccountId</dt>
            <dd>{row.createdByAccountId}</dd>
            <dt>channel</dt>
            <dd>{row.channel}</dd>
            <dt>policePhoneId</dt>
            <dd>{row.policePhoneId ?? 'none'}</dd>
            <dt>createdAt</dt>
            <dd>{row.createdAt}</dd>
          </SourceEvidence>
          {row.evidenceLinks.length > 0 ? (
            <ul aria-label={`${row.memoId} evidence links`}>
              {row.evidenceLinks.map((link) => (
                <li key={`${row.id}-${link.href}`}>
                  <a href={link.href}>{link.label}</a>
                </li>
              ))}
            </ul>
          ) : null}
        </article>
      ))}
    </section>
  );
}
