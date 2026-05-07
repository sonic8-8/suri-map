import { type BoardSlotName } from '../contracts/boardSlotRegistryContract';

export type BoardSlotLoadState =
  | {
      readonly kind: 'loading';
      readonly label?: string;
    }
  | {
      readonly kind: 'stale';
      readonly rowId?: string;
      readonly reason: string;
      readonly latestEventId?: string;
    }
  | {
      readonly kind: 'failure';
      readonly reason: string;
      readonly latestEventId?: string;
    };

export type BoardSlotStateProps = {
  readonly slot: BoardSlotName;
  readonly state: BoardSlotLoadState;
};

export function BoardSlotState({ slot, state }: BoardSlotStateProps) {
  if (state.kind === 'loading') {
    return (
      <div data-testid={`slot-state-${slot}`} role="status">
        {state.label ?? 'board slot loading'}
      </div>
    );
  }

  if (state.kind === 'stale') {
    return (
      <div data-testid={`slot-state-${slot}`} role="status">
        <span>{state.reason}</span>
        {state.rowId ? <span>{state.rowId}</span> : null}
        {state.latestEventId ? <span>{state.latestEventId}</span> : null}
      </div>
    );
  }

  return (
    <div data-testid={`slot-state-${slot}`} role="alert">
      <span>{state.reason}</span>
      {state.latestEventId ? <span>{state.latestEventId}</span> : null}
    </div>
  );
}
